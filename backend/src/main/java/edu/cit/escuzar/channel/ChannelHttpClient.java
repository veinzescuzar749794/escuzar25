package edu.cit.escuzar.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cit.escuzar.AppInstance;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
class ChannelHttpClient {
    private final ChannelProperties properties;
    private final AppInstance instance;
    private final ObjectMapper mapper;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    ChannelHttpClient(ChannelProperties properties, AppInstance instance, ObjectMapper mapper) {
        this.properties = properties;
        this.instance = instance;
        this.mapper = mapper;
    }

    JsonNode get(String path) { return send("GET", path, null); }
    JsonNode post(String path, Object body) { return send("POST", path, body); }
    JsonNode put(String path, Object body) { return send("PUT", path, body); }

    private JsonNode send(String method, String path, Object body) {
        if (properties.apiKey.isBlank() || properties.clientId.isBlank()) {
            throw new IllegalStateException("Tiangge credentials are missing; set TIANGGE_CLIENT_ID and TIANGGE_API_KEY.");
        }
        String json;
        try { json = body == null ? "" : mapper.writeValueAsString(body); }
        catch (Exception e) { throw new IllegalStateException("Could not encode Tiangge request", e); }
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(properties.baseUrl + path))
                        .timeout(Duration.ofSeconds(10))
                        .header("Accept", "application/json")
                        .header("Content-Type", "application/json")
                        .header("X-Client-Id", properties.clientId)
                        .header("Authorization", "Bearer " + properties.apiKey)
                        .header("X-Client-Instance", instance.id());
                builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
                HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return response.body().isBlank() ? mapper.createObjectNode() : mapper.readTree(response.body());
                }
                HttpFailure failure = new HttpFailure(response.statusCode(), "Tiangge " + method + " " + path
                        + " returned HTTP " + response.statusCode() + ": " + response.body());
                if (response.statusCode() < 500 || attempt == 3) throw failure;
                lastError = failure;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Tiangge request interrupted", e);
            } catch (HttpFailure e) {
                if (e.statusCode < 500 || attempt == 3) throw e;
                lastError = e;
            } catch (Exception e) {
                lastError = new IllegalStateException("Tiangge request failed for " + path, e);
                if (attempt == 3) throw lastError;
            }
            try { Thread.sleep(250L * attempt); }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Tiangge retry interrupted", e);
            }
        }
        throw lastError == null ? new IllegalStateException("Tiangge request failed for " + path) : lastError;
    }

    private static class HttpFailure extends IllegalStateException {
        final int statusCode;
        HttpFailure(int statusCode, String message) { super(message); this.statusCode = statusCode; }
    }
}
