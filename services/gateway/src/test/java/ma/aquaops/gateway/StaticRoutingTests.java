package ma.aquaops.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.context.ApplicationContext;

import com.netflix.discovery.EurekaClient;

/**
 * Default profile = Sprint 4 routing, unchanged: fixed addresses, no Eureka.
 */
@SpringBootTest
class StaticRoutingTests {

	@Autowired
	private RouteLocator routeLocator;

	@Autowired
	private ApplicationContext context;

	@Test
	void sixRoutesKeepTheirApiPrefix() {
		Map<String, Route> routes = RouteTargets.byId(routeLocator);

		assertThat(routes).containsOnlyKeys(RouteTargets.PREFIXES.keySet());
		RouteTargets.PREFIXES.forEach((id, prefix) ->
				assertThat(routes.get(id).getPredicate().toString()).as(id).contains(prefix));
	}

	@Test
	void routesUseTheSameFixedDefaultsAsBefore() {
		assertThat(RouteTargets.uris(routeLocator)).containsExactlyInAnyOrderEntriesOf(Map.of(
				"telemetry", "http://localhost:8081",
				"assets", "http://localhost:8082",
				"detection", "http://localhost:8083",
				"incidents", "http://localhost:8084",
				"notifications", "http://localhost:8085",
				"simulator", "http://localhost:8086"));
	}

	@Test
	void eurekaClientIsOff() {
		assertThat(context.getBeanNamesForType(EurekaClient.class)).isEmpty();
	}

}
