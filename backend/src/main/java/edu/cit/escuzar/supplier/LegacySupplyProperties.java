package edu.cit.escuzar.supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Package-private configuration properties for LegacySupply integration.
 * Securely reads credentials without hardcoding secrets in Git.
 */
@Component
class LegacySupplyProperties {

    private final String baseUrl;
    private final String clientId;
    private final String apiKey;

    LegacySupplyProperties(
            @Value("${legacy-supply.base-url:https://legacysupply.onrender.com/api/v1}") String baseUrl,
            @Value("${legacy-supply.client-id:22-1382-413}") String clientId,
            @Value("${LS_API_KEY:${legacy-supply.api-key:}}") String apiKey
    ) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.clientId = clientId;
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getClientId() {
        return clientId;
    }

    public String getApiKey() {
        return apiKey;
    }
}
