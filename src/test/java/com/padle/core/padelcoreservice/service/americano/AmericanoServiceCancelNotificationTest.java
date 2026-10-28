package com.padle.core.padelcoreservice.service.americano;

import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.TournamentRegistration;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.OwnerRole;
import com.padle.core.padelcoreservice.model.enums.RegistrationStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.repository.ClubRepository;
import com.padle.core.padelcoreservice.repository.TournamentRegistrationRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoPlayerRepository;
import com.padle.core.padelcoreservice.service.EmailService;
import com.padle.core.padelcoreservice.service.TournamentAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Отмена регистрации в Americano администратором/организатором (LFPT-0752) — письмо игроку
 * с причиной отмены, если она указана.
 */
@ExtendWith(MockitoExtension.class)
class AmericanoServiceCancelNotificationTest {

    @Mock private TournamentRepository tournamentRepository;
    @Mock private TournamentRegistrationRepository registrationRepository;
    @Mock private AmericanoPlayerRepository americanoPlayerRepository;
    @Mock private ClubRepository clubRepository;
    @Mock private EmailService emailService;
    @Mock private TournamentAccessService tournamentAccessService;

    private AmericanoService service;

    @Test
    void cancelacionPorAdmin_conMotivo_enviaEmailConElMotivo() {
        service = new AmericanoService(
                tournamentRepository, registrationRepository, americanoPlayerRepository,
                null, null, clubRepository, null, null, null, emailService, tournamentAccessService);

        LocalDateTime start = LocalDateTime.now().plusDays(2);
        Tournament tournament = Tournament.builder()
                .id(1L).nombre("Americano de prueba")
                .modalidad(Modalidad.INDIVIDUAL).tipo(TournamentType.AMERICANO)
                .estado(TournamentStatus.REGISTRO_ABIERTO)
                .cupoMax(16).clubId(5L)
                .fechaInicio(start.toLocalDate()).horaInicio(start.toLocalTime())
                .isActive(true).build();
        PlayerPadel player = PlayerPadel.builder().id(2L).email("jugador@example.com").nombre("Jugador").build();
        TournamentRegistration reg = TournamentRegistration.builder()
                .id(10L).tournament(tournament).player(player)
                .status(RegistrationStatus.WAITLIST).isActive(true).isDoubleRegistration(false).build();

        when(tournamentRepository.findById(1L)).thenReturn(Optional.of(tournament));
        when(registrationRepository.findByTournamentIdAndPlayerId(1L, 2L)).thenReturn(Optional.of(reg));
        lenient().when(americanoPlayerRepository.findByTournamentId(1L)).thenReturn(List.of());
        lenient().when(registrationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(clubRepository.findById(anyLong())).thenReturn(Optional.empty());

        Owner admin = Owner.builder().id(1L).role(OwnerRole.SUPER_ADMIN).build();

        service.cancelRegistration(1L, 2L, "No cumple con el nivel del torneo", admin);

        assertThat(reg.getStatus()).isEqualTo(RegistrationStatus.CANCELLED);
        verify(emailService).sendRegistrationCancelledEmail(
                eq(player.getEmail()), eq(player.getNombre()), eq(tournament.getNombre()),
                any(), any(), any(), eq("No cumple con el nivel del torneo"), eq(player.getLocale()));
    }
}
