package com.padle.core.padelcoreservice.receipts;

import com.padle.core.padelcoreservice.exception.AccountException;
import com.padle.core.padelcoreservice.model.TournamentRegistration;
import com.padle.core.padelcoreservice.model.enums.RegistrationStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.repository.TournamentRegistrationRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import com.padle.core.padelcoreservice.telegram.PlayerTelegramAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReceiptRegistrations {
    private final TournamentRegistrationRepository registrations;
    private final TournamentRepository tournaments;
    private final PlayerTelegramAccess access;
    private final ReceiptProperties properties;

    public boolean enabled() { return properties.enabled(); }

    public List<TournamentRegistration> choices(long playerId) {
        if (!allowsPlayer(playerId)) {
            return List.of();
        }
        return registrations.findReceiptChoices(playerId).stream().filter(this::eligible).toList();
    }

    public TournamentRegistration require(long id, long playerId, long version) {
        return registrations.findById(id).filter(this::eligible)
                .filter(row -> row.getPlayer().getId() == playerId)
                .filter(row -> Objects.equals(row.getCancellationVersion(), version))
                .orElseThrow(ReceiptRegistrations::unavailable);
    }

    @Transactional
    public TournamentRegistration lock(long tournamentId, long id, long playerId, long version) {
        tournaments.lockById(tournamentId).orElseThrow(ReceiptRegistrations::unavailable);
        return registrations.lockById(id).filter(this::eligible)
                .filter(row -> row.getTournament().getId() == tournamentId && row.getPlayer().getId() == playerId)
                .filter(row -> Objects.equals(row.getCancellationVersion(), version))
                .orElseThrow(ReceiptRegistrations::unavailable);
    }

    private boolean eligible(TournamentRegistration row) {
        var tournament = row.getTournament();
        return allows(row.getPlayer().getId(), tournament.getId())
                && row.getPlayer().isActivo() && !row.getPlayer().isGuestAccount()
                && Boolean.TRUE.equals(row.getIsActive())
                && (row.getStatus() == RegistrationStatus.CONFIRMED || row.getStatus() == RegistrationStatus.PAIR_REGISTERED)
                && Boolean.TRUE.equals(tournament.getIsActive())
                && tournament.getEstado() != TournamentStatus.BORRADOR && tournament.getEstado() != TournamentStatus.CANCELADO;
    }

    public boolean allows(long playerId, Long tournamentId) {
        return allowsPlayer(playerId) && access.allowsEvent(tournamentId)
                && (!properties.pilotEnabled() || Objects.equals(properties.pilotTournamentId(), tournamentId));
    }

    private boolean allowsPlayer(long playerId) {
        return enabled() && access.allowsPlayer(playerId)
                && (!properties.pilotEnabled() || Objects.equals(properties.pilotPlayerId(), playerId));
    }

    public static AccountException unavailable() {
        return new AccountException(HttpStatus.CONFLICT, "RECEIPT_UNAVAILABLE");
    }
}
