package ma.aquaops.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;

/**
 * The *_URL variables used by deploy/test/compose.yml (Sprint 4) still work.
 */
@SpringBootTest(properties = {
		"TELEMETRY_URL=http://telemetry:8081",
		"ASSETS_URL=http://assets:8082",
		"DETECTION_URL=http://detection:8083",
		"INCIDENTS_URL=http://incidents:8084",
		"NOTIFICATIONS_URL=http://notifications:8085",
		"SIMULATOR_URL=http://simulator:8086" })
class StaticRoutingVariablesTests {

	@Autowired
	private RouteLocator routeLocator;

	@Test
	void composeVariablesStillChooseTheTargets() {
		assertThat(RouteTargets.uris(routeLocator)).containsExactlyInAnyOrderEntriesOf(Map.of(
				"telemetry", "http://telemetry:8081",
				"assets", "http://assets:8082",
				"detection", "http://detection:8083",
				"incidents", "http://incidents:8084",
				"notifications", "http://notifications:8085",
				"simulator", "http://simulator:8086"));
	}

}
