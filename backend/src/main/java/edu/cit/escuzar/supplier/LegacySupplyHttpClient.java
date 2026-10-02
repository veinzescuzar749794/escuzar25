package edu.cit.escuzar.supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import edu.cit.escuzar.AppInstance;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * Package-private resilient HTTP client for interacting with LegacySupply.
 * Features:
 * - 3-second connect and read timeouts
 * - Retries on 503 / transient network errors with backoff (up to 3 attempts)
 * - Automatic session renewal on 401
 * - Idempotency through X-Request-Id preservation
 */
@Component
class LegacySupplyHttpClient implements LegacySupplyClient {

    private static final Logger log = LoggerFactory.getLogger(LegacySupplyHttpClient.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(3);

    private final LegacySupplyProperties properties;
    private final LegacySupplySessionManager sessionManager;
    private final HttpClient httpClient;
    private final AppInstance instance;

    LegacySupplyHttpClient(LegacySupplyProperties properties, LegacySupplySessionManager sessionManager, AppInstance instance) {
        this.properties = properties;
        this.sessionManager = sessionManager;
        this.instance = instance;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CALL_TIMEOUT)
                .build();
    }

    /**
     * Submits a purchase order to LegacySupply with retry, idempotency, and session renewal.
     */
    public LegacySupplyXmlParser.PurchaseOrderAckDto placePurchaseOrder(
            String supplierSku,
            int cases,
            String buyerRef,
            String requestId
    ) {
        String xmlBody = LegacySupplyXmlParser.buildPurchaseOrder(supplierSku, cases, buyerRef);
        long backoffMs = 500;
        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                String token = sessionManager.getValidSessionToken();

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(properties.getBaseUrl() + "/purchase-orders"))
                        .timeout(CALL_TIMEOUT)
                        .header("Content-Type", "application/xml")
                        .header("X-LS-Session", token)
                        .header("X-Request-Id", requestId)
                        .header("X-Client-Instance", instance.id())
                        .POST(HttpRequest.BodyPublishers.ofString(xmlBody))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 201) {
                    log.info("Purchase order accepted by LegacySupply for BuyerRef={}, Sku={}, Qty={}",
                            buyerRef, supplierSku, cases);
                    return LegacySupplyXmlParser.parsePurchaseOrderAck(response.body());
                }

                if (response.statusCode() == 401) {
                    log.warn("Session refused during placePurchaseOrder (attempt {}). Invalidating and retrying...", attempt);
                    sessionManager.invalidateSession();
                    if (attempt < MAX_ATTEMPTS) {
                        continue;
                    }
                }

                // If 503 or transient outage
                if (response.statusCode() == 503 && attempt < MAX_ATTEMPTS) {
                    log.warn("LegacySupply returned 503 on placePurchaseOrder attempt {}. Backing off {}ms...", attempt, backoffMs);
                    Thread.sleep(backoffMs);
                    backoffMs *= 2;
                    continue;
                }

                // If 409 (IDEM-04) or order already exists, check by BuyerRef before failing
                if (response.statusCode() == 409) {
                    log.warn("LegacySupply returned 409 for BuyerRef={}. Checking existing order...", buyerRef);
                    Optional<LegacySupplyXmlParser.PurchaseOrderStatusDto> existing = findOrderByBuyerRef(buyerRef);
                    if (existing.isPresent()) {
                        LegacySupplyXmlParser.PurchaseOrderStatusDto o = existing.get();
                        return new LegacySupplyXmlParser.PurchaseOrderAckDto(
                                o.poNumber(), o.statusCode(), o.supplierSku(), o.qty(), o.uom(), o.buyerRef(), o.createdAt()
                        );
                    }
                }

                LegacySupplyXmlParser.LSErrorDto err = LegacySupplyXmlParser.parseLSError(response.body());
                throw new IllegalStateException("LegacySupply rejected order with HTTP "
                        + response.statusCode() + " (" + err.code() + ": " + err.message() + ")");

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Order placement interrupted", e);
            } catch (Exception e) {
                lastException = e;
                log.warn("placePurchaseOrder attempt {} failed for BuyerRef={}: {}", attempt, buyerRef, e.getMessage());
                if (attempt < MAX_ATTEMPTS) {
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Backoff interrupted", ie);
                    }
                    backoffMs *= 2;
                }
            }
        }

        throw new IllegalStateException("Failed to place purchase order after " + MAX_ATTEMPTS + " attempts: "
                + (lastException != null ? lastException.getMessage() : "Unknown error"), lastException);
    }

    /**
     * Polls status of a specific purchase order.
     */
    public Optional<LegacySupplyXmlParser.PurchaseOrderStatusDto> getOrderStatus(String poNumber) {
        long backoffMs = 500;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                String token = sessionManager.getValidSessionToken();

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(properties.getBaseUrl() + "/purchase-orders/" + poNumber))
                        .timeout(CALL_TIMEOUT)
                        .header("X-LS-Session", token)
                        .header("X-Client-Instance", instance.id())
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    return Optional.of(LegacySupplyXmlParser.parsePurchaseOrderStatus(response.body()));
                }

                if (response.statusCode() == 404) {
                    log.warn("Order {} not found on LegacySupply (404)", poNumber);
                    return Optional.empty();
                }

                if (response.statusCode() == 401) {
                    sessionManager.invalidateSession();
                    if (attempt < MAX_ATTEMPTS) {
                        continue;
                    }
                }

                if (response.statusCode() == 503 && attempt < MAX_ATTEMPTS) {
                    Thread.sleep(backoffMs);
                    backoffMs *= 2;
                    continue;
                }

                log.warn("Failed to get order status for {}: HTTP {}", poNumber, response.statusCode());
                return Optional.empty();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            } catch (Exception e) {
                log.warn("Attempt {} to getOrderStatus for {} failed: {}", attempt, poNumber, e.getMessage());
                if (attempt < MAX_ATTEMPTS) {
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return Optional.empty();
                    }
                    backoffMs *= 2;
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Finds orders on LegacySupply by buyerRef.
     */
    public Optional<LegacySupplyXmlParser.PurchaseOrderStatusDto> findOrderByBuyerRef(String buyerRef) {
        try {
            String token = sessionManager.getValidSessionToken();
            String encodedRef = URLEncoder.encode(buyerRef, StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getBaseUrl() + "/purchase-orders?buyerRef=" + encodedRef))
                    .timeout(CALL_TIMEOUT)
                    .header("X-LS-Session", token)
                    .header("X-Client-Instance", instance.id())
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                LegacySupplyXmlParser.PurchaseOrderListDto list = LegacySupplyXmlParser.parsePurchaseOrderList(response.body());
                if (list.count() > 0 && !list.orders().isEmpty()) {
                    return Optional.of(list.orders().get(0));
                }
            } else if (response.statusCode() == 401) {
                sessionManager.invalidateSession();
            }
        } catch (Exception e) {
            log.warn("Error querying LegacySupply by buyerRef={}: {}", buyerRef, e.getMessage());
        }
        return Optional.empty();
    }
}
