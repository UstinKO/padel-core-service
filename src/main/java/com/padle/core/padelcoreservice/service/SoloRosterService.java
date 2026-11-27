package com.padle.core.padelcoreservice.service;

import com.padle.core.application.tournament.Nivel;
import com.padle.core.persistence.identity.PlayerPadel;
import com.padle.core.persistence.identity.PlayerRepository;
import com.padle.core.padelcoreservice.dto.SoloRoster.*;
import com.padle.core.padelcoreservice.dto.CancellationHistory.Scope;
import com.padle.core.padelcoreservice.exception.SoloRosterException;
import com.padle.core.padelcoreservice.model.*;
import com.padle.core.padelcoreservice.model.enums.*;
import com.padle.core.padelcoreservice.repository.*;
import com.padle.core.padelcoreservice.repository.PairProposalResponseRepository.Response;
import com.padle.core.padelcoreservice.repository.americano.AmericanoMatchRepository;
import com.padle.core.padelcoreservice.service.cancellation.RegistrationCancellationService;
import com.padle.core.padelcoreservice.service.conduct.ConductChange;
import com.padle.core.padelcoreservice.util.AfterCommit;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SoloRosterService {
    private final TournamentRepository events;
    private final TournamentRegistrationRepository registrations;
    private final PlayerRepository players;
    private final PaymentRepository payments;
    private final TournamentAccessService access;
    private final TournamentService tournaments;
    private final SoloRosterPlayers rosterPlayers;
    private final SoloRosterTeams teams;
    private final SoloRosterOperations operations;
    private final PairProposalResponseRepository proposals;
    private final RegistrationCancellationService cancellations;
    private final TournamentNotificationService notifications;
    private final EmailService email;
    private final AmericanoMatchRepository matches;
    private final ObjectMapper json;

    private record Prepared(Tournament event, Command command, List<TournamentRegistration> rows,
                            Optional<TournamentRegistration> source, Optional<TournamentRegistration> partner,
                            Optional<PlayerPadel> candidate) {}

    public Board board(long eventId, Owner owner) {
        authorized(eventId, owner, false);
        var rows = registrations.findByTournamentIdOrderByPositionAscWaitlistPositionAsc(eventId);
        return new Board(rows.stream().filter(SoloRosterService::single).map(this::participant).toList(),
                rows.stream().filter(row -> !Boolean.TRUE.equals(row.getIsActive()) && row.getPartner() == null)
                        .map(this::participant).toList(), operations.history(eventId));
    }

    public List<Found> search(long eventId, Owner owner, String query) {
        authorized(eventId, owner, false);
        if (query == null || query.strip().length() < 2 || query.length() > 100) return List.of();
        var rows = registrations.findByTournamentId(eventId);
        return players.searchRosterCandidates(query.strip(), query.replaceAll("\\D", ""), query.strip().replaceFirst("^@", ""))
                .stream().map(player -> {
                    var row = active(rows, player.getId());
                    return new Found(player.getId(), player.getNombreCompleto(), player.getNivelJugador().name(),
                            row.map(TournamentRegistration::getId).orElse(null), row.map(SoloRosterService::single).orElse(false),
                            row.map(this::pairName).orElse(""), row.map(r -> r.getStatus().name()).orElse(""), player.getPlayingSide());
                }).toList();
    }

    public Preview preview(long eventId, Owner owner, Command command) {
        return preview(prepare(authorized(eventId, owner, false), command));
    }

    @Transactional
    @ConductChange(ConductChange.Action.ROSTER)
    public Result apply(long eventId, Owner owner, Command command) {
        var event = authorized(eventId, owner, true);
        String hash = requestHash(command);
        var replay = operations.find(eventId, command.requestId());
        if (replay.isPresent()) {
            var saved = replay.orElseThrow();
            if (!saved.hash().equals(hash)) throw new SoloRosterException("solo.error.request");
            return json.readValue(saved.result(), Result.class);
        }
        rosterPlayers.lock(command.player());
        var prepared = prepare(event, command);
        var preview = preview(prepared);
        if (!Objects.equals(command.expectedState(), preview.state())) throw new SoloRosterException("solo.error.stale");
        if (preview.levelOverrideRequired() && !command.overrideLevel()) throw new SoloRosterException("solo.error.level");
        String before = json.writeValueAsString(preview.participants());
        Result result = switch (command.action()) {
            case ADD -> add(prepared);
            case PAIR -> pair(prepared);
            case REMOVE -> remove(prepared, owner);
        };
        registrations.flush();
        var changed = registrations.findByTournamentId(eventId).stream()
                .filter(row -> Objects.equals(row.getId(), result.registrationId())
                        || Objects.equals(row.getId(), result.partnerRegistrationId())).map(this::participant).toList();
        operations.save(eventId, command.requestId(), owner.getId(), command.action().name(), hash,
                json.writeValueAsString(result), before, json.writeValueAsString(changed));
        return result;
    }

    public static boolean single(TournamentRegistration row) {
        return row.isPairableSolo() || (row.needsReplacementInMain()
                && (row.getPartnerFirstName() == null || row.getPartnerFirstName().isBlank()));
    }

    private Tournament authorized(long id, Owner owner, boolean lock) {
        if (owner == null || !Boolean.TRUE.equals(owner.getIsActive())) throw new AccessDeniedException("Forbidden");
        var event = (lock ? events.lockById(id) : events.findById(id))
                .orElseThrow(() -> new SoloRosterException("solo.error.event"));
        access.assertCanManage(owner, event);
        if (event.getModalidad() != Modalidad.DOBLES) throw new SoloRosterException("solo.error.format");
        return event;
    }

    private Prepared prepare(Tournament event, Command command) {
        if (command == null || command.action() == null || command.requestId() == null) throw new SoloRosterException("solo.error.invalid");
        if (!Boolean.TRUE.equals(event.getIsActive()) || Set.of(TournamentStatus.FINALIZADO, TournamentStatus.CANCELADO).contains(event.getEstado())) {
            throw new SoloRosterException("solo.error.finished");
        }
        var rows = registrations.findByTournamentId(event.getId());
        var source = command.action() == Action.ADD ? Optional.<TournamentRegistration>empty()
                : Optional.of(requireSingle(rows, command.registrationId()));
        var partner = command.partnerRegistrationId() == null ? Optional.<TournamentRegistration>empty()
                : Optional.of(requireSingle(rows, command.partnerRegistrationId()));
        var candidate = command.action() == Action.REMOVE ? Optional.<PlayerPadel>empty()
                : partner.map(TournamentRegistration::getPlayer).or(() -> Optional.of(rosterPlayers.resolve(command.player())));
        if (command.action() != Action.PAIR && partner.isPresent()) throw new SoloRosterException("solo.error.invalid");
        if (command.action() == Action.ADD && command.kind() == null) throw new SoloRosterException("solo.error.invalid");
        if (command.action() == Action.PAIR && candidate.orElseThrow().getId() != null) {
            if (source.orElseThrow().getPlayer().getId().equals(candidate.orElseThrow().getId())) throw new SoloRosterException("solo.error.self");
            partner = active(rows, candidate.orElseThrow().getId());
            if (partner.isPresent() && !single(partner.orElseThrow())) throw new SoloRosterException("solo.error.paired", pairName(partner.orElseThrow()));
        }
        var prepared = new Prepared(event, command, rows, source, partner, candidate);
        if (command.action() != Action.ADD || active(rows, candidate.orElseThrow().getId()).isEmpty()) {
            teams.guard(event, participants(prepared).stream().map(Participant::playerId).filter(Objects::nonNull).toList());
        }
        return prepared;
    }

    private Preview preview(Prepared prepared) {
        var command = prepared.command();
        var people = participants(prepared);
        var existing = command.action() == Action.ADD ? active(prepared.rows(), prepared.candidate().orElseThrow().getId())
                : Optional.<TournamentRegistration>empty();
        String status = switch (command.action()) {
            case ADD -> existing.map(row -> row.getStatus().name()).orElse(command.kind() == Kind.SEARCH ? "SOLO_SEARCH" : "SOLO_ADD_LATER");
            case REMOVE -> "CANCELLED";
            case PAIR -> tournaments.previewPairStatus(prepared.event(), pairRows(prepared)).name();
        };
        boolean override = command.action() != Action.REMOVE && existing.isEmpty()
                && people.stream().anyMatch(person -> TournamentLevelPolicy.evaluate(prepared.event().getTipo(),
                prepared.event().getCategoriaNivel(), Nivel.valueOf(person.level())).verdict() != TournamentLevelPolicy.Verdict.ALLOWED);
        return new Preview(state(prepared), people, status, override, existing.map(TournamentRegistration::getId).orElse(null),
                existing.map(this::pairName).orElse(""));
    }

    private List<Participant> participants(Prepared p) {
        if (p.command().action() == Action.REMOVE) return List.of(participant(p.source().orElseThrow()));
        if (p.command().action() == Action.ADD) {
            return List.of(active(p.rows(), p.candidate().orElseThrow().getId()).map(this::participant)
                    .orElseGet(() -> candidate(p.candidate().orElseThrow())));
        }
        return List.of(participant(p.source().orElseThrow()), p.partner().map(this::participant)
                .orElseGet(() -> candidate(p.candidate().orElseThrow())));
    }

    private List<TournamentRegistration> pairRows(Prepared p) {
        var other = p.partner().orElseGet(() -> TournamentRegistration.builder().player(p.candidate().orElseThrow())
                .status(RegistrationStatus.SOLO_ADD_LATER).queuedAt(LocalDateTime.now()).build());
        return List.of(p.source().orElseThrow(), other);
    }

    private Result add(Prepared p) {
        var player = rosterPlayers.materialize(p.candidate().orElseThrow());
        var existing = active(p.rows(), player.getId());
        if (existing.isPresent()) return result(p.command(), existing.orElseThrow(), Optional.empty(), true);
        var row = newSolo(p.event(), player, p.command());
        return result(p.command(), row, Optional.empty(), false);
    }

    private Result pair(Prepared p) {
        var first = p.source().orElseThrow();
        var player = rosterPlayers.materialize(p.candidate().orElseThrow());
        var second = p.partner().orElseGet(() -> newSolo(p.event(), player, p.command()));
        if (second.getStatus() == RegistrationStatus.CONFIRMED && first.getStatus() != RegistrationStatus.CONFIRMED) {
            var swap = first; first = second; second = swap;
        }
        boolean held = first.getStatus() == RegistrationStatus.CONFIRMED;
        boolean imported = teams.imported(p.event().getId());
        UUID booking = held && first.getPairBookingId() != null ? first.getPairBookingId() : p.command().requestId();
        LocalDateTime queued = Stream.of(first.queueTime(), second.queueTime()).filter(Objects::nonNull).min(Comparator.naturalOrder()).orElseGet(LocalDateTime::now);
        Integer position = held ? first.getPosition() : registrations.findMaxWaitlistPosition(p.event().getId()).orElse(0) + 1;
        retag(first, booking); retag(second, booking);
        attach(first, second.getPlayer(), first.getPlayer().getId(), booking, queued, position, held);
        attach(second, first.getPlayer(), first.getPlayer().getId(), booking, queued, position, held);
        registrations.saveAllAndFlush(List.of(first, second));
        tournaments.processWaitlistForTournament(p.event().getId());
        teams.pair(first, second, imported);
        if (first.isInWaitlist()) {
            var main = first; var partner = second;
            AfterCommit.run(() -> notifications.sendPairRegistrationEmails(main, partner));
        }
        return result(p.command(), first, Optional.of(second), false);
    }

    private Result remove(Prepared p, Owner owner) {
        var row = p.source().orElseThrow();
        teams.remove(p.event().getId(), List.of(row.getPlayer().getId()));
        cancellations.cancel(List.of(row), cancellations.organizer(owner, Scope.PARTICIPANT, ""), "ORGANIZER_REMOVAL");
        registrations.saveAndFlush(row);
        tournaments.processWaitlistForTournament(p.event().getId());
        String date = p.event().getFechaInicio().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String time = p.event().getHoraInicio().toString();
        if (row.getPlayer().isGuestAccount()) {
            GuestPartnerNotice.forward(email, row, p.event().getNombre(), GuestPartnerNotice.Kind.CANCELLED);
        } else {
            AfterCommit.run(() -> email.sendRegistrationCancelledEmail(row.getPlayer().getEmail(), row.getPlayer().getNombre(),
                    p.event().getNombre(), date, time, "", null, row.getPlayer().getLocale()));
        }
        return result(p.command(), row, Optional.empty(), false);
    }

    private TournamentRegistration newSolo(Tournament event, PlayerPadel player, Command command) {
        var row = registrations.findByTournamentIdAndPlayerId(event.getId(), player.getId())
                .orElseGet(() -> TournamentRegistration.builder().tournament(event).player(player).build());
        if (Boolean.TRUE.equals(row.getIsActive()) && row.getId() != null) throw new SoloRosterException("solo.error.stale");
        row.setStatus(command.kind() == Kind.SEARCH ? RegistrationStatus.SOLO_SEARCH : RegistrationStatus.SOLO_ADD_LATER);
        row.setRegistrationDate(LocalDateTime.now()); row.setQueuedAt(row.getRegistrationDate());
        row.setIsActive(true); row.setIsDoubleRegistration(false); row.setPartner(null); row.setMainPlayerId(null);
        row.setPairBookingId(command.requestId()); row.setPairBookedBy(null); row.setAttended(false); row.setParticipationConfirmed(false);
        row.setPosition(null); row.setWaitlistPosition(null); row.setPartnerFirstName(null); row.setPartnerLastName(null);
        row.setPartnerPhone(null); row.setPartnerEmail(null); row.setPartnerTelegram(null); row.setReplacementSearch(false);
        row.setCancellationDate(null); row.setCancellationReason(null); row.setPartnerActionToken(UUID.randomUUID().toString());
        row.setPartnerRegistrationToken(null); row.setPartnerTokenExpiry(null); row.setPairProposerRegId(null);
        row.setSoloReminderSent(false); row.setFinalReminderSent(false); row.setPartnerAttended(false);
        row.setPairTermsVersion(null); row.setPairTermsAcceptedAt(null); row.setPairTermsAcceptedBy(null);
        row.setPairTermsPricePerPlayer(null); row.setPairTermsCurrency(null);
        row.setPairTermsFreeUntil(null); row.setPairTermsLateFraction(null);
        return registrations.saveAndFlush(row);
    }

    private void attach(TournamentRegistration row, PlayerPadel partner, Long main, UUID booking,
                        LocalDateTime queued, Integer position, boolean held) {
        proposals.record(row.getPartnerRegistrationToken(), Response.PAIR_FORMED);
        row.setPartner(partner); row.setIsDoubleRegistration(true); row.setMainPlayerId(main); row.setPairBookingId(booking);
        row.setPartnerFirstName(partner.getNombre()); row.setPartnerLastName(partner.getApellido());
        row.setPartnerPhone(partner.getTelefono()); row.setPartnerEmail(partner.getEmail()); row.setPartnerTelegram(partner.getTelegramUsername());
        row.setPartnerActionToken(null); row.setPartnerRegistrationToken(null); row.setPartnerTokenExpiry(null);
        row.setPairProposerRegId(null); row.setReplacementSearch(false); row.setQueuedAt(queued);
        if (held) { row.confirm(); row.setPosition(position); } else { row.moveToWaitlist(position); }
    }

    private void retag(TournamentRegistration row, UUID booking) {
        payments.findByRegistrationId(row.getId()).stream().filter(payment -> Boolean.TRUE.equals(payment.getIsActive()))
                .filter(payment -> PaymentService.isOwnPayment(payment, row)).forEach(payment -> payment.setPairBookingId(booking));
    }

    private Participant participant(TournamentRegistration row) {
        return new Participant(row.getId(), row.getPlayer().getId(), row.getPlayer().getNombreCompleto(),
                row.getPlayer().getNivelJugador().name(), row.getStatus().name(), row.getStatus() != RegistrationStatus.CONFIRMED,
                Boolean.TRUE.equals(row.getAttended()), payments.findByRegistrationId(row.getId()).stream()
                .filter(payment -> Boolean.TRUE.equals(payment.getIsActive()))
                .filter(payment -> PaymentService.isOwnPayment(payment, row))
                .map(payment -> new Money(payment.getId(), payment.getAmount(), payment.getCurrency(), payment.getStatus().name(),
                        payment.getPaymentMethod() == null ? "" : payment.getPaymentMethod().name(), payment.getTransactionId(), payment.getNotes())).toList(), row.getPlayer().getPlayingSide());
    }

    private Participant candidate(PlayerPadel player) {
        return new Participant(null, player.getId(), player.getNombreCompleto(), player.getNivelJugador().name(), "NEW", true, false, List.of(), player.getPlayingSide());
    }

    private TournamentRegistration requireSingle(List<TournamentRegistration> rows, Long id) {
        return rows.stream().filter(row -> Objects.equals(row.getId(), id)).filter(SoloRosterService::single).findFirst()
                .orElseThrow(() -> new SoloRosterException("solo.error.single"));
    }

    private Optional<TournamentRegistration> active(List<TournamentRegistration> rows, Long playerId) {
        return rows.stream().filter(row -> Objects.equals(row.getPlayer().getId(), playerId))
                .filter(row -> Boolean.TRUE.equals(row.getIsActive())).findFirst();
    }

    private String pairName(TournamentRegistration row) {
        return row.getPlayer().getNombreCompleto() + (row.getPartner() == null ? "" : " / " + row.getPartner().getNombreCompleto());
    }

    private Result result(Command command, TournamentRegistration row, Optional<TournamentRegistration> partner, boolean existing) {
        return new Result(command.requestId(), row.getId(), partner.map(TournamentRegistration::getId).orElse(null), row.getStatus().name(), existing);
    }

    private String requestHash(Command command) {
        return digest(json.writeValueAsString(new Command(command.requestId(), command.action(), command.registrationId(),
                command.partnerRegistrationId(), command.player(), command.kind(), command.overrideLevel(), null)));
    }

    private String state(Prepared p) {
        var rows = p.rows().stream().sorted(Comparator.comparing(TournamentRegistration::getId)).map(row ->
                List.of(row.getId(), row.getStatus(), String.valueOf(row.getPairBookingId()), String.valueOf(row.getUpdatedAt()),
                        Boolean.TRUE.equals(row.getIsActive()), String.valueOf(row.getPartner() == null ? null : row.getPartner().getId()),
                        Boolean.TRUE.equals(row.getAttended()), String.valueOf(row.getPlayer().getNivelJugador()))).toList();
        var money = payments.findByTournamentId(p.event().getId()).stream().sorted(Comparator.comparing(Payment::getId))
                .map(payment -> List.of(payment.getId(), String.valueOf(payment.getUpdatedAt()), String.valueOf(payment.getPairBookingId()),
                        payment.getStatus(), payment.getAmount(), payment.getCurrency())).toList();
        var games = matches.findByTournamentIdOrderByRoundIdAscMatchNumberAsc(p.event().getId()).stream()
                .map(match -> List.of(match.getId(), match.getStatus(), String.valueOf(match.getUpdatedAt()))).toList();
        return digest(json.writeValueAsString(List.of(p.event().getEstado(), p.event().getCupoMax(), String.valueOf(p.event().getUpdatedAt()),
                p.event().getTipo(), p.event().getCategoriaNivel(), p.event().getGeneroFormato(), rows, money, games,
                p.candidate().map(player -> String.valueOf(player.getId()) + ":" + player.getNivelJugador()).orElse(""))));
    }

    private String digest(String source) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException error) { throw new IllegalStateException("SHA-256 недоступен", error); }
    }
}
