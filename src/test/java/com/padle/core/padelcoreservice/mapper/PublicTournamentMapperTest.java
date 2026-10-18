package com.padle.core.padelcoreservice.mapper;

import com.padle.core.padelcoreservice.dto.PublicTournamentDto;
import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LFPT-0623: контракт публичного DTO для мобильного приложения — часовой пояс,
 * единица вместимости, отсутствующий дедлайн не превращается в выдуманную дату.
 */
class PublicTournamentMapperTest {

    private final PublicTournamentMapper mapper = new PublicTournamentMapperImpl();

    @Test
    void toPublicDto_dobles_mapeaFechaArgentinaYCapacidadEnParejas() {
        TournamentDto source = fixture(Modalidad.DOBLES);
        source.setDeadlineCancelacion(LocalDateTime.of(2026, 10, 23, 15, 0));

        PublicTournamentDto result = mapper.toPublicDto(source, "https://1-padel.com/");

        assertThat(result.startsAt().toString()).isEqualTo("2026-10-24T15:00-03:00");
        assertThat(result.timeZone()).isEqualTo("America/Argentina/Buenos_Aires");
        assertThat(result.cancellationDeadline().toString()).isEqualTo("2026-10-23T15:00-03:00");
        assertThat(result.capacityUnit()).isEqualTo(PublicTournamentDto.CapacityUnit.PAIRS);
        assertThat(result.occupied()).isEqualTo(6);
        assertThat(result.available()).isEqualTo(2);
        assertThat(result.registrationOpen()).isTrue();
        assertThat(result.websiteUrl()).isEqualTo("https://1-padel.com/torneo/42");
        assertThat(result.club().name()).isEqualTo("Club de prueba");
    }

    @Test
    void toPublicDto_individual_capacidadEnJugadoresYSinDeadlineEsNull() {
        TournamentDto source = fixture(Modalidad.INDIVIDUAL);
        source.setEstado(TournamentStatus.PUBLICADO);

        PublicTournamentDto result = mapper.toPublicDto(source, "https://1-padel.com");

        assertThat(result.capacityUnit()).isEqualTo(PublicTournamentDto.CapacityUnit.PLAYERS);
        assertThat(result.registrationOpen()).isFalse();
        assertThat(result.cancellationDeadline()).isNull();
        assertThat(result.websiteUrl()).isEqualTo("https://1-padel.com/torneo/42");
    }

    @Test
    void toPublicDto_sinNegativosNiNull_enContadores() {
        TournamentDto source = fixture(Modalidad.INDIVIDUAL);
        source.setInscritosActuales(null);
        source.setWaitlistCount(null);

        PublicTournamentDto result = mapper.toPublicDto(source, "https://1-padel.com");

        assertThat(result.occupied()).isZero();
        assertThat(result.waitlistCount()).isZero();
    }

    private TournamentDto fixture(Modalidad modality) {
        return TournamentDto.builder().id(42L).nombre("Evento de prueba")
                .clubId(1L).clubNombre("Club de prueba").clubDireccion("Dirección de prueba")
                .fechaInicio(LocalDate.of(2026, 10, 24)).horaInicio(LocalTime.of(15, 0))
                .modalidad(modality).cupoMax(8).inscritosActuales(6).disponibles(2).waitlistCount(0)
                .precio(new BigDecimal("40000")).moneda("ARS").duracion("3 horas")
                .estado(TournamentStatus.REGISTRO_ABIERTO)
                .ownerId(900L).createdBy(900L).build();
    }
}
