package ma.aquaops.simulator;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import ma.aquaops.simulator.scenario.Sample;
import ma.aquaops.simulator.scenario.ScenarioRegistry;

/** Mode charge (NFR-01) : debit soutenu, avec comptage des messages confirmes par Kafka. */
@Component
public class LoadGenerator {

    private static final String TOPIC = "sensor-readings";

    public record Status(boolean running, int rate, int seconds,
                         long attempted, long acked, long failed,
                         String startedAt, String endedAt) {}

    private final KafkaTemplate<String, String> kafka;
    private final SimulatorPublisher publisher;
    private final ScenarioRegistry scenarios;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "load-generator");
        t.setDaemon(true);
        return t;
    });

    private final AtomicLong attempted = new AtomicLong();
    private final AtomicLong acked = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();

    private ScheduledFuture<?> task;
    private volatile boolean running;
    private volatile Instant startedAt;
    private volatile Instant endedAt;
    private int rate;
    private int seconds;
    private long durationNanos;
    private long startNanos;
    private long index;

    public LoadGenerator(KafkaTemplate<String, String> kafka,
                         SimulatorPublisher publisher,
                         ScenarioRegistry scenarios) {
        this.kafka = kafka;
        this.publisher = publisher;
        this.scenarios = scenarios;
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
        endedAt = null;
        startedAt = Instant.now();
        startNanos = System.nanoTime();
        publisher.setPause(true);
        running = true;
        task = executor.scheduleAtFixedRate(this::tick, 0, 10, TimeUnit.MILLISECONDS);
    }

    public synchronized void stop() {
        if (running) {
            finish();
        }
    }

    public Status status() {
        return new Status(running, rate, seconds, attempted.get(), acked.get(), failed.get(),
                startedAt == null ? null : startedAt.toString(),
                endedAt == null ? null : endedAt.toString());
    }

    private synchronized void tick() {
        if (!running) {
            return;
        }
        long elapsed = System.nanoTime() - startNanos;
        long window = Math.min(elapsed, durationNanos);
        long target = rate * window / 1_000_000_000L;
        List<Sensor> capteurs = publisher.capteurs();

        while (attempted.get() < target) {
            Sensor s = capteurs.get((int) (index++ % capteurs.size()));
            Sample mesure = scenarios.mesure(s.type());
            attempted.incrementAndGet();
            try {
                kafka.send(TOPIC, String.valueOf(s.sensorId()), publisher.enJson(s, mesure))
                        .whenComplete((result, erreur) -> {
                            if (erreur == null) {
                                acked.incrementAndGet();
                            } else {
                                failed.incrementAndGet();
                            }
                        });
            } catch (Exception e) {
                failed.incrementAndGet();
            }
        }
        if (elapsed >= durationNanos) {
            finish();
        }
    }

    private synchronized void finish() {
        task.cancel(false);
        kafka.flush(); // attend que tous les messages soient confirmes ou en echec
        publisher.setPause(false);
        running = false;
        endedAt = Instant.now();
    }
}