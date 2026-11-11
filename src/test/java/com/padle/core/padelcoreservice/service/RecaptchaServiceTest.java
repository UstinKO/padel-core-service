package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.config.RecaptchaProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.ResourceAccessException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RecaptchaServiceTest {
    @Test void outageDoesNotAuthorizeRegistrationOrPutSecretInUrl() {
        var props = new RecaptchaProperties(); props.setSecretKey("private-key");
        var http = mock(RestTemplate.class);
        when(http.postForObject(eq("https://www.google.com/recaptcha/api/siteverify"), any(), eq(RecaptchaService.Verification.class)))
                .thenThrow(new ResourceAccessException("unavailable"));
        assertThat(new RecaptchaService(props,http).verify("private-token")).isFalse();
        verify(http).postForObject(eq("https://www.google.com/recaptcha/api/siteverify"), any(), eq(RecaptchaService.Verification.class));
    }
    @Test void disabledCaptchaOnlyAllowsConfiguredBypass() {
        var props = new RecaptchaProperties(); props.setEnabled(false);
        var http = mock(RestTemplate.class);
        assertThat(new RecaptchaService(props,http).verify(null)).isTrue();
        verifyNoInteractions(http);
    }
}
