package com.padle.core.padelcoreservice.repository;

import com.padle.core.persistence.clubs.Club;
import com.padle.core.persistence.clubs.ClubRepository;
import com.padle.core.persistence.identity.OwnerEntity;
import com.padle.core.persistence.identity.OwnerRepository;
import com.padle.core.application.identity.OwnerAccount.Role;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Модель данных клубных аккаунтов: OwnerEntity.clubId (nullable-колонка без FK, тот же паттерн,
 * что Tournament.clubId) и значение роли CLUB_ADMIN.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.mail.username=test@example.com",
        "spring.mail.password=test-password",
        "spring.security.oauth2.client.registration.google.client-id=test-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-client-secret",
        "recaptcha.site-key=test-site-key",
        "recaptcha.secret-key=test-secret-key",
})
@Transactional
class OwnerClubAccountTest {

    @Autowired
    private OwnerRepository ownerRepository;
    @Autowired
    private ClubRepository clubRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    void ownerExistente_sinClubId_sePersisteConClubIdNull() {
        OwnerEntity owner = ownerRepository.saveAndFlush(buildOwner(Role.ORGANIZER, null));
        entityManager.clear();

        OwnerEntity reloaded = ownerRepository.findById(owner.getId()).orElseThrow();
        assertThat(reloaded.getClubId()).isNull();
        assertThat(reloaded.getRole()).isEqualTo(Role.ORGANIZER);
    }

    @Test
    void ownerClubAdmin_conClubId_sePersisteYSeLee() {
        Club club = clubRepository.save(Club.builder().nombre("Club " + UUID.randomUUID()).isActive(true).build());

        OwnerEntity owner = ownerRepository.saveAndFlush(buildOwner(Role.CLUB_ADMIN, club.getId()));
        entityManager.clear();

        OwnerEntity reloaded = ownerRepository.findById(owner.getId()).orElseThrow();
        assertThat(reloaded.getRole()).isEqualTo(Role.CLUB_ADMIN);
        assertThat(reloaded.getClubId()).isEqualTo(club.getId());
    }

    @Test
    void findByClubId_devuelveSoloLosOwnersDeEseClub() {
        Club clubA = clubRepository.save(Club.builder().nombre("Club A " + UUID.randomUUID()).isActive(true).build());
        Club clubB = clubRepository.save(Club.builder().nombre("Club B " + UUID.randomUUID()).isActive(true).build());

        OwnerEntity ownerA = ownerRepository.saveAndFlush(buildOwner(Role.CLUB_ADMIN, clubA.getId()));
        ownerRepository.saveAndFlush(buildOwner(Role.CLUB_ADMIN, clubB.getId()));
        ownerRepository.saveAndFlush(buildOwner(Role.ORGANIZER, null));

        assertThat(ownerRepository.findByClubId(clubA.getId()))
                .extracting(OwnerEntity::getId)
                .containsExactly(ownerA.getId());
    }

    private OwnerEntity buildOwner(Role role, Long clubId) {
        String suffix = "LFPT375-" + UUID.randomUUID();
        return OwnerEntity.builder()
                .email(suffix + "@example.com")
                .password("irrelevant-hash")
                .firstName("Test")
                .lastName("Owner")
                .role(role)
                .clubId(clubId)
                .isActive(true)
                .build();
    }
}
