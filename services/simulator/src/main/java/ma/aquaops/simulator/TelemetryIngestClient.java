package ma.aquaops.simulator;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TelemetryIngestClient {

    static final String API_KEY_HEADER = "X-API-Key";
    static final String INGEST_PATH = "/api/telemetry/readings";

    private final HttpClient httpClient;
    private final URI endpoint;
    private final String apiKey;
    private final Duration requestTimeout;

    @Autowired
    public TelemetryIngestClient(
            @Value("${simulator.gateway-url}") String gatewayUrl,
            @Value("${simulator.api-key}") String apiKey,
            @Value("${simulator.request-timeout:5s}") Duration requestTimeout) {
        this(HttpClient.newBuilder().connectTimeout(requestTimeout).build(),
                URI.create(gatewayUrl).resolve(INGEST_PATH), apiKey, requestTimeout);
    }

    TelemetryIngestClient(HttpClient httpClient, URI endpoint, String apiKey, Duration requestTimeout) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("SIMULATOR_API_KEY ne peut pas etre vide");
        }
        this.httpClient = httpClient;
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.requestTimeout = requestTimeout;
    }

    public CompletableFuture<Void> publish(String payload) {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .header(API_KEY_HEADER, apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .thenCompose(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        return CompletableFuture.completedFuture(null);
                    }
                    return CompletableFuture.failedFuture(
                            new TelemetryPublishException(response.statusCode()));
                });
    }

    static final class TelemetryPublishException extends RuntimeException {
        private final int statusCode;

        TelemetryPublishException(int statusCode) {
            super("La passerelle a repondu avec le statut HTTP " + statusCode);
            this.statusCode = statusCode;
        }

        int statusCode() {
            return statusCode;
        }
    }
}
