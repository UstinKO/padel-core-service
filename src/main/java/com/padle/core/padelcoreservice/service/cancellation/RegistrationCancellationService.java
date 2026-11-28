package com.padle.core.padelcoreservice.service.cancellation;

import com.padle.core.padelcoreservice.annotation.Timed;
import com.padle.core.application.registration.CancellationHistory.Entry;
import com.padle.core.application.registration.CancellationHistory.Result;
import com.padle.core.application.registration.CancellationHistory.Scope;
import com.padle.core.padelcoreservice.exception.TournamentRegistrationException;
import com.padle.core.padelcoreservice.mapper.CancellationHistoryMapper;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.persistence.identity.PlayerPadel;
import com.padle.core.persistence.tournament.TournamentRegistration;
import com.padle.core.application.registration.RegistrationCancellationHistory;
import com.padle.core.application.registration.RegistrationCancellationHistory.Event;
import com.padle.core.application.registration.RegistrationCancellationHistory.Member;
import com.padle.core.application.registration.RegistrationCancellationHistory.ReceiptKey;
import com.padle.core.padelcoreservice.service.PairTermsService.Cancellation;
import com.padle.core.padelcoreservice.service.TournamentAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RegistrationCancellationService {
    private final RegistrationCancellationHistory history;
    private final CancellationHistoryMapper mapper;
    private final TournamentAccessService access;
    private final ObjectMapper json;
    private final MessageSource messages;

    @Transactional(propagation = Propagation.MANDATORY)
    public Result cancel(List<TournamentRegistration> source, CancellationContext context, String legacyReason) {
        var rows = source.stream().filter(row -> Boolean.TRUE.equals(row.getIsActive())).toList();
        if (rows.isEmpty()) return new Result(false, null, null);
        long tournamentId = rows.get(0).getTournament().getId();
        if (rows.stream().anyMatch(row -> row.getTournament().getId() != tournamentId)) {
            throw new IllegalArgumentException("Отмена должна относиться к одному турниру");
        }
        var id = UUID.randomUUID();
        var at = LocalDateTime.now();
        history.insert(new Event(id, tournamentId, at, context.initiator(), context.actorId(),
                context.scope(), context.reason(), context.channel(), context.organizerComment()));
        rows.forEach(row -> {
            long version = row.getCancellationVersion() + 1;
            PlayerPadel partner = row.getPartner();
            Long partnerId = partner == null ? null : partner.getId();
            history.member(id, new Member(row.getId(), version, row.getPlayer().getId(), row.getStatus(),
                    row.getRegistrationDate(), row.getPairBookingId(), partnerId));
            row.setCancellationVersion(version);
            row.cancel(legacyReason);
            row.setCancellationDate(at);
        });
        return new Result(true, id, at);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void saveReceipt(UUID event, TournamentRegistration row, long actor, String token, Cancellation quote) {
        history.saveReceipt(event, receiptKey(row, actor, token), json.writeValueAsString(mapper.receipt(quote, "")));
    }

    public Optional<Cancellation> replay(TournamentRegistration row, long actor, String token) {
        if (Boolean.TRUE.equals(row.getIsActive())) return Optional.empty();
        return history.receipt(receiptKey(row, actor, token))
                .map(body -> mapper.receipt(json.readValue(body, Cancellation.class), token));
    }

    @Timed(name = "registration.cancellation.history.time", tags = {"operation=player"})
    public List<Entry> forPlayer(PlayerPadel player, Collection<Long> eventIds) {
        if (player == null || player.getId() == null) {
            throw new AccessDeniedException("CANCELLATION_HISTORY_FORBIDDEN");
        }
        return history.forPlayer(player.getId(), eventIds);
    }

    @Timed(name = "registration.cancellation.history.time", tags = {"operation=manager"})
    public List<Entry> forManager(Owner owner, long tournamentId) {
        if (owner == null) throw new AccessDeniedException("CANCELLATION_HISTORY_FORBIDDEN");
        access.assertCanManageTournament(owner, tournamentId);
        return history.forManager(tournamentId);
    }

    public CancellationContext organizer(Owner owner, Scope scope, String comment) {
        try {
            return CancellationContext.organizer(owner, scope, comment);
        } catch (IllegalArgumentException exception) {
            throw new TournamentRegistrationException(messages.getMessage(
                    "registration.cancellation.comment.invalid", null, LocaleContextHolder.getLocale()));
        }
    }

    private ReceiptKey receiptKey(TournamentRegistration row, long actor, String token) {
        return new ReceiptKey(row.getId(), row.getCancellationVersion(), actor, digest(token));
    }

    private String digest(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 недоступен", exception);
        }
    }
}
