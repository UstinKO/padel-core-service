package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.dto.PaymentManagementViewDto;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.TournamentRegistration;
import com.padle.core.padelcoreservice.model.enums.RegistrationStatus;
import com.padle.core.padelcoreservice.repository.PaymentRepository;
import com.padle.core.padelcoreservice.repository.TournamentRegistrationRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * LFPT-0729: la página de pagos ("Oplaty i poseschaemost'") debe incluir también las parejas en
 * reserva (WAITLIST) — antes quedaban completamente excluidas, no solo sin marcar.
 */
class PaymentServiceReserveTest {

    private final TournamentRegistrationRepository registrationRepository = mock(TournamentRegistrationRepository.class);
    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);

    private final PaymentService paymentService = new PaymentService(
            paymentRepository, registrationRepository, null, null);

    @Test
    void getPaymentManagementData_incluyeParejaEnReservaConFlagIsReserve() {
        Tournament tournament = Tournament.builder().id(1L).build();
        PlayerPadel mainPlayer = PlayerPadel.builder().id(10L).nombre("Juan").apellido("Perez").build();
        PlayerPadel confirmedPlayer = PlayerPadel.builder().id(20L).nombre("Ana").apellido("Gomez").build();
        PlayerPadel reservePlayer = PlayerPadel.builder().id(30L).nombre("Luis").apellido("Diaz").build();

        TournamentRegistration confirmed = TournamentRegistration.builder()
                .id(100L).tournament(tournament).player(confirmedPlayer)
                .status(RegistrationStatus.CONFIRMED).position(1)
                .isDoubleRegistration(true).mainPlayerId(20L)
                .build();
        TournamentRegistration reserve = TournamentRegistration.builder()
                .id(200L).tournament(tournament).player(reservePlayer)
                .status(RegistrationStatus.WAITLIST).waitlistPosition(1)
                .isDoubleRegistration(true).mainPlayerId(30L)
                .build();
        TournamentRegistration cancelled = TournamentRegistration.builder()
                .id(300L).tournament(tournament).player(mainPlayer)
                .status(RegistrationStatus.CANCELLED)
                .isDoubleRegistration(true).mainPlayerId(10L)
                .build();

        when(registrationRepository.findByTournamentIdOrderByPositionAscWaitlistPositionAsc(1L))
                .thenReturn(List.of(confirmed, reserve, cancelled));
        when(paymentRepository.findByTournamentId(1L)).thenReturn(List.of());

        List<PaymentManagementViewDto> result = paymentService.getPaymentManagementData(1L);

        assertThat(result).hasSize(2); // CANCELLED sigue excluida — no forma parte de este fix
        PaymentManagementViewDto confirmedDto = result.stream()
                .filter(r -> r.getPlayerId().equals(20L)).findFirst().orElseThrow();
        PaymentManagementViewDto reserveDto = result.stream()
                .filter(r -> r.getPlayerId().equals(30L)).findFirst().orElseThrow();

        assertThat(confirmedDto.isReserve()).isFalse();
        assertThat(reserveDto.isReserve()).isTrue();
        assertThat(reserveDto.getWaitlistPosition()).isEqualTo(1);
    }
}
