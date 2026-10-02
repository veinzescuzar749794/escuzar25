package edu.cit.escuzar.supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import edu.cit.escuzar.AppInstance;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

/**
 * Package-private session manager for LegacySupply.
 * Manages token acquisition, caching, proactive expiry refresh, and reactive invalidation.
 */
@Component
class LegacySupplySessionManager {

    private static final Logger log = LoggerFactory.getLogger(LegacySupplySessionManager.class);
    private static final long SESSION_LIFETIME_SECONDS = 120; // Empirically verified TTL
    private static final long PROACTIVE_REFRESH_SECONDS = 100; // 20s safety buffer

    private final LegacySupplyProperties properties;
    private final AppInstance instance;
    private final HttpClient httpClient;

    private volatile String currentToken = null;
    private volatile Instant tokenAcquiredAt = null;

    LegacySupplySessionManager(LegacySupplyProperties properties, AppInstance instance) {
        this.properties = properties;
        this.instance = instance;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    public synchronized String getValidSessionToken() {
        if (currentToken != null && tokenAcquiredAt != null) {
            long ageSeconds = Duration.between(tokenAcquiredAt, Instant.now()).getSeconds();
            if (ageSeconds < PROACTIVE_REFRESH_SECONDS) {
                return currentToken;
            }
            log.info("LegacySupply session token age is {}s (>= {}s threshold). Refreshing session.",
                    ageSeconds, PROACTIVE_REFRESH_SECONDS);
        }

        return acquireNewSession();
    }

    public synchronized void invalidateSession() {
        log.info("Invalidating cached LegacySupply session token.");
        this.currentToken = null;
        this.tokenAcquiredAt = null;
    }

    private String acquireNewSession() {
        String authXml = LegacySupplyXmlParser.buildAuthRequest(
                properties.getClientId(),
                properties.getApiKey()
        );

        int maxAttempts = 3;
        long backoffMs = 500;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(properties.getBaseUrl() + "/auth/token"))
                        .timeout(Duration.ofSeconds(3))
                        .header("Content-Type", "application/xml")
                        .header("X-Client-Instance", instance.id())
                        .POST(HttpRequest.BodyPublishers.ofString(authXml))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    LegacySupplyXmlParser.AuthResponseDto auth = LegacySupplyXmlParser.parseAuthResponse(response.body());
                    this.currentToken = auth.sessionToken();
                    this.tokenAcquiredAt = Instant.now();
                    log.info("Successfully acquired new LegacySupply session token. IssuedAt: {}", auth.issuedAt());
                    return this.currentToken;
                } else if (response.statusCode() == 503 && attempt < maxAttempts) {
                    log.warn("LegacySupply auth returned 503 on attempt {}. Retrying in {}ms...", attempt, backoffMs);
                    Thread.sleep(backoffMs);
                    backoffMs *= 2;
                } else {
                    LegacySupplyXmlParser.LSErrorDto err = LegacySupplyXmlParser.parseLSError(response.body());
                    throw new IllegalStateException("LegacySupply authentication rejected with status "
                            + response.statusCode() + " (" + err.code() + ": " + err.message() + ")");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Authentication interrupted", e);
            } catch (Exception e) {
                if (attempt < maxAttempts) {
                    log.warn("LegacySupply auth attempt {} failed ({}). Retrying in {}ms...",
                            attempt, e.getMessage(), backoffMs);
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Retry interrupted", ie);
                    }
                    backoffMs *= 2;
                } else {
                    throw new IllegalStateException("Failed to authenticate with LegacySupply after "
                            + maxAttempts + " attempts: " + e.getMessage(), e);
                }
            }
        }

        throw new IllegalStateException("Exhausted authentication retries without success");
    }
}
