package com.padle.core.padelcoreservice.receipts;

import com.padle.core.padelcoreservice.config.PlayerTelegramProperties;
import com.padle.core.padelcoreservice.exception.AccountException;
import com.padle.core.padelcoreservice.model.PlayerPadel;
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
            mock(TournamentRepository.class), access, new ReceiptProperties(true));
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
        var disabled = new ReceiptRegistrations(repository,mock(TournamentRepository.class),access,new ReceiptProperties(false));
        assertThat(disabled.choices(42)).isEmpty();
    }
    @Test void choicesExcludeCancelledInactiveAndDraftRowsButAllowCompletedEvents() {
        var good = row(); var cancelled = row(); cancelled.setStatus(RegistrationStatus.CANCELLED);
        var draft = row(); draft.getTournament().setEstado(TournamentStatus.BORRADOR);
        when(repository.findReceiptChoices(42)).thenReturn(List.of(good,cancelled,draft));
        assertThat(registrations.choices(42)).containsExactly(good);
    }
}
