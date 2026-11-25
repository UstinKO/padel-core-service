package com.padle.core.padelcoreservice.telegram;

import com.padle.core.padelcoreservice.exception.AccountException;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import com.padle.core.padelcoreservice.service.TelegramMessagePreviewService;
import com.padle.core.padelcoreservice.telegram.CampaignModels.*;
import com.padle.core.padelcoreservice.telegram.TelegramAudienceModels.*;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import io.micrometer.core.annotation.Timed;
import com.padle.core.padelcoreservice.mapper.TelegramAudienceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TelegramCampaignService {
    private final TelegramCampaignStore store;
    private final TelegramMessagePreviewService preview;
    private final CampaignAudienceGuard guard;
    private final PlayerTelegramStore connections;
    private final PlayerRepository players;
    private final TelegramAudienceMapper mapper;

    @Transactional
    public Prepared prepare(Owner session, Prepare request) {
        var actor = guard.actor(session.getId());
        guard.scope(actor, request.selection().tournamentId());
        String hash = fingerprint(request.selection());
        var prior = store.byRequest(actor.getId(), request.requestId());
        if (prior.isPresent()) return prepared(checkFingerprint(prior.orElseThrow(),hash),actor);
        connections.guardConnections();
        var result = preview.preview(actor,request.selection());
        var creation = store.create(actor.getId(),actor.getFirstName()+" "+actor.getLastName(),request.selection().tournamentId(),
                request.requestId(),hash,request.selection().text(),"MESSAGE",null);
        var header = checkFingerprint(creation.header(),hash);
        if (creation.created()) Stream.concat(result.recipients().stream(),result.unavailable().stream())
                .forEach(candidate -> store.add(header.id(), candidate, store.connection(candidate.playerId())));
        return prepared(header,actor);
    }
    private Header checkFingerprint(Header header, String fingerprint) {
        if (!header.fingerprint().equals(fingerprint)) throw new AccountException(HttpStatus.CONFLICT,"TELEGRAM_CAMPAIGN_CHANGED");
        return header;
    }
    private String fingerprint(Selection selection) {
        return PlayerTelegramLinks.hash(Objects.toString(selection.tournamentId(),"global")+":"
                + sorted(selection.groups())+":"+sorted(selection.categories())+":"+sorted(selection.includedPlayerIds())+":"
                +sorted(selection.excludedPlayerIds())+":"+selection.text());
    }
    private String sorted(Set<?> values) { return values.stream().map(Object::toString).sorted().collect(Collectors.joining(",")); }

    private Prepared prepared(Header header, Owner actor) {
        var rows = store.rows(header.id());
        var available = rows.stream().filter(row -> row.state().equals("PENDING")).map(this::candidate).toList();
        var unavailable = rows.stream().filter(row -> !row.state().equals("PENDING")).map(this::candidate).toList();
        boolean ready = header.state().equals("DRAFT") && header.expiresAt().isAfter(OffsetDateTime.now())
                && guard.ready(header.tournamentId());
        return new Prepared(header.id(),header.text(),available,unavailable,rows.size(),ready,
                ready && guard.self(actor,header.tournamentId()).isPresent(),header.expiresAt());
    }
    private Candidate candidate(StoredRecipient row) {
        Set<Group> groups = row.groups().isBlank() ? Set.of() : Arrays.stream(row.groups().split(","))
                .map(Group::valueOf).collect(Collectors.toSet());
        Availability availability = row.state().equals("PENDING") ? Availability.AVAILABLE
                : Arrays.stream(Availability.values()).filter(value -> value.name().equals(row.reason())).findFirst().orElse(Availability.NOT_CONNECTED);
        return new Candidate(row.playerId(),row.name(),Nivel.valueOf(row.category()),groups,availability);
    }
    @Transactional
    public View confirm(Owner session, UUID id) {
        var actor = guard.actor(session.getId());
        var header = owned(actor,id);
        guard.requireEnabled(header.tournamentId());
        if (!header.state().equals("DRAFT")) return view(actor,header);
        if (header.expiresAt().isBefore(OffsetDateTime.now())) throw new AccountException(HttpStatus.CONFLICT,"TELEGRAM_CAMPAIGN_EXPIRED");
        connections.guardConnections();
        recheck(header);
        store.queue(id);
        return view(actor,required(id));
    }
    private void recheck(Header header) {
        store.rows(header.id()).stream().filter(row -> row.state().equals("PENDING") || row.state().equals("FAILED"))
                .forEach(row -> guard.reason(header,row).ifPresent(reason -> store.result(header.id(),row.playerId(),"SKIPPED",reason)));
    }
    @Transactional
    public View test(Owner session, UUID sourceId, Action action) {
        var actor = guard.actor(session.getId());
        var source = owned(actor,sourceId);
        if (!source.state().equals("DRAFT") || source.expiresAt().isBefore(OffsetDateTime.now())) {
            throw new AccountException(HttpStatus.CONFLICT,"TELEGRAM_CAMPAIGN_EXPIRED");
        }
        guard.requireEnabled(source.tournamentId());
        connections.guardConnections();
        var self = guard.self(actor,source.tournamentId()).orElseThrow(() -> new AccountException(HttpStatus.CONFLICT,"TELEGRAM_TEST_UNAVAILABLE"));
        var creation = store.create(actor.getId(),source.author(),source.tournamentId(),action.requestId(),
                PlayerTelegramLinks.hash("test:"+sourceId),source.text(),"TEST",sourceId);
        var header = checkFingerprint(creation.header(),PlayerTelegramLinks.hash("test:"+sourceId));
        if (creation.created()) {
            var player = players.findById(self.playerId()).orElseThrow();
            store.add(header.id(),mapper.candidate(player,Set.of(),Availability.AVAILABLE),Optional.of(self));
            store.queue(header.id());
        }
        return view(actor,required(header.id()));
    }
    @Transactional
    public View retry(Owner session, UUID id, Action action) {
        var actor = guard.actor(session.getId());
        var header = owned(actor,id);
        guard.requireEnabled(header.tournamentId());
        if (!store.action(id,action.requestId())) return view(actor,header);
        if (!header.state().equals("FINISHED")) throw new AccountException(HttpStatus.CONFLICT,"TELEGRAM_CAMPAIGN_CHANGED");
        connections.guardConnections(); recheck(header); store.retry(id); store.finishCampaigns();
        return view(actor,required(id));
    }
    public View get(Owner session, UUID id) {
        var actor = guard.actor(session.getId()); var header = required(id);
        if (!actor.isSuperAdmin()) guard.scope(actor,header.tournamentId());
        return view(actor,header);
    }
    @Timed("telegram.campaign.history")
    public List<View> history(Owner session, Long event, int page) {
        var actor = guard.actor(session.getId()); guard.scope(actor,event);
        return store.history(event,page).stream().map(header -> view(actor,header)).toList();
    }
    private Header owned(Owner actor, UUID id) {
        var header = store.lock(id);
        if (header.ownerId()!=actor.getId()) throw new AccessDeniedException("Нельзя подтвердить чужой черновик");
        guard.scope(actor,header.tournamentId());
        return header;
    }
    private Header required(UUID id) {
        return store.find(id).orElseThrow(() -> new AccountException(HttpStatus.NOT_FOUND,"TELEGRAM_CAMPAIGN_NOT_FOUND"));
    }
    private View view(Owner actor, Header header) {
        boolean own = actor.getId()==header.ownerId();
        var rows = store.rows(header.id());
        var recipients = rows.stream().map(row -> new Recipient(row.playerId(),row.name(),row.state(),row.reason(),row.attempt())).toList();
        return new View(header.id(),header.tournamentId(),header.author(),header.text(),header.kind(),header.state(),header.createdAt(),recipients,
                own && guard.ready(header.tournamentId()) && header.state().equals("DRAFT") && header.expiresAt().isAfter(OffsetDateTime.now())
                        && rows.stream().anyMatch(row -> row.state().equals("PENDING")),
                own && guard.ready(header.tournamentId()) && header.state().equals("FINISHED") && rows.stream().anyMatch(row -> row.state().equals("FAILED")));
    }
}
