package ma.aquaops.simulator;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import ma.aquaops.simulator.scenario.Sample;
import ma.aquaops.simulator.scenario.ScenarioRegistry;

/**
 * Mode charge (NFR-01) : debit soutenu, avec comptage des mesures acceptees par la passerelle.
 *
 * Nombre de requetes en cours BORNE (simulator.load.max-in-flight). Avant (RC-04/RC-05, 23/11/2026) :
 * le generateur envoyait chaque tranche de 10 ms sans attendre les reponses ; quand la chaine
 * ralentissait, les requetes en attente s'accumulaient sans limite jusqu'a l'arret du pod faute de
 * memoire (OOMKilled a 384 Mi puis 640 Mi, meme a 200/s). Desormais, la limite atteinte, il attend :
 * s'il ne tient pas le debit demande, attempted reste inferieur a rate x seconds (notSent > 0),
 * ce qui se voit dans le statut au lieu de faire tomber le simulateur.
 *
 * attempted, acked et failed comptent des MESURES (une mesure renvoyee apres une erreur passagere
 * reste une seule mesure, avec le meme identifiant ; retries compte les renvois).
 */
@Component
public class LoadGenerator {

    public record Status(boolean running, int rate, int seconds,
                         long attempted, long acked, long failed,
                         String startedAt, String endedAt,
                         long inFlight, int maxInFlight, long retries, long notSent) {}

    private final SimulatorPublisher publisher;
    private final ScenarioRegistry scenarios;
    private final int maxInFlight;
    private final Semaphore permits;
    private long retriesAtStart;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "load-generator");
        t.setDaemon(true);
        return t;
    });

    private final AtomicLong attempted = new AtomicLong();
    private final AtomicLong acked = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong inFlight = new AtomicLong();

    private ScheduledFuture<?> task;
    private volatile boolean running;
    private volatile Instant startedAt;
    private volatile Instant endedAt;
    private int rate;
    private int seconds;
    private long durationNanos;
    private long startNanos;
    private long index;
    private boolean completionRequested;

    public LoadGenerator(SimulatorPublisher publisher,
                         ScenarioRegistry scenarios,
                         @Value("${simulator.load.max-in-flight:256}") int maxInFlight) {
        if (maxInFlight < 1) {
            throw new IllegalArgumentException("simulator.load.max-in-flight doit valoir au moins 1");
        }
        this.publisher = publisher;
        this.scenarios = scenarios;
        this.maxInFlight = maxInFlight;
        this.permits = new Semaphore(maxInFlight);
    }

    /**
     * @throws IllegalStateException si un test est deja en cours ou si un scenario de panne est actif
     * @throws IllegalArgumentException si le debit ou la duree est invalide
     */
    public synchronized void start(int rate, int seconds) {
        if (running) {
            throw new IllegalStateException("Test de charge deja en cours");
        }
        if (!"normal".equals(scenarios.actif())) {
            throw new IllegalStateException("Un scenario de panne est actif : appeler /scenarios/stop d'abord");
        }
        if (rate < 1 || seconds < 1) {
            throw new IllegalArgumentException("rate et seconds doivent etre superieurs a 0");
        }
        this.rate = rate;
        this.seconds = seconds;
        this.durationNanos = seconds * 1_000_000_000L;
        this.index = 0;
        attempted.set(0);
        acked.set(0);
        failed.set(0);
        inFlight.set(0);
        retriesAtStart = publisher.retries();
        completionRequested = false;
        endedAt = null;
        startedAt = Instant.now();
        startNanos = System.nanoTime();
        publisher.setPause(true);
        running = true;
        task = executor.scheduleAtFixedRate(this::tick, 0, 10, TimeUnit.MILLISECONDS);
    }

    public synchronized void stop() {
        if (running) {
            requestCompletion();
        }
    }

    public Status status() {
        long expected = startedAt == null ? 0
                : running ? rate * Math.min(System.nanoTime() - startNanos, durationNanos) / 1_000_000_000L
                : (long) rate * seconds;
        return new Status(running, rate, seconds, attempted.get(), acked.get(), failed.get(),
                startedAt == null ? null : startedAt.toString(),
                endedAt == null ? null : endedAt.toString(),
                inFlight.get(), maxInFlight, publisher.retries() - retriesAtStart,
                Math.max(0, expected - attempted.get()));
    }

    private synchronized void tick() {
        if (!running) {
            return;
        }
        long elapsed = System.nanoTime() - startNanos;
        long window = Math.min(elapsed, durationNanos);
        long target = rate * window / 1_000_000_000L;
        List<Sensor> capteurs = publisher.capteurs();

        // Limite atteinte : on n'envoie plus rien dans cette tranche ; les mesures en retard
        // partiront des que des reponses arrivent (jamais au-dela de maxInFlight en cours).
        while (attempted.get() < target && permits.tryAcquire()) {
            Sensor s = capteurs.get((int) (index++ % capteurs.size()));
            Sample mesure = scenarios.mesure(s.type());
            attempted.incrementAndGet();
            inFlight.incrementAndGet();
            try {
                publisher.envoyer(s, mesure).whenComplete((ignored, error) -> {
                    if (error == null) {
                        acked.incrementAndGet();
                    } else {
                        failed.incrementAndGet();
                    }
                    done();
                });
            } catch (RuntimeException e) {
                failed.incrementAndGet();
                done();
            }
        }
        if (elapsed >= durationNanos) {
            requestCompletion();
        }
    }

    /** Fin d'une mesure (acceptee ou en echec definitif) : libere sa place. */
    private void done() {
        permits.release();
        if (inFlight.decrementAndGet() == 0) {
            finishIfReady();
        }
    }

    private synchronized void requestCompletion() {
        completionRequested = true;
        if (task != null) {
            task.cancel(false);
        }
        finishIfReady();
    }

    private synchronized void finishIfReady() {
        if (running && completionRequested && inFlight.get() == 0) {
            finish();
        }
    }

    private synchronized void finish() {
        if (task != null) {
            task.cancel(false);
        }
        publisher.setPause(false);
        running = false;
        endedAt = Instant.now();
    }
}