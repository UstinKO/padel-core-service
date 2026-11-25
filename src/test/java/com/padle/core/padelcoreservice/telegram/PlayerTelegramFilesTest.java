package com.padle.core.padelcoreservice.telegram;

import com.padle.core.padelcoreservice.config.PlayerTelegramProperties;
import com.padle.core.padelcoreservice.exception.AccountException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class PlayerTelegramFilesTest {
    private MockRestServiceServer server;
    private PlayerTelegramFiles files;
    @BeforeEach void setup() {
        var http = new RestTemplate();
        server = MockRestServiceServer.bindTo(http).build();
        files = new PlayerTelegramFiles(new PlayerTelegramProperties(true,"FixtureBot",false,24,3,
                null,null,false,"fixture-token",false,null,null), http);
    }
    private void metadata(String path, long size) {
        server.expect(requestTo("https://api.telegram.org/botfixture-token/getFile"))
                .andExpect(jsonPath("$.file_id").value("fixture-file"))
                .andRespond(withSuccess("{\"ok\":true,\"result\":{\"file_path\":\"" + path + "\",\"file_size\":" + size + "}}",
                        MediaType.APPLICATION_JSON));
    }
    @Test void downloadsFromTheFixedTelegramHostAndValidatesActualBytes() {
        metadata("documents/file_1.pdf", 16);
        server.expect(requestTo("https://api.telegram.org/file/botfixture-token/documents/file_1.pdf"))
                .andRespond(withSuccess("%PDF-1.7\nfixture", MediaType.APPLICATION_OCTET_STREAM));
        assertThat(files.download("fixture-file").contentType()).isEqualTo("application/pdf");
        server.verify();
    }
    @Test void rejectsRemotePathsAndFilesOverTheLimitBeforeDownload() {
        metadata("https://attacker.example/file.pdf", 12);
        assertThatThrownBy(() -> files.download("fixture-file")).isInstanceOf(AccountException.class);
        server.verify(); server.reset();
        metadata("documents/file.pdf", 5_242_881);
        assertThatThrownBy(() -> files.download("fixture-file")).isInstanceOf(AccountException.class);
        server.verify();
    }
    @Test void aLyingSizeCannotBypassTheStreamingLimit() {
        metadata("documents/file.pdf", 8);
        var oversized = new byte[5_242_881];
        System.arraycopy("%PDF-".getBytes(StandardCharsets.US_ASCII),0,oversized,0,5);
        server.expect(anything()).andRespond(withSuccess(oversized, MediaType.APPLICATION_OCTET_STREAM));
        assertThatThrownBy(() -> files.download("fixture-file")).isInstanceOf(AccountException.class);
        server.verify();
    }
    @Test void networkErrorsDoNotExposeTheBotTokenOrUnderlyingCause() {
        server.expect(anything()).andRespond(request -> { throw new ResourceAccessException("https://secret-token.example"); });
        assertThatThrownBy(() -> files.download("fixture-file")).isInstanceOf(RestClientException.class)
                .hasMessage("Файл временно недоступен").hasNoCause();
    }
}
