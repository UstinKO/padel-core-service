package com.padle.core.padelcoreservice.controller.admin;

import com.padle.core.persistence.clubs.Club;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.enums.OwnerRole;
import com.padle.core.persistence.clubs.ClubRepository;
import com.padle.core.padelcoreservice.support.OwnerFixtures;
import com.padle.core.padelcoreservice.service.ClubAccountService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.mail.username=test@example.com", "spring.mail.password=test-password",
        "spring.security.oauth2.client.registration.google.client-id=test-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-client-secret",
        "recaptcha.site-key=test-site-key", "recaptcha.secret-key=test-secret-key"
})
@Transactional
class ClubAccountUiTest {
    @Autowired MockMvc mvc;
    @Autowired OwnerFixtures owners;
    @Autowired ClubRepository clubs;
    @Autowired ClubAccountService accounts;

    @ParameterizedTest
    @CsvSource({"ru,Назначить площадки", "es,Asignar sedes", "en,Assign venues"})
    void localizedPagesRenderAssignmentsOnlyForSuperAdmin(String language, String heading) throws Exception {
        Owner root = account(OwnerRole.SUPER_ADMIN);
        Owner clubAccount = account(OwnerRole.CLUB_ADMIN);
        Club first = clubs.save(Club.builder().nombre("QA Norte " + UUID.randomUUID()).isActive(true).build());
        Club second = clubs.save(Club.builder().nombre("QA Sur " + UUID.randomUUID()).isActive(true).build());
        Set<Long> selected = Set.of(first.getId(), second.getId());
        accounts.assign(root, clubAccount.getId(), selected);
        mvc.perform(get("/admin/club-accounts").param("lang", language).with(user(root)))
                .andExpect(status().isOk()).andExpect(content().string(containsString(clubAccount.getEmail())))
                .andExpect(content().string(not(containsString(clubAccount.getPassword()))));
        mvc.perform(get("/admin/club-accounts/" + clubAccount.getId()).param("lang", language).with(user(root)))
                .andExpect(status().isOk()).andExpect(content().string(containsString(heading)))
                .andExpect(model().attribute("assignedClubIds", selected))
                .andExpect(content().string(containsString(first.getNombre())))
                .andExpect(content().string(containsString(second.getNombre())))
                .andExpect(content().string(not(containsString("??club_access."))));
        mvc.perform(get("/admin/club-accounts").with(user(clubAccount)).header("Accept", "application/json"))
                .andExpect(status().isForbidden());
    }

    private Owner account(OwnerRole role) {
        return owners.save(Owner.builder().email(UUID.randomUUID() + "@example.com")
                .password("never-render-this-password").firstName("QA").lastName("Club").role(role).build());
    }
}
