package ma.aquaops.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TelemetryIngestClientTests {

    private HttpServer server;
    private AtomicReference<String> receivedKey;
    private AtomicReference<String> receivedBody;
    private AtomicInteger responseStatus;
    private TelemetryIngestClient client;
    /** Statuts a renvoyer dans l'ordre (puis responseStatus) et cles Idempotency-Key recues. */
    private final ConcurrentLinkedQueue<Integer> scripted = new ConcurrentLinkedQueue<>();
    private final List<String> idempotencyKeys = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        receivedKey = new AtomicReference<>();
        receivedBody = new AtomicReference<>();
        responseStatus = new AtomicInteger(202);
        scripted.clear();
        idempotencyKeys.clear();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(TelemetryIngestClient.INGEST_PATH, exchange -> {
            receivedKey.set(exchange.getRequestHeaders().getFirst(TelemetryIngestClient.API_KEY_HEADER));
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String key = exchange.getRequestHeaders().getFirst(TelemetryIngestClient.IDEMPOTENCY_KEY_HEADER);
            idempotencyKeys.add(key == null ? "<none>" : key);
            Integer next = scripted.poll();
            exchange.sendResponseHeaders(next != null ? next : responseStatus.get(), -1);
            exchange.close();
        });
        server.start();
        URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort()
                + TelemetryIngestClient.INGEST_PATH);
        client = new TelemetryIngestClient(
                HttpClient.newHttpClient(), endpoint, "test-secret", Duration.ofSeconds(2));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void envoieLaMesureAvecLaCleApi() {
        String payload = "{\"eventVersion\":\"1\",\"sensorId\":42}";

        client.publish(payload).join();

        assertThat(receivedKey.get()).isEqualTo("test-secret");
        assertThat(receivedBody.get()).isEqualTo(payload);
    }

    @Test
    void uneReponseNon2xxSignaleUnEchec() {
        responseStatus.set(429);

        assertThatThrownBy(() -> client.publish("{}").join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(TelemetryIngestClient.TelemetryPublishException.class);
    }

    // RC-04 / RC-05 : identifiant de mesure et nouvelles tentatives sures.
    private static final String ID = "9a1b2c3d-4e5f-4a6b-8c7d-0e1f2a3b4c5d";

    @Test
    void envoieLIdentifiantDeLaMesureDansIdempotencyKey() {
        client.publish("{}", ID).join();

        assertThat(idempotencyKeys).containsExactly(ID);
    }

    @Test
    void uneErreurPassagereEstRenvoyeeAvecLaMemeCleJusquAuSucces() {
        scripted.add(503);
        scripted.add(502);

        client.publish("{}", ID).join();

        assertThat(idempotencyKeys).containsExactly(ID, ID, ID);
        assertThat(client.retries()).isEqualTo(2);
    }

    @Test
    void uneMesureRefuseeNEstPasRenvoyee() {
        responseStatus.set(400);

        assertThatThrownBy(() -> client.publish("{}", ID).join())
                .hasCauseInstanceOf(TelemetryIngestClient.TelemetryPublishException.class);
        assertThat(idempotencyKeys).containsExactly(ID);
        assertThat(client.retries()).isZero();
    }

    @Test
    void sansIdentifiantAucunRenvoiPourNePasCreerDeDoublon() {
        responseStatus.set(503);

        assertThatThrownBy(() -> client.publish("{}").join())
                .hasCauseInstanceOf(TelemetryIngestClient.TelemetryPublishException.class);
        assertThat(idempotencyKeys).containsExactly("<none>");
    }

    @Test
    void lesRenvoisSontBornes() {
        responseStatus.set(503);

        assertThatThrownBy(() -> client.publish("{}", ID).join())
                .hasCauseInstanceOf(TelemetryIngestClient.TelemetryPublishException.class);
        assertThat(idempotencyKeys).hasSize(3);
    }
}
