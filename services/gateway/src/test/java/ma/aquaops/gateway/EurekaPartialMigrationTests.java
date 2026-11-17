package ma.aquaops.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.test.context.ActiveProfiles;

/**
 * Migration one service at a time: with the "eureka" profile, a service whose
 * *_URL is given keeps its fixed address; the others use Eureka.
 */
@SpringBootTest(properties = {
		"eureka.client.register-with-eureka=false",
		"eureka.client.fetch-registry=false",
		"TELEMETRY_URL=http://telemetry:8081",
		"SIMULATOR_URL=http://simulator:8086" })
@ActiveProfiles("eureka")
class EurekaPartialMigrationTests {

	@Autowired
	private RouteLocator routeLocator;

	@Test
	void givenUrlKeepsTheFixedAddressForThatServiceOnly() {
		assertThat(RouteTargets.uris(routeLocator)).containsExactlyInAnyOrderEntriesOf(Map.of(
				"telemetry", "http://telemetry:8081",
				"assets", "lb://assets-service",
				"detection", "lb://detection-service",
				"incidents", "lb://incidents-service",
				"notifications", "lb://notifications-service",
				"simulator", "http://simulator:8086"));
	}

}
