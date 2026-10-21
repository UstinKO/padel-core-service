package com.padle.core.padelcoreservice.controller;

import com.padle.core.padelcoreservice.dto.DuplicatesAuditReportDto;
import com.padle.core.padelcoreservice.dto.DuplicatesAuditSummaryDto;
import com.padle.core.padelcoreservice.service.PlayerDuplicatesAuditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * LFPT-0677: {@code GET /api/admin/players/duplicates-audit} — только read-only доступ для
 * {@code ROLE_SUPER_ADMIN}, остальным (включая {@code ORGANIZER}) и анониму — отказ. Сервис
 * замокан — сами эвристики проверяет {@link com.padle.core.padelcoreservice.service.PlayerDuplicatesAuditServiceTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.mail.username=test@example.com",
        "spring.mail.password=test-password",
        "spring.security.oauth2.client.registration.google.client-id=test-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-client-secret",
        "recaptcha.site-key=test-site-key",
        "recaptcha.secret-key=test-secret-key",
})
class AdminPlayerAuditControllerSecurityTest {

    private static final String URL = "/api/admin/players/duplicates-audit";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlayerDuplicatesAuditService playerDuplicatesAuditService;

    @Test
    void anonimo_esRedirigidoALogin() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));

        verifyNoInteractions(playerDuplicatesAuditService);
    }

    @Test
    void jugador_esRechazado() throws Exception {
        mockMvc.perform(get(URL).with(user("player@test").roles("PLAYER")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(playerDuplicatesAuditService);
    }

    @Test
    void organizerSinSuperAdmin_esRechazado() throws Exception {
        mockMvc.perform(get(URL).with(user("owner@test").roles("ORGANIZER")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(playerDuplicatesAuditService);
    }

    @Test
    void superAdmin_obtieneElReporte() throws Exception {
        DuplicatesAuditReportDto report = new DuplicatesAuditReportDto(
                new DuplicatesAuditSummaryDto(10L, 0L, 10L, Map.of()),
                List.of(), List.of(), List.of(), Map.of());
        when(playerDuplicatesAuditService.buildReport()).thenReturn(report);

        mockMvc.perform(get(URL).with(user("root@test").roles("SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"));

        verify(playerDuplicatesAuditService).buildReport();
    }
}
