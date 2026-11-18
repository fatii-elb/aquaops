package ma.aquaops.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.CompletionException;
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

    @BeforeEach
    void setUp() throws IOException {
        receivedKey = new AtomicReference<>();
        receivedBody = new AtomicReference<>();
        responseStatus = new AtomicInteger(202);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(TelemetryIngestClient.INGEST_PATH, exchange -> {
            receivedKey.set(exchange.getRequestHeaders().getFirst(TelemetryIngestClient.API_KEY_HEADER));
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(responseStatus.get(), -1);
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
}
