package com.padle.core.padelcoreservice.repository;

import com.padle.core.padelcoreservice.model.Club;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.enums.OwnerRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LFPT-375: modelo de datos para cuentas de club — Owner.clubId (columna nullable, sin FK,
 * mismo patrón que Tournament.clubId) y el nuevo valor de rol CLUB_ADMIN.
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

    @Test
    void ownerExistente_sinClubId_sePersisteConClubIdNull() {
        Owner owner = ownerRepository.saveAndFlush(buildOwner(OwnerRole.ORGANIZER, null));

        Owner reloaded = ownerRepository.findById(owner.getId()).orElseThrow();
        assertThat(reloaded.getClubId()).isNull();
        assertThat(reloaded.getRole()).isEqualTo(OwnerRole.ORGANIZER);
    }

    @Test
    void ownerClubAdmin_conClubId_sePersisteYSeLee() {
        Club club = clubRepository.save(Club.builder().nombre("Club " + UUID.randomUUID()).isActive(true).build());

        Owner owner = ownerRepository.saveAndFlush(buildOwner(OwnerRole.CLUB_ADMIN, club.getId()));

        Owner reloaded = ownerRepository.findById(owner.getId()).orElseThrow();
        assertThat(reloaded.getRole()).isEqualTo(OwnerRole.CLUB_ADMIN);
        assertThat(reloaded.getClubId()).isEqualTo(club.getId());
    }

    @Test
    void findByClubId_devuelveSoloLosOwnersDeEseClub() {
        Club clubA = clubRepository.save(Club.builder().nombre("Club A " + UUID.randomUUID()).isActive(true).build());
        Club clubB = clubRepository.save(Club.builder().nombre("Club B " + UUID.randomUUID()).isActive(true).build());

        Owner ownerA = ownerRepository.saveAndFlush(buildOwner(OwnerRole.CLUB_ADMIN, clubA.getId()));
        ownerRepository.saveAndFlush(buildOwner(OwnerRole.CLUB_ADMIN, clubB.getId()));
        ownerRepository.saveAndFlush(buildOwner(OwnerRole.ORGANIZER, null));

        assertThat(ownerRepository.findByClubId(clubA.getId()))
                .extracting(Owner::getId)
                .containsExactly(ownerA.getId());
    }

    private Owner buildOwner(OwnerRole role, Long clubId) {
        String suffix = "LFPT375-" + UUID.randomUUID();
        return Owner.builder()
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
