package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.application.clubs.ClubReadPort;
import com.padle.core.application.clubs.PublicClubQuery;
import com.padle.core.padelcoreservice.adapter.clubs.LegacyClubReadAdapter;
import com.padle.core.padelcoreservice.model.Club;
import com.padle.core.padelcoreservice.repository.ClubRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.mail.username=test@example.com", "spring.mail.password=test-password",
        "spring.security.oauth2.client.registration.google.client-id=test-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-client-secret",
        "recaptcha.site-key=test-site-key", "recaptcha.secret-key=test-secret-key",
        "app.base-url=https://example.test///"
})
class PublicClubApplicationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PublicClubQuery query;
    @MockitoSpyBean ClubRepository clubs;
    @Autowired ApplicationContext context;
    @Autowired PlatformTransactionManager transactions;
    @MockitoSpyBean LegacyClubReadAdapter adapter;
    private final List<Long> owned = new ArrayList<>();

    @AfterEach
    void removeOwnClubs() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> clubs.deleteAllById(owned));
    }

    @Test
    void queryIsSingleProxyAndPortRunsInsideReadOnlyTransaction() {
        var club = create("Транзакция " + UUID.randomUUID(), true);
        assertThat(AopUtils.isAopProxy(query)).isTrue();
        assertThat(context.getBeansOfType(PublicClubQuery.class)).hasSize(1);
        assertThat(context.getBeansOfType(ClubReadPort.class)).hasSize(1);
        doAnswer(invocation -> {
            assertReadOnlyTransaction();
            return invocation.callRealMethod();
        }).when(adapter).findActiveOrderedByNameAndId();
        doAnswer(invocation -> {
            assertReadOnlyTransaction();
            return invocation.callRealMethod();
        }).when(adapter).findActiveById(club.getId());

        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(query.clubs()).extracting(value -> value.id()).contains(club.getId());
        assertThat(query.club(club.getId())).isPresent();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    @Test
    void adapterPreservesActiveFilterAndNameThenIdOrdering() {
        String prefix = "Порядок " + UUID.randomUUID();
        var last = create(prefix + " Z", true);
        var first = create(prefix + " A", true);
        var second = create(prefix + " B", true);
        var hidden = create(prefix + " Hidden", false);

        assertThat(query.clubs().stream().filter(value -> value.name().startsWith(prefix)))
                .extracting(value -> value.id()).containsExactly(first.getId(), second.getId(), last.getId());
        verify(clubs).findByIsActiveTrue(Sort.by("nombre", "id"));
        assertThat(query.club(hidden.getId())).isEmpty();
    }

    @Test
    void httpKeepsExactlyNinePublicFieldsNullsCanonicalUrlAndHeaders() throws Exception {
        var club = create("Публичный " + UUID.randomUUID(), true);
        var response = mvc.perform(get("/api/public/clubs/" + club.getId()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andReturn().getResponse();
        var value = json.readTree(response.getContentAsString());
        assertThat(value.propertyNames()).containsExactlyInAnyOrder("id", "name", "address", "city",
                "description", "logoUrl", "mapUrl", "websiteUrl", "canonicalUrl");
        assertThat(value.path("address").isNull()).isTrue();
        assertThat(value.path("description").isNull()).isTrue();
        assertThat(value.path("canonicalUrl").asString()).isEqualTo("https://example.test/clubs/" + club.getId());
        assertThat(response.getContentAsString()).doesNotContain("private-contact", "private@example.test");
    }

    @Test
    void emptyReadKeepsEmptyJsonArray() throws Exception {
        doReturn(List.of()).when(adapter).findActiveOrderedByNameAndId();
        mvc.perform(get("/api/public/clubs")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().json("[]"));
    }

    @Test
    void unknownInactiveZeroAndNegativeIdsKeepSameLocalizedNotFound() throws Exception {
        var hidden = create("Неактивный " + UUID.randomUUID(), false);
        for (Long id : List.of(hidden.getId(), Long.MAX_VALUE, 0L, -1L)) {
            mvc.perform(get("/api/public/clubs/" + id).header("Accept-Language", "ru"))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.code").value("CLUB_NOT_FOUND"));
        }
        mvc.perform(get("/api/public/clubs/not-a-number").header("Accept-Language", "en"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CATALOG_FILTER"));
    }

    @Test
    void storageFailureAcrossPortKeepsSafeLocalizedProblem() throws Exception {
        doThrow(new DataAccessResourceFailureException("private database detail"))
                .when(adapter).findActiveById(Long.MAX_VALUE);
        var body = mvc.perform(get("/api/public/clubs/" + Long.MAX_VALUE).header("Accept-Language", "en"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("PUBLIC_DATA_UNAVAILABLE"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("private database detail", "stackTrace");
    }

    private Club create(String name, boolean active) {
        var club = new TransactionTemplate(transactions).execute(status -> clubs.saveAndFlush(Club.builder()
                .nombre(name).isActive(active).telefonoContacto("private-contact")
                .emailContacto("private@example.test").build()));
        owned.add(club.getId());
        return club;
    }

    private void assertReadOnlyTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
        assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
    }
}
