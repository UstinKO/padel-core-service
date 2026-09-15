package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.model.Club;
import com.padle.core.padelcoreservice.model.enums.GenderFormat;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.Nivel;
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
 * LFPT-374: el nombre del torneo ya no se ingresa manualmente en el formulario — se genera
 * en el servidor a partir de generoFormato + categoriaNivel, tanto al crear como al editar.
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
class TournamentNombreGenerationTest {

    @Autowired
    private TournamentService tournamentService;
    @Autowired
    private ClubRepository clubRepository;

    @Test
    void createTournament_ignoraElNombreEnviadoYLoGeneraDeGeneroYNivel() {
        Long clubId = createClub();

        TournamentDto dto = buildDto(clubId, GenderFormat.MASCULINO, Nivel.C6);
        dto.setNombre("Nombre que el usuario ya no puede escribir");

        TournamentDto created = tournamentService.createTournament(dto, 1L);

        assertThat(created.getNombre()).isEqualTo("Masculino · C6");
    }

    @Test
    void updateTournament_regeneraElNombreSiCambiaLaCategoria() {
        Long clubId = createClub();
        TournamentDto created = tournamentService.createTournament(
                buildDto(clubId, GenderFormat.FEMENINO, Nivel.D7), 1L);
        assertThat(created.getNombre()).isEqualTo("Femenino · D7");

        TournamentDto updateDto = buildDto(clubId, GenderFormat.FEMENINO, Nivel.D5);
        TournamentDto updated = tournamentService
                .updateTournament(created.getId(), updateDto, 1L, true)
                .orElseThrow();

        assertThat(updated.getNombre()).isEqualTo("Femenino · D5");
    }

    private Long createClub() {
        String suffix = "LFPT374-" + UUID.randomUUID();
        return clubRepository.save(Club.builder().nombre("Club " + suffix).isActive(true).build()).getId();
    }

    private TournamentDto buildDto(Long clubId, GenderFormat generoFormato, Nivel nivel) {
        return TournamentDto.builder()
                .clubId(clubId)
                .fechaInicio(LocalDate.now().plusDays(7))
                .horaInicio(LocalTime.of(10, 0))
                .generoFormato(generoFormato)
                .categoriaNivel(nivel.name())
                .tipo(TournamentType.AMERICANO)
                .modalidad(Modalidad.INDIVIDUAL)
                .cupoMax(16)
                .precio(BigDecimal.TEN)
                .moneda("ARS")
                .contactoOrganizador("test@example.com")
                .build();
    }
}
