package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.dto.ParticipationDtos.RequestSummary;
import com.padle.core.padelcoreservice.model.ParticipationRequest;
import com.padle.core.padelcoreservice.model.ParticipationRequest.Trigger;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.TournamentRegistration;
import com.padle.core.padelcoreservice.model.enums.ParticipationStatus;
import com.padle.core.padelcoreservice.repository.ParticipationRequestRepository;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import com.padle.core.padelcoreservice.util.AfterCommit;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ParticipationRequests {
    private final ParticipationRequestRepository requests;
    private final PlayerRepository players;
    private final EmailService email;

    @Value("${app.participation.response-email.enabled:false}")
    private boolean responseEmailEnabled;

    boolean sendsResponseEmail() {
        return responseEmailEnabled;
    }

    void open(TournamentRegistration row, Trigger trigger) {
        open(row, trigger, row.getPairBookedBy());
    }

    void open(TournamentRegistration row, Trigger trigger, Long bookedBy) {
        var previous = requests.findByRegistrationIdAndSupersededAtIsNull(row.getId());
        if (previous.filter(request -> sameCycle(request, row)).isPresent()) return;

        var now = OffsetDateTime.now();
        previous.ifPresent(request -> {
            request.supersede(now);
            requests.flush();
        });

        var guest = row.getPlayer().isGuestAccount();
        var recipient = guest
                ? Optional.ofNullable(bookedBy).flatMap(players::findById)
                : Optional.of(row.getPlayer());
        recipient = recipient.filter(PlayerPadel::isActivo).filter(player -> !player.isGuestAccount());
        Long delegateId = guest ? recipient.map(PlayerPadel::getId).orElse(null) : null;
        var request = requests.save(new ParticipationRequest(row, delegateId, trigger, now));
        if (!responseEmailEnabled) return;
        recipient.filter(player -> player.getEmail() != null && !player.getEmail().isBlank()).ifPresent(player -> {
            String to = player.getEmail();
            String participant = row.getPlayer().getNombreCompleto();
            String tournament = row.getTournament().getNombre();
            var locale = player.getLocale();
            var requestId = request.getId();
            AfterCommit.run(() -> email.sendParticipationRequestEmail(to, participant, tournament, requestId, guest, locale));
        });
    }

    void close(TournamentRegistration row) {
        requests.findByRegistrationIdAndSupersededAtIsNull(row.getId())
                .ifPresent(request -> request.supersede(OffsetDateTime.now()));
    }

    Map<Long, ParticipationRequest> current(Collection<TournamentRegistration> rows) {
        if (rows.isEmpty()) return Map.of();
        return requests.findByRegistrationIdInAndSupersededAtIsNull(
                        rows.stream().map(TournamentRegistration::getId).toList())
                .stream().filter(request -> sameCycle(request, request.getRegistration()))
                .collect(Collectors.toMap(request -> request.getRegistration().getId(), Function.identity()));
    }

    static boolean sameCycle(ParticipationRequest request, TournamentRegistration row) {
        return request.getSupersededAt() == null
                && (request.getRegistrationVersion() == row.getCancellationVersion()
                || (request.getStatus() == ParticipationStatus.DECLINED && row.isCancelled()
                    && request.getRegistrationVersion() + 1 == row.getCancellationVersion()));
    }

    static boolean canActFor(ParticipationRequest request, long actorId) {
        PlayerPadel participant = request.getRegistration().getPlayer();
        return !participant.isGuestAccount() && participant.getId() == actorId
                || participant.isGuestAccount() && Objects.equals(request.getDelegatePlayerId(), actorId);
    }

    static RequestSummary summary(ParticipationRequest request, long actorId) {
        var row = request.getRegistration();
        return new RequestSummary(request.getId(), row.getId(), row.getPlayer().getId(),
                row.getPlayer().getNombreCompleto(), request.getStatus(), !row.getPlayer().getId().equals(actorId));
    }
}
