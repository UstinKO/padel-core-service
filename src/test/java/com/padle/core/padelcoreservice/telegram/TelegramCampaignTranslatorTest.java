package com.padle.core.padelcoreservice.telegram;

import com.padle.core.padelcoreservice.config.TelegramTranslationProperties;
import com.padle.core.padelcoreservice.exception.AccountException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class TelegramCampaignTranslatorTest {
    private final RestTemplate http = new RestTemplate();
    private final ObjectMapper json = new ObjectMapper();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(http).build();
    private final TelegramCampaignTranslator translator = new TelegramCampaignTranslator(
            new TelegramTranslationProperties(true, "qa-key", "gpt-6.1-terra"), http, json);

    @Test void sendsOnlySourceAndKeepsOriginalRussianAndSpanishExactly() {
        for (String language : new String[]{"ru", "es"}) {
            String source = language.equals("ru") ? "Привет 👋 18:30 https://1-padel.com" : "¡Hola! 👋 18:30 https://1-padel.com";
            server.expect(requestTo("https://api.openai.com/v1/responses"))
                    .andExpect(header("Authorization", "Bearer qa-key"))
                    .andExpect(jsonPath("$.input").value(source)).andExpect(jsonPath("$.store").value(false))
                    .andExpect(jsonPath("$.text.format.strict").value(true))
                    .andRespond(withSuccess(response(language, "Español", "Русский", "English"), MediaType.APPLICATION_JSON));
            var translated = translator.translate(source);
            assertThat(translated.texts().forLanguage(language)).isEqualTo(source);
            assertThat(translated.sourceLanguage()).isEqualTo(language);
            server.verify(); server.reset();
        }
    }
    @Test void unavailableProviderDoesNotMakeRequest() {
        var disabled = new TelegramCampaignTranslator(new TelegramTranslationProperties(false, "", "gpt-6.1-terra"), http, json);
        assertThatThrownBy(() -> disabled.translate("Hola")).isInstanceOf(AccountException.class)
                .hasMessage("TELEGRAM_TRANSLATION_UNAVAILABLE");
        server.verify();
    }
    @Test void refusalIncompleteMissingAndTooLongResultsNeverBecomeTranslations() {
        for (String body : new String[]{"{\"status\":\"incomplete\",\"output\":[]}",
                "{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"refusal\"}]}]}",
                response("ru", "", "Русский", "English"), response("ru", "x".repeat(4097), "Русский", "English"),
                "{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"null\"}]}]}"}) {
            server.expect(requestTo("https://api.openai.com/v1/responses")).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
            assertThatThrownBy(() -> translator.translate("Привет")).isInstanceOf(AccountException.class)
                    .hasMessage("TELEGRAM_TRANSLATION_FAILED");
            server.verify(); server.reset();
        }
    }
    @Test void providerFailureHasOnlyLocalErrorAndNoAutomaticRetry() {
        server.expect(requestTo("https://api.openai.com/v1/responses")).andRespond(withServerError());
        assertThatThrownBy(() -> translator.translate("Привет")).isInstanceOf(AccountException.class)
                .hasMessage("TELEGRAM_TRANSLATION_FAILED");
        server.verify();
    }
    private String response(String language, String es, String ru, String en) {
        String text = json.writeValueAsString(new TelegramCampaignTranslator.Translation(language, es, ru, en));
        return "{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":"
                + json.writeValueAsString(text) + "}]}]}";
    }
}
