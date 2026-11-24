package ma.aquaops.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.junit.jupiter.api.Test;

import ma.aquaops.simulator.scenario.ReadingType;
import ma.aquaops.simulator.scenario.ScenarioRegistry;

/**
 * RC-04 / RC-05 : quand la chaine ne repond plus, le generateur ne doit pas accumuler des requetes
 * sans limite (cause des arrets OOM du simulateur, 384 Mi puis 640 Mi). Il s'arrete a max-in-flight
 * et le dit (notSent), puis termine proprement quand les reponses arrivent.
 */
class LoadGeneratorBoundedTests {

    private static final int MAX_IN_FLIGHT = 20;

    private final ConcurrentLinkedQueue<CompletableFuture<Void>> pending = new ConcurrentLinkedQueue<>();

    private LoadGenerator generatorWithStalledChain() {
        SimulatorPublisher publisher = mock(SimulatorPublisher.class);
        when(publisher.capteurs()).thenReturn(List.of(new Sensor(1, 1, ReadingType.PRESSURE)));
        when(publisher.envoyer(any(), any())).thenAnswer(invocation -> {
            CompletableFuture<Void> neverAnswered = new CompletableFuture<>();
            pending.add(neverAnswered);
            return neverAnswered;
        });
        return new LoadGenerator(publisher, new ScenarioRegistry(Clock.systemUTC()), MAX_IN_FLIGHT);
    }

    @Test
    void sansReponseLeNombreDeRequetesEnCoursResteBorne() throws Exception {
        LoadGenerator load = generatorWithStalledChain();

        load.start(500, 2);
        Thread.sleep(1_000);

        LoadGenerator.Status status = load.status();
        assertThat(status.inFlight()).isEqualTo(MAX_IN_FLIGHT);
        assertThat(status.attempted()).isEqualTo(MAX_IN_FLIGHT);
        assertThat(pending).hasSize(MAX_IN_FLIGHT);
        // 500/s pendant ~1 s demandees : le retard est visible au lieu d'etre empile en memoire.
        assertThat(status.notSent()).isGreaterThan(400);
        load.stop();
        pending.forEach(f -> f.complete(null));
    }

    @Test
    void quandLesReponsesArriventLeTestSeTermineEtLesComptesSontJustes() throws Exception {
        LoadGenerator load = generatorWithStalledChain();

        load.start(100, 1);
        long deadline = System.currentTimeMillis() + 10_000;
        while (load.status().running() && System.currentTimeMillis() < deadline) {
            CompletableFuture<Void> next;
            while ((next = pending.poll()) != null) {
                next.complete(null);
            }
            Thread.sleep(5);
        }

        LoadGenerator.Status status = load.status();
        assertThat(status.running()).isFalse();
        assertThat(status.inFlight()).isZero();
        assertThat(status.acked() + status.failed()).isEqualTo(status.attempted());
        assertThat(status.attempted() + status.notSent()).isEqualTo(100);
    }
}
