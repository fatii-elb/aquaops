package ma.aquaops.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

class TelemetryIngestFilterTests {

    @Test
    void ingestionWithoutValidKeyIsRejectedBeforeForwarding() {
        TelemetryIngestFilter filter = new TelemetryIngestFilter("expected", 1);
        MockServerWebExchange exchange = exchange("wrong");
        AtomicBoolean forwarded = new AtomicBoolean();

        filter.filter(exchange, markForwarded(forwarded)).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(forwarded).isFalse();
    }

    @Test
    void authenticatedIngestionIsRateLimited() {
        TelemetryIngestFilter filter = new TelemetryIngestFilter("expected", 1);
        MockServerWebExchange first = exchange("expected");
        MockServerWebExchange second = exchange("expected");
        AtomicBoolean forwarded = new AtomicBoolean();

        filter.filter(first, markForwarded(forwarded)).block();
        assertThat(forwarded).isTrue();
        forwarded.set(false);
        filter.filter(second, markForwarded(forwarded)).block();

        assertThat(first.getResponse().getStatusCode()).isNull();
        assertThat(second.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(second.getResponse().getHeaders().getFirst("Retry-After")).isNotBlank();
        assertThat(forwarded).isFalse();
    }

    @Test
    void otherRequestsAreNotRateLimitedOrAuthenticatedHere() {
        TelemetryIngestFilter filter = new TelemetryIngestFilter("expected", 1);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.GET, TelemetryIngestFilter.INGEST_PATH).build());
        AtomicBoolean forwarded = new AtomicBoolean();

        filter.filter(exchange, markForwarded(forwarded)).block();

        assertThat(forwarded).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    private static MockServerWebExchange exchange(String apiKey) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.post(TelemetryIngestFilter.INGEST_PATH)
                        .header(TelemetryIngestFilter.API_KEY_HEADER, apiKey)
                        .build());
    }

    private static org.springframework.cloud.gateway.filter.GatewayFilterChain markForwarded(
            AtomicBoolean forwarded) {
        return exchange -> {
            forwarded.set(true);
            return Mono.empty();
        };
    }
}
