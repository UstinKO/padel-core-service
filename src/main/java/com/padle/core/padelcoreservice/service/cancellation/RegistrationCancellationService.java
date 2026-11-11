package com.padle.core.padelcoreservice.service.cancellation;

import com.padle.core.padelcoreservice.annotation.Timed;
import com.padle.core.padelcoreservice.dto.CancellationHistory.Entry;
import com.padle.core.padelcoreservice.dto.CancellationHistory.Result;
import com.padle.core.padelcoreservice.dto.CancellationHistory.Scope;
import com.padle.core.padelcoreservice.exception.TournamentRegistrationException;
import com.padle.core.padelcoreservice.mapper.CancellationHistoryMapper;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.TournamentRegistration;
import com.padle.core.padelcoreservice.repository.CancellationHistoryRepository;
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
    private final CancellationHistoryRepository history;
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
        history.insert(id, at, context, rows.get(0));
        rows.forEach(row -> {
            history.member(id, row);
            row.setCancellationVersion(row.getCancellationVersion() + 1);
            row.cancel(legacyReason);
            row.setCancellationDate(at);
        });
        return new Result(true, id, at);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void saveReceipt(UUID event, TournamentRegistration row, long actor, String token, Cancellation quote) {
        history.receipt(event, row, actor, digest(token), json.writeValueAsString(mapper.receipt(quote, "")));
    }

    public Optional<Cancellation> replay(TournamentRegistration row, long actor, String token) {
        if (Boolean.TRUE.equals(row.getIsActive())) return Optional.empty();
        return history.receipt(row, actor, digest(token))
                .map(body -> mapper.receipt(json.readValue(body, Cancellation.class), token));
    }

    @Timed(name = "registration.cancellation.history.time", tags = {"operation=player"})
    public List<Entry> forPlayer(PlayerPadel player, Collection<Long> eventIds) {
        if (player == null || player.getId() == null) {
            throw new AccessDeniedException("CANCELLATION_HISTORY_FORBIDDEN");
        }
        return mapper.entries(history.forPlayer(player.getId(), eventIds));
    }

    @Timed(name = "registration.cancellation.history.time", tags = {"operation=manager"})
    public List<Entry> forManager(Owner owner, long tournamentId) {
        if (owner == null) throw new AccessDeniedException("CANCELLATION_HISTORY_FORBIDDEN");
        access.assertCanManageTournament(owner, tournamentId);
        return mapper.entries(history.forManager(tournamentId));
    }

    public CancellationContext organizer(Owner owner, Scope scope, String comment) {
        try {
            return CancellationContext.organizer(owner, scope, comment);
        } catch (IllegalArgumentException exception) {
            throw new TournamentRegistrationException(messages.getMessage(
                    "registration.cancellation.comment.invalid", null, LocaleContextHolder.getLocale()));
        }
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
