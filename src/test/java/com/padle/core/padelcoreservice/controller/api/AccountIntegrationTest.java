package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.padelcoreservice.model.*;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import com.padle.core.padelcoreservice.repository.*;
import com.padle.core.padelcoreservice.service.*;
import com.padle.core.padelcoreservice.security.oauth2.CustomOAuth2User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"recaptcha.enabled=false", "app.security.whitelisted-ips=127.0.0.1", "app.auth.google-enabled=false"})
@AutoConfigureMockMvc
@Transactional
class AccountIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired PlayerRepository players;
    @Autowired PasswordEncoder encoder;
    @Autowired PasswordResetTokenRepository tokens;
    @MockitoBean EmailService email;
    @MockitoBean RecaptchaService captcha;
    private PlayerPadel player() {
        return players.save(PlayerPadel.builder().nombre("Maria").apellido("Garcia")
                .email(UUID.randomUUID()+"@gmail.com").passwordHash(encoder.encode("secret-password"))
                .nivelJugador(Nivel.C7).activo(true).emailConfirmado(true).telegramUsername("@player_"+UUID.randomUUID().toString().replace("-", "").substring(0,20))
                .preferredLocale("es").build());
    }
    private String update(PlayerPadel p) {
        return "{\"nombre\":\"Changed\",\"apellido\":\"Garcia\",\"email\":\""+p.getEmail()+"\",\"telefono\":\"\",\"telegramUsername\":\""+p.getTelegramUsername()+"\",\"nivelJugador\":\"C7\",\"preferredLocale\":\"ru\"}";
    }
    @Test void realLoginSessionRotationProfileRefreshAndLogout() throws Exception {
        var p=player(); var before=new MockHttpSession(); String old=before.getId();
        var login=mvc.perform(post("/login").session(before).with(req -> { req.setRequestedSessionId(old); req.setRequestedSessionIdValid(true); return req; }).with(csrf()).accept("application/json")
                .param("username",p.getEmail()).param("password","secret-password").param("remember-me","true")
                .param("destination","/torneo/42#tournament-registration"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.session.authenticated").value(true))
                .andExpect(jsonPath("$.destination").value("/torneo/42#tournament-registration")).andReturn();
        var session=(MockHttpSession)login.getRequest().getSession(false);
        assertThat(session.getId()).isNotEqualTo(old);
        assertThat(login.getResponse().getCookie("remember-me")).isNotNull();
        mvc.perform(put("/api/players/me").session(session).with(csrf()).contentType("application/json").content(update(p)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("Changed"));
        mvc.perform(get("/api/auth/session").session(session)).andExpect(jsonPath("$.fullName").value("Changed Garcia"));
        mvc.perform(post("/logout").session(session).with(csrf()).accept("application/json"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("LOGGED_OUT"));
    }
    @Test void failedLoginAndCsrfAreJsonAndLocalized() throws Exception {
        mvc.perform(post("/login").with(csrf()).accept("application/json").param("lang","ru")
                .param("username","missing@gmail.com").param("password","wrong"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("LOGIN_INVALID"))
                .andExpect(jsonPath("$.detail").value("Неверная почта или пароль."));
        mvc.perform(post("/login").accept("application/json"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }
    @Test void anonymousAndOwnerCannotReadPlayerWithSameId() throws Exception {
        var p=player();
        mvc.perform(get("/api/players/me")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("SESSION_REQUIRED"));
        var owner=Owner.builder().id(p.getId()).email("owner@example.test").build();
        mvc.perform(get("/api/players/me").with(user(owner))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PLAYER_REQUIRED"));
    }
    @Test void googlePrincipalCanReadOwnProfileWithoutKnownPassword() throws Exception {
        var p=player(); var oauth=new CustomOAuth2User(mock(OAuth2User.class),p);
        var auth=new UsernamePasswordAuthenticationToken(oauth,null,oauth.getAuthorities());
        mvc.perform(get("/api/players/me").with(authentication(auth))).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(p.getId()));
        mvc.perform(get("/api/auth/session").with(authentication(auth))).andExpect(status().isOk()).andExpect(jsonPath("$.authenticated").value(true));
    }
    @Test void invalidProfileAndPasswordDoNotPartiallyMutatePlayer() throws Exception {
        var p=player();
        mvc.perform(put("/api/players/me").with(user(p)).with(csrf()).contentType("application/json")
                .content(update(p).replace(p.getTelegramUsername(),"")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.fieldErrors[0].code").value("CONTACT_REQUIRED"));
        assertThat(players.findById(p.getId()).orElseThrow().getNombre()).isEqualTo("Maria");
        mvc.perform(post("/api/players/me/password").with(user(p)).with(csrf()).contentType("application/json")
                .content("{\"currentPassword\":\"wrong\",\"newPassword\":\"another-secret\",\"confirmPassword\":\"another-secret\"}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.fieldErrors[0].code").value("CURRENT_PASSWORD_INVALID"));
    }
    @Test void validPasswordChangeUsesCurrentPassword() throws Exception {
        var p=player();
        mvc.perform(post("/api/players/me/password").with(user(p)).with(csrf()).contentType("application/json")
                .content("{\"currentPassword\":\"secret-password\",\"newPassword\":\"another-secret\",\"confirmPassword\":\"another-secret\"}"))
                .andExpect(status().isOk());
        assertThat(encoder.matches("another-secret",players.findById(p.getId()).orElseThrow().getPassword())).isTrue();
    }
    private String registration(String email) {
        return "{\"nombre\":\"Maria\",\"apellido\":\"Garcia\",\"email\":\""+email+"\",\"telegramUsername\":\"@player_"+UUID.randomUUID().toString().substring(0,8)+"\",\"password\":\"secret-password\",\"confirmPassword\":\"secret-password\",\"nivelJugador\":\"C7\"}";
    }
    @Test void registrationValidationAndDuplicateHaveStableFieldsWithoutSecrets() throws Exception {
        when(captcha.verify(any())).thenReturn(true);
        var p=player();
        mvc.perform(post("/api/players/registro").with(csrf()).contentType("application/json").content(registration(p.getEmail())))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].code").value("EMAIL_DUPLICATE"));
        mvc.perform(post("/api/players/registro").with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.fieldErrors[0].field").exists());
    }
    @Test void anonymousRegistrationAcceptsTelegramOnlyAndAutomaticallyConfirmsEmail() throws Exception {
        when(captcha.verify(any())).thenReturn(true); String email=UUID.randomUUID()+"@gmail.com";
        mvc.perform(post("/api/players/registro").with(csrf()).contentType("application/json").content(registration(email)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("REGISTERED"));
        assertThat(players.findByEmail(email).orElseThrow().isEmailConfirmado()).isTrue();
    }
    @Test void honeypotAndCaptchaPreventCreation() throws Exception {
        var email=UUID.randomUUID()+"@gmail.com";
        mvc.perform(post("/api/players/registro").with(csrf()).contentType("application/json").content(registration(email)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.fieldErrors[0].code").value("CAPTCHA_INVALID"));
        assertThat(players.existsByEmail(email)).isFalse();
    }
    @Test void resetRequestDoesNotEnumerateEmailsAndTokenIsSingleUse() throws Exception {
        var p=player();
        for(String address: java.util.List.of(p.getEmail(),"unknown@gmail.com")) {
            mvc.perform(post("/api/password-reset/request").with(csrf()).contentType("application/json").content("{\"email\":\""+address+"\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("RESET_REQUESTED"));
        }
        var token=tokens.save(PasswordResetToken.builder().token(UUID.randomUUID().toString()).player(p)
                .expiryDate(LocalDateTime.now().plusHours(1)).used(false).build());
        mvc.perform(get("/api/password-reset/validate").param("token", token.getToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valid").value(true));
        String body="{\"token\":\""+token.getToken()+"\",\"newPassword\":\"another-secret\",\"confirmPassword\":\"another-secret\",\"destination\":\"//evil.test\"}";
        mvc.perform(post("/api/password-reset/confirm").with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.destination").value("/login"));
        mvc.perform(post("/api/password-reset/confirm").with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.fieldErrors[0].code").value("RESET_TOKEN_INVALID"));
    }
    @Test void invalidTokenAndPublicConfigAreSafe() throws Exception {
        mvc.perform(get("/api/password-reset/validate").param("token","unknown"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valid").value(false));
        mvc.perform(get("/api/public/config").param("lang","en"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.googleEnabled").value(false))
                .andExpect(jsonPath("$.locale").value("en")).andExpect(jsonPath("$.secretKey").doesNotExist());
    }
}
