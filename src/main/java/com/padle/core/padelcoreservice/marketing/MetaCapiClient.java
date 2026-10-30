package com.padle.core.padelcoreservice.marketing;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

@Component
public class MetaCapiClient {
    private final ObjectMapper json;
    private final MetaProperties properties;
    private final HttpClient http;

    @Autowired
    public MetaCapiClient(ObjectMapper json, MetaProperties properties) {
        this(json, properties, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
    }

    MetaCapiClient(ObjectMapper json, MetaProperties properties, HttpClient http) {
        this.json = json;
        this.properties = properties;
        this.http = http;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Envelope(List<ServerEvent> data, String test_event_code) { }
    record ServerEvent(String event_name, long event_time, String event_id, String action_source,
                       UserData user_data, CustomData custom_data) { }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record UserData(List<String> external_id, String fbc, String fbp) { }
    record CustomData(BigDecimal value, String currency, List<String> content_ids,
                      String content_type, int num_items, int paid_participants) { }
    public record Result(boolean accepted, int httpStatus) { }

    Envelope payload(MarketingOutbox.Delivery delivery) {
        UserData user = new UserData(List.of(hash(delivery.visitorId().toString())), delivery.fbc(), delivery.fbp());
        CustomData custom = new CustomData(delivery.amount(), delivery.currency(),
                List.of(Long.toString(delivery.tournamentId())), "product", 1, delivery.paidParticipants());
        ServerEvent event = new ServerEvent("Purchase", delivery.occurredAt().getEpochSecond(),
                delivery.eventId().toString(), "other", user, custom);
        return new Envelope(List.of(event), properties.testMode() ? properties.testEventCode() : null);
    }

    public Result send(MarketingOutbox.Delivery delivery) {
        if (!properties.serverEnabled()) {
            return new Result(false, 0);
        }
        URI endpoint = URI.create("https://graph.facebook.com/" + properties.apiVersion() + "/" + properties.pixelId() + "/events");
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(5))
                .header("Authorization", "Bearer " + properties.accessToken()).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload(delivery)))).build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            boolean accepted = response.statusCode() == 200
                    && json.readTree(response.body()).path("events_received").asInt(0) == 1;
            return new Result(accepted, response.statusCode());
        } catch (IOException | JacksonException exception) {
            return new Result(false, 0);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new Result(false, 0);
        }
    }

    static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 недоступен", exception);
        }
    }
}
