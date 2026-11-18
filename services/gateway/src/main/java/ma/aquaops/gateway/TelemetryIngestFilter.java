package ma.aquaops.gateway;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class TelemetryIngestFilter implements GlobalFilter, Ordered {

    static final String API_KEY_HEADER = "X-API-Key";
    static final String INGEST_PATH = "/api/telemetry/readings";

    private static final Logger LOG = LoggerFactory.getLogger(TelemetryIngestFilter.class);

    private final byte[] expectedApiKey;
    private final Bucket bucket;

    public TelemetryIngestFilter(
            @Value("${aquaops.telemetry-ingest.api-key:}") String apiKey,
            @Value("${aquaops.telemetry-ingest.requests-per-second:500}") long requestsPerSecond) {
        if (requestsPerSecond < 1) {
            throw new IllegalArgumentException("TELEMETRY_INGEST_RATE_LIMIT doit etre positif");
        }
        this.expectedApiKey = apiKey.getBytes(StandardCharsets.UTF_8);
        this.bucket = Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(requestsPerSecond)
                        .refillGreedy(requestsPerSecond, Duration.ofSeconds(1))
                        .build())
                .build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!HttpMethod.POST.equals(exchange.getRequest().getMethod())
                || !INGEST_PATH.equals(exchange.getRequest().getURI().getPath())) {
            return chain.filter(exchange);
        }

        String apiKey = exchange.getRequest().getHeaders().getFirst(API_KEY_HEADER);
        if (!isAuthenticated(apiKey)) {
            LOG.warn("Requete d'ingestion telemetrie rejetee : authentification invalide");
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            long retryAfterSeconds = Math.max(1,
                    TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()));
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            exchange.getResponse().getHeaders().set("Retry-After", Long.toString(retryAfterSeconds));
            return exchange.getResponse().setComplete();
        }

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private boolean isAuthenticated(String apiKey) {
        return expectedApiKey.length > 0
                && apiKey != null
                && MessageDigest.isEqual(expectedApiKey, apiKey.getBytes(StandardCharsets.UTF_8));
    }
}
