package ma.aquaops.gateway;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;

/** Test helper: the routes the gateway really built, by id. */
final class RouteTargets {

	/** The 6 routes and their /api prefix: the API contract. */
	static final Map<String, String> PREFIXES = Map.of(
			"telemetry", "/api/telemetry/**",
			"assets", "/api/assets/**",
			"detection", "/api/detection/**",
			"incidents", "/api/incidents/**",
			"notifications", "/api/notifications/**",
			"simulator", "/api/simulator/**");

	private RouteTargets() {
	}

	static Map<String, Route> byId(RouteLocator locator) {
		return locator.getRoutes()
				.collectMap(Route::getId)
				.block();
	}

	static Map<String, String> uris(RouteLocator locator) {
		return byId(locator).values().stream()
				.collect(Collectors.toMap(Route::getId, r -> r.getUri().toString()));
	}

}
