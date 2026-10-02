package edu.cit.escuzar.channel;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class ChannelProperties {
    final String baseUrl;
    final String clientId;
    final String apiKey;

    ChannelProperties(@Value("${tiangge.base-url:https://legacysupply.onrender.com/tiangge/v1}") String baseUrl,
                      @Value("${tiangge.client-id:}") String clientId,
                      @Value("${tiangge.api-key:}") String apiKey) {
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.clientId = clientId;
        this.apiKey = apiKey;
    }
}
