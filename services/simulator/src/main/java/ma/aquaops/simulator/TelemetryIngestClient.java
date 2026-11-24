package ma.aquaops.simulator;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Envoi d'une mesure a la passerelle (POST /api/telemetry/readings).
 *
 * RC-04 / RC-05 (mesure en bac a sable k3s, 23/11/2026) :
 *  - un seul client HTTP/1.1 (pas de tentative de montee en HTTP/2 sur http://), connexions
 *    reutilisees, executeur de taille fixe : le client par defaut creait un fil et une connexion
 *    par requete en cours, sans limite ;
 *  - chaque mesure porte un identifiant (en-tete Idempotency-Key, UUID) : une mesure renvoyee
 *    apres une reponse perdue ou une erreur passagere n'est enregistree qu'une fois (telemetry) ;
 *  - nouvelle tentative seulement pour une erreur passagere (429, 5xx, delai, connexion), avec
 *    la MEME cle ; jamais pour une mesure refusee (400...).
 */
@Component
public class TelemetryIngestClient {

    static final String API_KEY_HEADER = "X-API-Key";
    static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    static final String INGEST_PATH = "/api/telemetry/readings";

    private final HttpClient httpClient;
    private final URI endpoint;
    private final String apiKey;
    private final Duration requestTimeout;
    private final int maxAttempts;
    private final Duration retryBackoff;
    private final AtomicLong retries = new AtomicLong();

    @Autowired
    public TelemetryIngestClient(
            @Value("${simulator.gateway-url}") String gatewayUrl,
            @Value("${simulator.api-key}") String apiKey,
            @Value("${simulator.request-timeout:5s}") Duration requestTimeout,
            @Value("${simulator.request-attempts:3}") int maxAttempts,
            @Value("${simulator.retry-backoff:200ms}") Duration retryBackoff,
            @Value("${simulator.client-threads:8}") int clientThreads) {
        this(HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_1_1)
                        .connectTimeout(requestTimeout)
                        .executor(fixedPool(clientThreads))
                        .build(),
                URI.create(gatewayUrl).resolve(INGEST_PATH), apiKey, requestTimeout, maxAttempts, retryBackoff);
    }

    TelemetryIngestClient(HttpClient httpClient, URI endpoint, String apiKey, Duration requestTimeout) {
        this(httpClient, endpoint, apiKey, requestTimeout, 3, Duration.ofMillis(50));
    }

    TelemetryIngestClient(HttpClient httpClient, URI endpoint, String apiKey, Duration requestTimeout,
                          int maxAttempts, Duration retryBackoff) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("SIMULATOR_API_KEY ne peut pas etre vide");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("simulator.request-attempts doit valoir au moins 1");
        }
        this.httpClient = httpClient;
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.requestTimeout = requestTimeout;
        this.maxAttempts = maxAttempts;
        this.retryBackoff = retryBackoff;
    }

    /** Sans identifiant de mesure : une seule tentative (un renvoi pourrait creer un doublon). */
    public CompletableFuture<Void> publish(String payload) {
        return attempt(payload, null, 1, 1);
    }

    /** Avec identifiant de mesure : nouvelles tentatives sures (meme cle a chaque fois). */
    public CompletableFuture<Void> publish(String payload, String readingId) {
        return attempt(payload, readingId, 1, readingId == null ? 1 : maxAttempts);
    }

    /** Nombre total de nouvelles tentatives depuis le demarrage (mesure du test de charge). */
    public long retries() {
        return retries.get();
    }

    private CompletableFuture<Void> attempt(String payload, String readingId, int attempt, int attempts) {
        return send(payload, readingId).handle((ok, error) -> {
            if (error == null) {
                return CompletableFuture.<Void>completedFuture(null);
            }
            Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
            if (attempt >= attempts || !isTransient(cause)) {
                return CompletableFuture.<Void>failedFuture(cause);
            }
            retries.incrementAndGet();
            long delayMs = retryBackoff.toMillis() * attempt;   // 200 ms, puis 400 ms
            Executor later = CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS);
            return CompletableFuture.supplyAsync(() -> null, later)
                    .thenCompose(ignored -> attempt(payload, readingId, attempt + 1, attempts));
        }).thenCompose(f -> f);
    }

    private CompletableFuture<Void> send(String payload, String readingId) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .header(API_KEY_HEADER, apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(payload));
        if (readingId != null) {
            builder.header(IDEMPOTENCY_KEY_HEADER, readingId);
        }
        return httpClient.sendAsync(builder.build(), HttpResponse.BodyHandlers.discarding())
                .thenCompose(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        return CompletableFuture.completedFuture(null);
                    }
                    return CompletableFuture.failedFuture(
                            new TelemetryPublishException(response.statusCode()));
                });
    }

    /** Erreur qui peut disparaitre en reessayant : surcharge, pod en cours de remplacement, delai. */
    static boolean isTransient(Throwable error) {
        if (error instanceof TelemetryPublishException http) {
            return http.statusCode() == 429 || http.statusCode() >= 500;
        }
        return error instanceof HttpTimeoutException || error instanceof IOException;
    }

    private static Executor fixedPool(int threads) {
        return Executors.newFixedThreadPool(Math.max(1, threads), r -> {
            Thread t = new Thread(r, "telemetry-ingest-client");
            t.setDaemon(true);
            return t;
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
