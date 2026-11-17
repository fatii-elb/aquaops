package ma.aquaops.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = "simulator.interval-ms=200")
class SimulatorPublisherHttpTests {

    private static final String API_KEY = "test-api-key";
    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "eventVersion", "sensorId", "zoneId", "type", "value", "unit", "pumpState", "measuredAt");
    private static final ConcurrentLinkedQueue<ReceivedRequest> RECEIVED = new ConcurrentLinkedQueue<>();
    private static final CountDownLatch FIRST_CYCLE = new CountDownLatch(7);
    private static final HttpServer FAKE_GATEWAY = startFakeGateway();

    @Autowired
    private LoadGenerator load;

    @DynamicPropertySource
    static void configureGateway(DynamicPropertyRegistry registry) {
        registry.add("simulator.gateway-url",
                () -> "http://127.0.0.1:" + FAKE_GATEWAY.getAddress().getPort());
        registry.add("simulator.api-key", () -> API_KEY);
    }

    @AfterAll
    static void stopFakeGateway() {
        FAKE_GATEWAY.stop(0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void publisherEnvoieDesMesuresV1AuGatewayAvecLaCleDeService() throws Exception {
        assertThat(FIRST_CYCLE.await(20, TimeUnit.SECONDS)).isTrue();

        JsonMapper json = JsonMapper.builder().build();
        var requests = RECEIVED.stream().limit(7).toList();
        assertThat(requests).hasSize(7);
        for (ReceivedRequest request : requests) {
            assertThat(request.apiKey()).isEqualTo(API_KEY);
            assertThat(request.contentType()).startsWith("application/json");
            Map<String, Object> measure = json.readValue(request.body(), Map.class);
            assertThat(measure.get("eventVersion")).isEqualTo("1");
            assertThat(measure.keySet()).allMatch(ALLOWED_FIELDS::contains);
            assertThat(measure.get("sensorId")).isInstanceOf(Number.class);
            assertThat(measure.get("zoneId")).isInstanceOf(Number.class);
            assertThat(measure.get("type")).isIn("PRESSURE", "FLOW", "LEVEL", "PUMP_STATE");
            if ("PUMP_STATE".equals(measure.get("type"))) {
                assertThat(measure.get("pumpState")).isIn("NORMAL", "FAULT");
                assertThat(measure).doesNotContainKey("value");
            } else {
                assertThat(measure.get("value")).isInstanceOf(Number.class);
                assertThat(measure.get("unit")).isInstanceOf(String.class);
                assertThat(measure).doesNotContainKey("pumpState");
            }
            Instant.parse((String) measure.get("measuredAt"));
        }
    }

    @Test
    void modeChargeCompteLesAcquittementsHttp() throws Exception {
        load.start(200, 3);
        long deadline = System.currentTimeMillis() + 30_000;
        while (load.status().running() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }

        LoadGenerator.Status status = load.status();
        assertThat(status.running()).isFalse();
        assertThat(status.attempted()).isEqualTo(600);
        assertThat(status.acked()).isEqualTo(status.attempted());
        assertThat(status.failed()).isZero();
    }

    private static HttpServer startFakeGateway() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext(TelemetryIngestClient.INGEST_PATH, exchange -> {
                byte[] body = exchange.getRequestBody().readAllBytes();
                ReceivedRequest request = new ReceivedRequest(
                        exchange.getRequestHeaders().getFirst(TelemetryIngestClient.API_KEY_HEADER),
                        exchange.getRequestHeaders().getFirst("Content-Type"),
                        new String(body, StandardCharsets.UTF_8));
                RECEIVED.add(request);
                FIRST_CYCLE.countDown();
                int status = API_KEY.equals(request.apiKey()) ? 202 : 401;
                exchange.sendResponseHeaders(status, -1);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException("impossible de demarrer le faux gateway", e);
        }
    }

    private record ReceivedRequest(String apiKey, String contentType, String body) { }
}