package com.padle.core.padelcoreservice.marketing;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MetaCapiClientTest {
    private final MetaProperties properties = new MetaProperties(true, "930260823500064", "test-secret", "v26.0", true, "TEST123");
    private final HttpClient http = mock(HttpClient.class);
    private final MetaCapiClient client = new MetaCapiClient(JsonMapper.builder().build(), properties, http);

    @Test
    void offlinePurchaseHasActualAmountAndParticipantIdentifiersWithoutOrganizerOrContactData() {
        var delivery = delivery();
        String payload = JsonMapper.builder().build().writeValueAsString(client.payload(delivery));
        assertThat(payload).contains("\"value\":400.00", "\"paid_participants\":0", "\"action_source\":\"other\"", "TEST123")
                .doesNotContain("test-secret", "client_ip_address", "client_user_agent", "event_source_url", "utm_", "email", "phone");
        assertThat(payload).contains(MetaCapiClient.hash(delivery.visitorId().toString())).doesNotContain(delivery.visitorId().toString());
        assertThat(properties.toString()).doesNotContain("test-secret");
    }

    @Test
    @SuppressWarnings("unchecked")
    void acceptanceRequiresSuccessfulStatusAndAnActuallyAcceptedEvent() throws IOException, InterruptedException {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"events_received\":0}", "{\"events_received\":1}");
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        assertThat(client.send(delivery()).accepted()).isFalse();
        assertThat(client.send(delivery()).accepted()).isTrue();
        ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(2)).send(request.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(request.getValue().uri().toString()).isEqualTo("https://graph.facebook.com/v26.0/930260823500064/events");
        assertThat(request.getValue().uri().getQuery()).isNull();
    }

    @Test
    void unavailableMetaReturnsRetryableResultWithoutThrowing() throws IOException, InterruptedException {
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new IOException("offline"));
        assertThat(client.send(delivery())).isEqualTo(new MetaCapiClient.Result(false, 0));
    }

    @Test
    void disabledOrIncompleteConfigurationNeverSends() {
        var disabled = new MetaCapiClient(JsonMapper.builder().build(), new MetaProperties(false, "", "", "", true, ""), http);
        assertThat(disabled.send(delivery()).accepted()).isFalse();
        verifyNoInteractions(http);
        assertThat(new MetaProperties(true, "930260823500064", "test-secret", "v26.0", true, "").serverEnabled()).isFalse();
    }

    private MarketingOutbox.Delivery delivery() {
        return new MarketingOutbox.Delivery(UUID.randomUUID(), 123, UUID.randomUUID(), Instant.parse("2026-10-30T12:00:00Z"),
                new BigDecimal("400.00"), "ARS", 0, 1, "fb.1.1700000000000.click", "fb.1.1700000000000.123");
    }
}
