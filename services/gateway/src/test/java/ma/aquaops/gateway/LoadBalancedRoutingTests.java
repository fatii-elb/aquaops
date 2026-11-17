package ma.aquaops.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.sun.net.httpserver.HttpServer;

/**
 * Real HTTP through the gateway with the "eureka" profile: lb://assets-service
 * is resolved by Spring Cloud LoadBalancer to a fake assets-service. The
 * instance list comes from the simple discovery client (no Eureka server in a
 * unit test); with Eureka the same LoadBalancer reads the Eureka registry.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "eureka.client.enabled=false")
@ActiveProfiles("eureka")
class LoadBalancedRoutingTests {

	private static final AtomicReference<String> RECEIVED = new AtomicReference<>();
	private static final HttpServer FAKE_ASSETS = startFakeAssets();

	private static final HttpClient HTTP = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.build();

	@Value("${local.server.port}")
	private int gatewayPort;

	@DynamicPropertySource
	static void instances(DynamicPropertyRegistry registry) {
		registry.add("spring.cloud.discovery.client.simple.instances.assets-service[0].uri",
				() -> "http://127.0.0.1:" + FAKE_ASSETS.getAddress().getPort());
	}

	@AfterAll
	static void stopFakeAssets() {
		FAKE_ASSETS.stop(0);
	}

	@Test
	void requestReachesTheDiscoveredInstanceWithItsPathUnchanged() throws Exception {
		HttpResponse<String> response = get("/api/assets/zones/Z1?page=2");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).isEqualTo("fake assets-service");
		assertThat(RECEIVED.get()).isEqualTo("GET /api/assets/zones/Z1?page=2");
	}

	@Test
	void serviceWithoutAnyInstanceAnswers503() throws Exception {
		// detection-service has no instance: the gateway must not invent one.
		assertThat(get("/api/detection/rules").statusCode()).isEqualTo(503);
	}

	@Test
	void pathOutsideTheApiPrefixesIsNotRouted() throws Exception {
		assertThat(get("/assets/zones").statusCode()).isEqualTo(404);
	}

	private HttpResponse<String> get(String path) throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + gatewayPort + path))
				.timeout(Duration.ofSeconds(10))
				.GET()
				.build();
		return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
	}

	private static HttpServer startFakeAssets() {
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			server.createContext("/", exchange -> {
				RECEIVED.set(exchange.getRequestMethod() + " " + exchange.getRequestURI());
				byte[] body = "fake assets-service".getBytes(StandardCharsets.UTF_8);
				exchange.sendResponseHeaders(200, body.length);
				exchange.getResponseBody().write(body);
				exchange.close();
			});
			server.start();
			return server;
		} catch (IOException e) {
			throw new IllegalStateException("cannot start the fake assets-service", e);
		}
	}

}
