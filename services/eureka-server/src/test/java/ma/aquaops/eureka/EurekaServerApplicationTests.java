package ma.aquaops.eureka;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

import com.netflix.eureka.EurekaServerConfig;

/**
 * Starts the real server on a random port and talks to it over HTTP, with the
 * Eureka REST API the clients use (JDK HttpClient, no Eureka client needed).
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class EurekaServerApplicationTests {

	private static final HttpClient HTTP = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.build();

	@Value("${local.server.port}")
	private int port;

	@Autowired
	private EurekaServerConfig serverConfig;

	@Test
	void healthIsUp() throws Exception {
		HttpResponse<String> response = get("/actuator/health");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).contains("\"status\":\"UP\"");
	}

	@Test
	void kubernetesProbesAreExposed() throws Exception {
		assertThat(get("/actuator/health/liveness").statusCode()).isEqualTo(200);
		assertThat(get("/actuator/health/readiness").statusCode()).isEqualTo(200);
	}

	@Test
	void onlyHealthIsExposedOnActuator() throws Exception {
		assertThat(get("/actuator/env").statusCode()).isEqualTo(404);
		assertThat(get("/actuator/beans").statusCode()).isEqualTo(404);
	}

	@Test
	void registryIsTunedToDropStoppedServicesQuickly() {
		assertThat(serverConfig.shouldEnableSelfPreservation()).isFalse();
		assertThat(serverConfig.getEvictionIntervalTimerInMs()).isEqualTo(5000L);
		assertThat(serverConfig.shouldUseReadOnlyResponseCache()).isFalse();
	}

	@Test
	void serviceRegistersAndLeavesOnShutdown() throws Exception {
		String app = "TEST-REGISTER";

		assertThat(register(app, "test-register:1", 30).statusCode()).isEqualTo(204);

		HttpResponse<String> listed = get("/eureka/apps/" + app);
		assertThat(listed.statusCode()).isEqualTo(200);
		assertThat(listed.body()).contains("test-register:1");

		// What a client sends when it stops normally (graceful shutdown).
		assertThat(delete("/eureka/apps/" + app + "/test-register:1").statusCode()).isEqualTo(200);
		assertThat(get("/eureka/apps/" + app).statusCode()).isEqualTo(404);
	}

	@Test
	void instanceThatStopsRenewingIsEvicted() throws Exception {
		String app = "TEST-EVICTION";

		// Lease of 2 s and no renewal: the instance "crashed" without saying so.
		assertThat(register(app, "test-eviction:1", 2).statusCode()).isEqualTo(204);
		assertThat(get("/eureka/apps/" + app).statusCode()).isEqualTo(200);

		long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
		int status = 200;
		while (status == 200 && System.nanoTime() < deadline) {
			Thread.sleep(500);
			status = get("/eureka/apps/" + app).statusCode();
		}
		assertThat(status).as("instance still listed after 30 s").isEqualTo(404);
	}

	private HttpResponse<String> register(String app, String instanceId, int leaseSeconds)
			throws IOException, InterruptedException {
		String body = """
				{"instance": {
				  "instanceId": "%s",
				  "hostName": "127.0.0.1",
				  "app": "%s",
				  "ipAddr": "127.0.0.1",
				  "status": "UP",
				  "port": {"$": 9999, "@enabled": "true"},
				  "leaseInfo": {"renewalIntervalInSecs": 1, "durationInSecs": %d},
				  "dataCenterInfo": {
				    "@class": "com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo",
				    "name": "MyOwn"
				  }
				}}
				""".formatted(instanceId, app, leaseSeconds);
		HttpRequest request = HttpRequest.newBuilder(uri("/eureka/apps/" + app))
				.header("Content-Type", "application/json")
				.header("Accept", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.build();
		return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> get(String path) throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(uri(path))
				.header("Accept", "application/json")
				.GET()
				.build();
		return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> delete(String path) throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(uri(path)).DELETE().build();
		return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
	}

	private URI uri(String path) {
		return URI.create("http://127.0.0.1:" + port + path);
	}

}
