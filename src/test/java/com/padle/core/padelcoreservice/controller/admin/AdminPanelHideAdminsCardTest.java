package com.padle.core.padelcoreservice.controller.admin;

import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.enums.OwnerRole;
import com.padle.core.padelcoreservice.repository.OwnerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * LFPT-0547: главная админ-панель (GET /admin) скрывает карточку статистики «Администраторы»
 * для клубной учётной записи (CLUB_ADMIN) — эта информация касается всей платформы, а не
 * конкретного клуба. Для остальных ролей карточка должна рендериться как раньше (регресс).
 * Маркер карточки в HTML — уникальная для неё иконка {@code fa-user-cog}.
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
@Transactional
class AdminPanelHideAdminsCardTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private OwnerRepository ownerRepository;

    @Test
    void clubAdmin_tarjetaDeAdministradoresNoApareceEnElHtml() throws Exception {
        mockMvc.perform(get("/admin").with(user(createOwner(OwnerRole.CLUB_ADMIN))))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("fa-user-cog"))));
    }

    @ParameterizedTest
    @EnumSource(value = OwnerRole.class, names = "CLUB_ADMIN", mode = EnumSource.Mode.EXCLUDE)
    void otrosRoles_tarjetaDeAdministradoresSigueApareciendo_regresion(OwnerRole role) throws Exception {
        mockMvc.perform(get("/admin").with(user(createOwner(role))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("fa-user-cog")));
    }

    private Owner createOwner(OwnerRole role) {
        String suffix = "LFPT0547-" + UUID.randomUUID();
        return ownerRepository.save(Owner.builder()
                .email(suffix + "@example.com")
                .password("irrelevant-hash")
                .firstName("Test")
                .lastName(role.name())
                .role(role)
                .isActive(true)
                .build());
    }
}
