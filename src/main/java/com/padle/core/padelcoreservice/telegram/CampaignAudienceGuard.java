package com.padle.core.padelcoreservice.telegram;

import com.padle.core.application.identity.OwnerAccount;
import com.padle.core.application.identity.OwnerAccounts;
import com.padle.core.padelcoreservice.config.PlayerTelegramProperties;
import com.padle.core.padelcoreservice.config.TelegramCampaignProperties;
import com.padle.core.padelcoreservice.exception.AccountException;
import com.padle.core.padelcoreservice.exception.ResourceNotFoundException;
import com.padle.core.padelcoreservice.mapper.OwnerPrincipalMapper;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.enums.RegistrationStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.repository.*;
import com.padle.core.padelcoreservice.service.TournamentAccessService;
import com.padle.core.padelcoreservice.telegram.CampaignModels.*;
import com.padle.core.padelcoreservice.telegram.TelegramModels.ContactPreference;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CampaignAudienceGuard {
    private final OwnerAccounts owners;
    private final OwnerPrincipalMapper principals;
    private final PlayerRepository players;
    private final TournamentRepository tournaments;
    private final TournamentRegistrationRepository registrations;
    private final TournamentAccessService access;
    private final PlayerTelegramAccess pilot;
    private final PlayerTelegramStore connections;
    private final TelegramCampaignStore store;
    private final PlayerTelegramProperties properties;
    private final TelegramCampaignProperties campaigns;
    private final PlayerTelegramApi api;

    public Owner actor(long id) {
        return activeOwner(id)
                .orElseThrow(() -> new AccessDeniedException("Отправитель недоступен"));
    }
    public void scope(Owner actor, Long event) {
        if (event == null) {
            if (!actor.isSuperAdmin()) throw new AccessDeniedException("Общая рассылка недоступна");
        } else {
            access.assertCanManageTournament(actor, event);
        }
    }
    public boolean enabled() { return campaigns.enabled() && api.available(); }
    public boolean ready(Long event) { return enabled() && (!pilot.pilot() || event!=null && pilot.allowsEvent(event)); }
    public void requireEnabled(Long event) {
        if (!ready(event)) throw new AccountException(HttpStatus.CONFLICT, "TELEGRAM_CAMPAIGNS_DISABLED");
    }
    public Optional<Link> self(Owner actor, Long event) {
        if (pilot.pilot() && (event==null || !pilot.allowsEvent(event))) return Optional.empty();
        return Optional.ofNullable(properties.organizerChatId()).filter(chat -> chat > 0)
                .filter(chat -> actor.isSuperAdmin() && Objects.equals(actor.getId(), properties.organizerOwnerId()))
                .flatMap(connections::byUser).filter(connection -> pilot.allowsPlayer(connection.playerId()))
                .filter(connection -> event == null || pilot.allowsEvent(event))
                .flatMap(connection -> players.findById(connection.playerId()))
                .filter(PlayerPadel::isActivo).filter(player -> !player.isGuestAccount())
                .filter(player -> connections.preference(player.getId()) != ContactPreference.WHATSAPP)
                .flatMap(player -> store.connection(player.getId()));
    }
    public Optional<String> reason(Header campaign, StoredRecipient recipient) {
        if (campaign.texts().localized() && recipient.text().isBlank()) {
            return Optional.of("TRANSLATION_MISSING");
        }
        var actor = activeOwner(campaign.ownerId());
        if (actor.isEmpty()) return Optional.of("ACCESS_CHANGED");
        try { scope(actor.orElseThrow(), campaign.tournamentId()); }
        catch (AccessDeniedException | ResourceNotFoundException exception) {
            return Optional.of("ACCESS_CHANGED");
        }
        if (campaign.tournamentId() != null && tournaments.findById(campaign.tournamentId())
                .filter(event -> Boolean.TRUE.equals(event.getIsActive()) && event.getEstado() != TournamentStatus.CANCELADO
                        && !Boolean.TRUE.equals(event.getTestTournament())).isEmpty()) return Optional.of("EVENT_UNAVAILABLE");
        if (!pilot.allowsPlayer(recipient.playerId()) || pilot.pilot() && campaign.tournamentId()==null
                || campaign.tournamentId() != null && !pilot.allowsEvent(campaign.tournamentId())) {
            return Optional.of("OUTSIDE_PILOT");
        }
        var player = players.findById(recipient.playerId()).filter(PlayerPadel::isActivo).filter(p -> !p.isGuestAccount());
        if (player.isEmpty()) return Optional.of("INACTIVE");
        if (campaign.tournamentId()!=null && (!actor.orElseThrow().isSuperAdmin() || !recipient.groups().isBlank())
                && !participant(campaign.tournamentId(),recipient.playerId())) return Optional.of("REGISTRATION_CHANGED");
        if (connections.preference(recipient.playerId()) == ContactPreference.WHATSAPP) return Optional.of("WHATSAPP");
        var link = store.connection(recipient.playerId());
        if (link.isEmpty()) return Optional.of("NOT_CONNECTED");
        var actual = link.orElseThrow();
        if (!Objects.equals(recipient.chatId(),actual.chatId()) || !Objects.equals(recipient.connectedAt(),actual.connectedAt())) {
            return Optional.of("CONNECTION_CHANGED");
        }
        if (campaign.kind().equals("TEST") && self(actor.orElseThrow(),campaign.tournamentId()).filter(actual::equals).isEmpty()) {
            return Optional.of("TEST_UNAVAILABLE");
        }
        return Optional.empty();
    }
    private Optional<Owner> activeOwner(long id) {
        return owners.findById(id).filter(OwnerAccount::isActive).map(principals::toPrincipal);
    }

    private boolean participant(Long event, long player) {
        return registrations.findByTournamentId(event).stream().filter(row -> Boolean.TRUE.equals(row.getIsActive())
                && !row.isCancelled() && row.getStatus() != RegistrationStatus.PARTICIPATED)
                .anyMatch(row -> row.getPlayer().getId() == player || Optional.ofNullable(row.getPartner())
                        .filter(partner -> partner.getId() == player).isPresent());
    }
}
