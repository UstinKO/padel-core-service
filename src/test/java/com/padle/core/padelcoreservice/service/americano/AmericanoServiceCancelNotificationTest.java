package com.padle.core.padelcoreservice.service.americano;

import com.padle.core.application.clubs.ClubDetails;
import com.padle.core.application.clubs.InternalClubReadPort;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.persistence.identity.PlayerPadel;
import com.padle.core.persistence.tournament.Tournament;
import com.padle.core.persistence.tournament.TournamentRegistration;
import com.padle.core.application.tournament.Modalidad;
import com.padle.core.padelcoreservice.model.enums.OwnerRole;
import com.padle.core.application.registration.RegistrationStatus;
import com.padle.core.application.tournament.TournamentStatus;
import com.padle.core.application.tournament.TournamentType;
import com.padle.core.persistence.tournament.TournamentRegistrationRepository;
import com.padle.core.persistence.tournament.TournamentRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoPlayerRepository;
import com.padle.core.padelcoreservice.service.EmailService;
import com.padle.core.padelcoreservice.service.TournamentAccessService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.padle.core.padelcoreservice.service.cancellation.RegistrationCancellationService;
import com.padle.core.application.registration.RegistrationCancellationHistory;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    @Mock private InternalClubReadPort clubReads;
    @Mock private EmailService emailService;
    @Mock private TournamentAccessService tournamentAccessService;

    private AmericanoService service;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void cancelacionPorAdmin_conMotivo_enviaEmailConElMotivo(boolean clubExists) {
        service = new AmericanoService(
                new RegistrationCancellationService(mock(RegistrationCancellationHistory.class),null,null,null,null),
                tournamentRepository, registrationRepository, americanoPlayerRepository,
                null, null, clubReads, null, null, null, emailService, tournamentAccessService, null);

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
        when(tournamentRepository.lockById(1L)).thenReturn(Optional.of(tournament));
        when(registrationRepository.findByTournamentIdAndPlayerId(1L, 2L)).thenReturn(Optional.of(reg));
        lenient().when(americanoPlayerRepository.findByTournamentId(1L)).thenReturn(List.of());
        lenient().when(registrationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var inactiveClub = new ClubDetails(5L, "Club inactivo", null, null, false);
        when(clubReads.findById(5L)).thenReturn(clubExists ? Optional.of(inactiveClub) : Optional.empty());

        Owner admin = Owner.builder().id(1L).role(OwnerRole.SUPER_ADMIN).build();

        service.cancelRegistration(1L, 2L, "No cumple con el nivel del torneo", admin);

        assertThat(reg.getStatus()).isEqualTo(RegistrationStatus.CANCELLED);
        verify(emailService).sendRegistrationCancelledEmail(
                eq(player.getEmail()), eq(player.getNombre()), eq(tournament.getNombre()),
                any(), any(), eq(clubExists ? inactiveClub.name() : "Club Desconocido"),
                eq("No cumple con el nivel del torneo"), eq(player.getLocale()));
    }
}
