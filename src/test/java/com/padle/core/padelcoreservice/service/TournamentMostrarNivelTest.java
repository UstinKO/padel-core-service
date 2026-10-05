package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.model.Club;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.enums.GenderFormat;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import com.padle.core.padelcoreservice.model.enums.OwnerRole;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.repository.ClubRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LFPT-483: Tournament.mostrarNivel — флаг опционального отображения уровня участников,
 * по умолчанию выключен, сохраняется независимо от TournamentType.
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
class TournamentMostrarNivelTest {

    @Autowired
    private TournamentService tournamentService;
    @Autowired
    private ClubRepository clubRepository;

    @Test
    void createTournament_sinMostrarNivelEnDto_persisteFalse() {
        Long clubId = createClub();
        TournamentDto dto = buildDto(clubId, TournamentType.AMERICANO);
        dto.setMostrarNivel(null);

        TournamentDto created = tournamentService.createTournament(dto, 1L);

        assertThat(created.getMostrarNivel()).isFalse();
    }

    @Test
    void createTournament_conMostrarNivelTrue_persisteTrue() {
        Long clubId = createClub();
        TournamentDto dto = buildDto(clubId, TournamentType.KING_OF_COURT);
        dto.setMostrarNivel(true);

        TournamentDto created = tournamentService.createTournament(dto, 1L);

        assertThat(created.getMostrarNivel()).isTrue();
    }

    @Test
    void updateTournament_cambiaMostrarNivelDeFalseATrueYVuelta() {
        Long clubId = createClub();
        TournamentDto created = tournamentService.createTournament(
                buildDto(clubId, TournamentType.AMERICANO_TEAMS), 1L);
        assertThat(created.getMostrarNivel()).isFalse();

        Owner superAdmin = Owner.builder().id(1L).role(OwnerRole.SUPER_ADMIN).build();

        TournamentDto enableDto = buildDto(clubId, TournamentType.AMERICANO_TEAMS);
        enableDto.setMostrarNivel(true);
        TournamentDto enabled = tournamentService
                .updateTournament(created.getId(), enableDto, superAdmin)
                .orElseThrow();
        assertThat(enabled.getMostrarNivel()).isTrue();

        TournamentDto disableDto = buildDto(clubId, TournamentType.AMERICANO_TEAMS);
        disableDto.setMostrarNivel(false);
        TournamentDto disabled = tournamentService
                .updateTournament(created.getId(), disableDto, superAdmin)
                .orElseThrow();
        assertThat(disabled.getMostrarNivel()).isFalse();
    }

    @Test
    void createTournament_tipoCanchaAbierta_tambienPersisteMostrarNivel() {
        Long clubId = createClub();
        TournamentDto dto = buildDto(clubId, TournamentType.CANCHA_ABIERTA);
        dto.setMostrarNivel(true);

        TournamentDto created = tournamentService.createTournament(dto, 1L);

        assertThat(created.getMostrarNivel()).isTrue();
    }

    private Long createClub() {
        String suffix = "LFPT483-" + UUID.randomUUID();
        return clubRepository.save(Club.builder().nombre("Club " + suffix).isActive(true).build()).getId();
    }

    private TournamentDto buildDto(Long clubId, TournamentType tipo) {
        return TournamentDto.builder()
                .clubId(clubId)
                .fechaInicio(LocalDate.now().plusDays(7))
                .horaInicio(LocalTime.of(10, 0))
                .generoFormato(GenderFormat.MASCULINO)
                .categoriaNivel(Nivel.C6.name())
                .tipo(tipo)
                .modalidad(Modalidad.INDIVIDUAL)
                .cupoMax(16)
                .precio(BigDecimal.TEN)
                .moneda("ARS")
                .contactoOrganizador("test@example.com")
                .build();
    }
}
