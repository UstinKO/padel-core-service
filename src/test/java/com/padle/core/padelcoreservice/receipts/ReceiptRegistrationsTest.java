package com.padle.core.padelcoreservice.receipts;

import com.padle.core.padelcoreservice.config.PlayerTelegramProperties;
import com.padle.core.padelcoreservice.exception.AccountException;
import com.padle.core.persistence.identity.PlayerPadel;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.TournamentRegistration;
import com.padle.core.padelcoreservice.model.enums.RegistrationStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.repository.TournamentRegistrationRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import com.padle.core.padelcoreservice.telegram.PlayerTelegramAccess;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReceiptRegistrationsTest {
    private final TournamentRegistrationRepository repository = mock(TournamentRegistrationRepository.class);
    private final PlayerTelegramAccess access = new PlayerTelegramAccess(new PlayerTelegramProperties(
            true,"FixtureBot",false,24,3,null,null,false,"fixture-token",true,42L,10L));
    private final ReceiptRegistrations registrations = new ReceiptRegistrations(repository,
            mock(TournamentRepository.class), access, new ReceiptProperties(true, true, 42L, 10L));
    private TournamentRegistration row() {
        return TournamentRegistration.builder().id(100L).cancellationVersion(2L)
                .tournament(Tournament.builder().id(10L).isActive(true).estado(TournamentStatus.FINALIZADO).build())
                .player(PlayerPadel.builder().id(42L).email("fixture@example.test").activo(true).build())
                .isActive(true).status(RegistrationStatus.CONFIRMED).build();
    }
    @Test void rejectsForeignPlayersEventsAndOldCancellationCycles() {
        var row = row();
        when(repository.findById(100L)).thenReturn(Optional.of(row));
        assertThat(registrations.require(100,42,2)).isSameAs(row);
        assertThatThrownBy(() -> registrations.require(100,43,2)).isInstanceOf(AccountException.class);
        assertThatThrownBy(() -> registrations.require(100,42,1)).isInstanceOf(AccountException.class);
        row.getTournament().setId(11L);
        assertThatThrownBy(() -> registrations.require(100,42,2)).isInstanceOf(AccountException.class);
    }
    @Test void thePilotAndReceiptFlagStayClosedByDefault() {
        assertThat(registrations.choices(43)).isEmpty();
        verifyNoInteractions(repository);
        var disabled = new ReceiptRegistrations(repository,mock(TournamentRepository.class),access,
                new ReceiptProperties(false, true, 42L, 10L));
        assertThat(disabled.choices(42)).isEmpty();
    }
    @Test void choicesExcludeCancelledInactiveAndDraftRowsButAllowCompletedEvents() {
        var good = row(); var cancelled = row(); cancelled.setStatus(RegistrationStatus.CANCELLED);
        var draft = row(); draft.getTournament().setEstado(TournamentStatus.BORRADOR);
        when(repository.findReceiptChoices(42)).thenReturn(List.of(good,cancelled,draft));
        assertThat(registrations.choices(42)).containsExactly(good);
    }

    private ReceiptRegistrations general(ReceiptProperties properties, TournamentRepository tournaments) {
        var generalAccess = new PlayerTelegramAccess(new PlayerTelegramProperties(
                true, "FixtureBot", false, 24, 3, null, null, false, "fixture-token", false, null, null));
        return new ReceiptRegistrations(repository, tournaments, generalAccess, properties);
    }

    @Test void generalTelegramDoesNotOpenOtherPlayersOrEventsToReceipts() {
        var tournaments = mock(TournamentRepository.class);
        var scope = general(new ReceiptProperties(true, true, 42L, 10L), tournaments);
        var allowed = row();
        var otherEvent = row(); otherEvent.setId(101L); otherEvent.getTournament().setId(11L);
        when(repository.findReceiptChoices(42)).thenReturn(List.of(allowed, otherEvent));
        when(repository.findById(100L)).thenReturn(Optional.of(allowed));
        when(repository.findById(101L)).thenReturn(Optional.of(otherEvent));
        assertThat(scope.choices(42)).containsExactly(allowed);
        assertThat(scope.choices(43)).isEmpty();
        verify(repository, never()).findReceiptChoices(43);
        assertThat(scope.require(100, 42, 2)).isSameAs(allowed);
        assertThatThrownBy(() -> scope.require(100, 43, 2)).isInstanceOf(AccountException.class);
        assertThatThrownBy(() -> scope.require(101, 42, 2)).isInstanceOf(AccountException.class);
        when(tournaments.lockById(10L)).thenReturn(Optional.of(allowed.getTournament()));
        when(repository.lockById(100L)).thenReturn(Optional.of(allowed));
        assertThat(scope.lock(10, 100, 42, 2)).isSameAs(allowed);
        allowed.getTournament().setId(11L);
        assertThatThrownBy(() -> scope.lock(10, 100, 42, 2)).isInstanceOf(AccountException.class);
    }

    @Test void missingOrInvalidReceiptPilotIdsNeverOpenTheScope() {
        when(repository.findReceiptChoices(42)).thenReturn(List.of(row()));
        for (var properties : List.of(new ReceiptProperties(true, true, null, null),
                new ReceiptProperties(true, true, 42L, null), new ReceiptProperties(true, true, null, 10L),
                new ReceiptProperties(true, true, 0L, 10L), new ReceiptProperties(true, true, 42L, -1L))) {
            var scope = general(properties, mock(TournamentRepository.class));
            assertThat(scope.choices(42)).isEmpty();
        }
    }

    @Test void receiptPilotRequiresAnExplicitIndependentSwitchToOpenAllEvents() {
        var scope = general(new ReceiptProperties(true, false, null, null), mock(TournamentRepository.class));
        var allowed = row(); allowed.getPlayer().setId(43L); allowed.getTournament().setId(11L);
        when(repository.findReceiptChoices(43)).thenReturn(List.of(allowed));
        assertThat(scope.choices(43)).containsExactly(allowed);
    }
}
