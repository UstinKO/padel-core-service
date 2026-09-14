package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.model.Club;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.enums.GenderFormat;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.repository.ClubRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * LFPT-373: regresión sobre el CHECK-constraint {@code tournaments_db_categoria_nivel_check} —
 * cada ampliación previa de {@link Nivel} (v1.43, v1.45) exigió una migración que actualizara
 * este constraint; sin ella, un valor nuevo del enum de Java es rechazado por Postgres al
 * persistir, aunque compile sin problema.
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
class NivelPersistenceTest {

    @Autowired
    private TournamentRepository tournamentRepository;
    @Autowired
    private ClubRepository clubRepository;

    @ParameterizedTest
    @EnumSource(value = Nivel.class, names = {
            // nuevas parejas adyacentes masculinas
            "C9_C8", "C8_C7", "C6_C5", "C5_C4",
            // nuevos individuales y pareja femeninos
            "D5", "D4", "D9_D8", "D7_D6", "D6_D5", "D5_D4",
            // nuevo rango mixto
            "SUMA_10", "SUMA_11", "SUMA_12",
            // valores legacy ya existentes — regresión de compatibilidad hacia atrás
            "D7_D8", "PRINCIPIANTES", "TODOS",
    })
    void nuevosValoresDeNivel_sePersistenSinViolarElCheckConstraint(Nivel nivel) {
        assertThatCode(() -> tournamentRepository.saveAndFlush(buildTournament(nivel)))
                .doesNotThrowAnyException();
    }

    private Tournament buildTournament(Nivel nivel) {
        String suffix = "LFPT373-" + UUID.randomUUID();
        Club club = clubRepository.save(Club.builder().nombre("Club " + suffix).isActive(true).build());
        return Tournament.builder()
                .clubId(club.getId())
                .nombre("Torneo " + suffix)
                .fechaInicio(LocalDate.now().plusDays(7))
                .horaInicio(LocalTime.of(10, 0))
                .generoFormato(GenderFormat.MIXTO)
                .categoriaNivel(nivel)
                .tipo(TournamentType.AMERICANO)
                .modalidad(Modalidad.INDIVIDUAL)
                .cupoMax(16)
                .precio(BigDecimal.ZERO)
                .estado(TournamentStatus.REGISTRO_ABIERTO)
                .contactoOrganizador("test@example.com")
                .isActive(true)
                .build();
    }
}
