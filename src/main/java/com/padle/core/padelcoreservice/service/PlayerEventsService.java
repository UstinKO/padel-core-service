package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.dto.CancellationHistory;
import com.padle.core.padelcoreservice.dto.ParticipationDtos.Decision;
import com.padle.core.padelcoreservice.dto.ParticipationDtos.Review;
import com.padle.core.padelcoreservice.dto.PlayerEvents.*;
import com.padle.core.padelcoreservice.exception.AccountException;
import com.padle.core.padelcoreservice.mapper.PlayerEventsMapper;
import com.padle.core.padelcoreservice.model.Club;
import com.padle.core.padelcoreservice.model.Payment;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.TournamentRegistration;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.ParticipationStatus;
import com.padle.core.padelcoreservice.model.enums.RegistrationStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.repository.ClubRepository;
import com.padle.core.padelcoreservice.repository.PaymentRepository;
import com.padle.core.padelcoreservice.repository.TournamentRegistrationRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import com.padle.core.padelcoreservice.service.cancellation.RegistrationCancellationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlayerEventsService {
    private final TournamentRegistrationRepository registrations;
    private final TournamentRepository tournaments;
    private final ClubRepository clubs;
    private final PaymentRepository payments;
    private final RegistrationCancellationService cancellationHistory;
    private final TournamentParticipationService participation;
    private final TournamentService registrationService;
    private final PairTermsService pairTerms;
    private final PlayerEventsMapper mapper;

    public Overview overview(PlayerPadel actor) {
        var rows = registrations.findForPlayerEvents(actor.getId());
        if (rows.isEmpty()) return new Overview(List.of(), List.of());

        var clubNames = clubs.findAllById(rows.stream().map(row -> row.getTournament().getClubId())
                        .distinct().toList()).stream()
                .collect(Collectors.toMap(Club::getId, Club::getNombre));
        var paymentsByRow = payments.findActiveForRegistrations(rows.stream().map(TournamentRegistration::getId).toList())
                .stream().collect(Collectors.groupingBy(payment -> payment.getRegistration().getId()));
        var historyByEvent = cancellationHistory.forPlayer(actor, rows.stream()
                        .map(row -> row.getTournament().getId()).distinct().toList()).stream()
                .collect(Collectors.groupingBy(CancellationHistory.Entry::tournamentId));
        var reviewsByEvent = participation.currentReviews(actor).stream()
                .collect(Collectors.groupingBy(Review::tournamentId));

        List<Entry> upcoming = new ArrayList<>();
        List<Entry> history = new ArrayList<>();
        for (var row : rows) {
            var event = mapper.event(row.getTournament(), clubNames.get(row.getTournament().getClubId()));
            var pastCancellations = historyByEvent.getOrDefault(event.id(), List.of());
            var reviews = reviewsByEvent.getOrDefault(event.id(), List.of());
            var ownPayment = paymentsByRow.getOrDefault(row.getId(), List.of()).stream()
                    .filter(payment -> PaymentService.isOwnPayment(payment, row))
                    .findFirst().map(Payment::getStatus);
            boolean open = upcoming(row, event.startsAt());
            var ownParticipation = reviews.stream().filter(review -> !review.delegated())
                    .findFirst().map(Review::status).orElse(ParticipationStatus.NOT_REQUIRED);
            var mode = mode(row);
            var entry = new Entry(event, row.getId(), row.getCancellationVersion(), row.getStatus(), mode,
                    partnerName(row), mode != Mode.INDIVIDUAL && !row.isConfirmed() && !row.isCancelled()
                            ? row.queueTime() : null, row.getWaitlistPosition(), ownParticipation,
                    reviews.stream().map(this::participation).toList(),
                    row.needsReplacementInMain(), row.isReplacementSearch(), Boolean.TRUE.equals(row.getShareContacts()),
                    ownPayment.orElse(null), open ? availableActions(row) : List.of(),
                    open ? List.of() : pastCancellations);
            if (open) {
                upcoming.add(entry);
                if (!pastCancellations.isEmpty()) {
                    history.add(new Entry(event, row.getId(), row.getCancellationVersion(),
                            RegistrationStatus.CANCELLED, null, null, null, null, ParticipationStatus.NOT_REQUIRED,
                            List.of(), false, false, false, null, List.of(), pastCancellations));
                }
            } else {
                history.add(entry);
            }
        }
        var byStart = Comparator.comparing((Entry entry) -> entry.event().startsAt()).thenComparing(Entry::registrationId);
        upcoming.sort(byStart);
        history.sort(byStart.reversed());
        return new Overview(upcoming, history);
    }

    @Transactional
    public void withdraw(PlayerPadel actor, long registrationId, long version) {
        var row = lockOwned(actor, registrationId);
        if (row.isCancelled() && row.getCancellationVersion() > 0 && row.getCancellationVersion() - 1 == version) {
            return;
        }
        requireAction(row, version, Action.WITHDRAW);
        registrationService.cancelRegistration(row.getTournament().getId(), actor.getId(), null, version);
    }

    @Transactional
    public void changeContactVisibility(PlayerPadel actor, long registrationId, ContactVisibility request) {
        var row = lockOwned(actor, registrationId);
        requireAction(row, request.version(), Action.CHANGE_CONTACT_VISIBILITY);
        row.setShareContacts(request.shareContacts());
    }

    private TournamentRegistration lockOwned(PlayerPadel actor, long registrationId) {
        var eventId = registrations.findOwnedEventId(registrationId, actor.getId())
                .orElseThrow(PlayerEventsService::notFound);
        tournaments.lockById(eventId).orElseThrow(PlayerEventsService::notFound);
        return registrations.findByTournamentIdAndPlayerId(eventId, actor.getId())
                .filter(row -> row.getId().equals(registrationId)).orElseThrow(PlayerEventsService::notFound);
    }

    private void requireAction(TournamentRegistration row, long version, Action action) {
        if (!Objects.equals(row.getCancellationVersion(), version)
                || !upcoming(row, mapper.startsAt(row.getTournament())) || !availableActions(row).contains(action)) {
            throw new AccountException(HttpStatus.CONFLICT, "PLAYER_EVENT_ACTION_UNAVAILABLE");
        }
    }

    private List<Action> availableActions(TournamentRegistration row) {
        var tournament = row.getTournament();
        List<Action> actions = new ArrayList<>();
        boolean acceptedTerms = PairTermsService.accepted(row);
        boolean cancellablePair = mode(row) == Mode.PAIR && pairTerms.isCancellable(tournament);
        if (cancellablePair) actions.add(Action.CANCEL_PAIR);
        if (acceptedTerms
                ? cancellablePair && registrationService.findActivePairPartner(tournament.getId(), row).isPresent()
                : tournament.canCancelRegistration()) {
            actions.add(Action.WITHDRAW);
        }
        if (row.getStatus() == RegistrationStatus.SOLO_SEARCH || row.needsReplacementInMain()) {
            actions.add(Action.CHANGE_CONTACT_VISIBILITY);
        }
        if (row.needsReplacementInMain() && pairTerms.isCancellable(tournament)) {
            actions.add(Action.SEARCH_REPLACEMENT);
        }
        return actions;
    }

    private boolean upcoming(TournamentRegistration row, OffsetDateTime start) {
        Tournament event = row.getTournament();
        return Boolean.TRUE.equals(row.getIsActive()) && !row.isCancelled() && Boolean.TRUE.equals(event.getIsActive())
                && event.getEstado() != TournamentStatus.CANCELADO && event.getEstado() != TournamentStatus.FINALIZADO
                && start.isAfter(OffsetDateTime.now());
    }

    private Mode mode(TournamentRegistration row) {
        if (row.getTournament().getModalidad() != Modalidad.DOBLES) return Mode.INDIVIDUAL;
        return Boolean.TRUE.equals(row.getIsDoubleRegistration()) ? Mode.PAIR : Mode.SOLO;
    }

    private String partnerName(TournamentRegistration row) {
        return Optional.ofNullable(row.getPartner()).map(PlayerPadel::getNombreCompleto)
                .orElseGet(() -> {
                    var name = Stream.of(row.getPartnerFirstName(), row.getPartnerLastName())
                            .filter(Objects::nonNull).map(String::strip).filter(part -> !part.isEmpty())
                            .collect(Collectors.joining(" "));
                    return name.isEmpty() ? null : name;
                });
    }

    private Participation participation(Review review) {
        List<Decision> actions = new ArrayList<>();
        if (review.canRespond()) actions.add(Decision.CONFIRM);
        if (review.canDecline()) actions.add(Decision.DECLINE);
        return new Participation(review.requestId(), review.playerName(), review.status(), review.delegated(), actions);
    }

    private static AccountException notFound() {
        return new AccountException(HttpStatus.NOT_FOUND, "PLAYER_EVENT_NOT_FOUND");
    }
}
