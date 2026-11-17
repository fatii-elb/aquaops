package ma.aquaops.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

/**
 * "eureka" profile: every route goes through Eureka (lb://service-name), same
 * /api prefixes. No registry in this test: registration and fetch are off.
 */
@SpringBootTest(properties = {
		"eureka.client.register-with-eureka=false",
		"eureka.client.fetch-registry=false" })
@ActiveProfiles("eureka")
class EurekaRoutingProfileTests {

	@Autowired
	private RouteLocator routeLocator;

	@Autowired
	private Environment environment;

	@Test
	void everyRouteUsesTheServiceNameInEureka() {
		assertThat(RouteTargets.uris(routeLocator)).containsExactlyInAnyOrderEntriesOf(Map.of(
				"telemetry", "lb://telemetry-service",
				"assets", "lb://assets-service",
				"detection", "lb://detection-service",
				"incidents", "lb://incidents-service",
				"notifications", "lb://notifications-service",
				"simulator", "lb://simulator"));
	}

	@Test
	void apiPrefixesAreUnchanged() {
		Map<String, Route> routes = RouteTargets.byId(routeLocator);

		assertThat(routes).containsOnlyKeys(RouteTargets.PREFIXES.keySet());
		RouteTargets.PREFIXES.forEach((id, prefix) ->
				assertThat(routes.get(id).getPredicate().toString()).as(id).contains(prefix));
	}

	@Test
	void stoppedInstancesAreNotKeptInACache() {
		assertThat(environment.getProperty("eureka.client.enabled")).isEqualTo("true");
		assertThat(environment.getProperty("eureka.client.registry-fetch-interval-seconds")).isEqualTo("5");
		assertThat(environment.getProperty("spring.cloud.loadbalancer.cache.enabled")).isEqualTo("false");
	}

}
