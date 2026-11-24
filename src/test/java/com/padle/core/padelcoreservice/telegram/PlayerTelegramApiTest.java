package com.padle.core.padelcoreservice.telegram;

import com.padle.core.padelcoreservice.config.PlayerTelegramProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class PlayerTelegramApiTest {
    private MockRestServiceServer server;
    private PlayerTelegramApi api;
    @BeforeEach void setup() {
        var properties = properties(true, "fixture-token");
        var rest = new RestTemplate();
        server = MockRestServiceServer.bindTo(rest).build();
        api = new PlayerTelegramApi(properties, rest);
    }
    private PlayerTelegramProperties properties(boolean enabled, String token) {
        return new PlayerTelegramProperties(enabled, "OnePadelQaBot", false, 24, 3, null, null, false, token, false, null, null);
    }
    @Test void playerBotRequiresItsOwnTokenAndAnExplicitFeatureFlag() {
        assertThat(new PlayerTelegramApi(properties(true, ""), new RestTemplate()).available()).isFalse();
        assertThat(new PlayerTelegramApi(properties(false, "fixture-token"), new RestTemplate()).available()).isFalse();
        assertThat(api.available()).isTrue();
    }
    @Test void successfulResponseCarriesActualMessageId() {
        server.expect(requestTo("https://api.telegram.org/botfixture-token/sendMessage"))
                .andExpect(jsonPath("$.chat_id").value(123))
                .andRespond(withSuccess("{\"ok\":true,\"result\":{\"message_id\":456}}", MediaType.APPLICATION_JSON));
        var result = api.send(123, "Fixture", List.of());
        assertThat(result.state()).isEqualTo("SENT");
        assertThat(result.messageId()).isEqualTo(456L);
        server.verify();
    }
    @Test void rejectedAndUncertainRequestsAreNeverReportedAsSent() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertThat(api.send(123, "Fixture", List.of()).state()).isEqualTo("BLOCKED");
        server.verify(); server.reset();
        server.expect(anything()).andRespond(request -> { throw new ResourceAccessException("fixture timeout"); });
        assertThat(api.send(123, "Fixture", List.of()).state()).isEqualTo("UNKNOWN");
        server.verify();
    }
    @Test void readsUpdatesWithTheirStableNumericIdentity() {
        server.expect(requestTo("https://api.telegram.org/botfixture-token/getUpdates?offset=12&limit=20&timeout=0"))
                .andRespond(withSuccess("""
                        {"ok":true,"result":[{"update_id":15,"message":{"message_id":8,
                         "from":{"id":123},"chat":{"id":123,"type":"private"},"text":"/start"}}]}
                        """, MediaType.APPLICATION_JSON));
        assertThat(api.updates(12)).singleElement().satisfies(update -> {
            assertThat(update.updateId()).isEqualTo(15);
            assertThat(update.message().from().id()).isEqualTo(123);
        });
    }

    @Test void editsTheOriginalMessageWithOnlyWebsiteLinks() {
        server.expect(requestTo("https://api.telegram.org/botfixture-token/editMessageReplyMarkup"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.chat_id").value(123))
                .andExpect(jsonPath("$.message_id").value(8))
                .andExpect(jsonPath("$.reply_markup.inline_keyboard[0][1].url").value("https://padel.example/players/dashboard"))
                .andExpect(jsonPath("$.reply_markup.inline_keyboard[0][2]").doesNotExist())
                .andExpect(jsonPath("$.reply_markup.inline_keyboard[0][0].url").value("https://padel.example/torneo/10"))
                .andExpect(jsonPath("$.reply_markup.inline_keyboard[0][0].callback_data").doesNotExist())
                .andRespond(withSuccess("{\"ok\":true,\"result\":true}", MediaType.APPLICATION_JSON));
        api.editKeyboard(123, 8, List.of(List.of(
                TelegramModels.Button.link("View", "https://padel.example/torneo/10"),
                TelegramModels.Button.link("Cannot attend", "https://padel.example/players/dashboard"))));
        server.verify();
    }

    @Test void keyboardNetworkFailureOrUnchangedMarkupDoNotUndoConfirmation() {
        server.expect(anything()).andRespond(request -> { throw new ResourceAccessException("fixture timeout"); });
        assertThatCode(() -> api.editKeyboard(123, 8, List.of())).doesNotThrowAnyException();
        server.verify(); server.reset();
        server.expect(anything()).andRespond(withStatus(HttpStatus.BAD_REQUEST));
        assertThatCode(() -> api.editKeyboard(123, 8, List.of())).doesNotThrowAnyException();
        server.verify();
    }
}
