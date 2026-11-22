package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.exception.SoloRosterException;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.Payment;
import com.padle.core.padelcoreservice.model.TournamentRegistration;
import com.padle.core.padelcoreservice.model.americano.AmericanoTeam;
import com.padle.core.padelcoreservice.model.enums.*;
import com.padle.core.padelcoreservice.repository.PaymentRepository;
import com.padle.core.padelcoreservice.repository.MatchRepository;
import com.padle.core.padelcoreservice.repository.TournamentRegistrationRepository;
import com.padle.core.padelcoreservice.repository.americano.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.stream.Stream;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SoloRosterTeams {
    private final AmericanoTeamRepository teams;
    private final AmericanoMatchRepository matches;
    private final AmericanoRoundRepository rounds;
    private final TournamentRegistrationRepository registrations;
    private final PaymentRepository payments;
    private final MatchRepository brackets;

    public void guard(Tournament tournament, List<Long> playerIds) {
        long id = tournament.getId();
        if (rounds.existsByTournamentIdAndPhaseIsNull(id)
                || rounds.existsByTournamentIdAndPhase(id, TournamentPhase.PLAYOFF)) {
            throw new SoloRosterException("solo.error.stage");
        }
        var legacy = brackets.findByTournamentId(id);
        var legacyBlocked = legacy.stream().filter(match -> Stream.of(match.getPlayer1Id(), match.getPlayer2Id(),
                match.getPlayer3Id(), match.getPlayer4Id()).filter(Objects::nonNull).anyMatch(playerIds::contains))
                .map(match -> "#" + match.getId()).toList();
        if (!legacyBlocked.isEmpty()) throw new SoloRosterException("solo.error.matches", String.join(", ", legacyBlocked));
        if (!legacy.isEmpty()) throw new SoloRosterException("solo.error.stage");
        var teamIds = involved(id, playerIds).stream().map(AmericanoTeam::getId).toList();
        var blocked = matches.findByTournamentIdOrderByRoundIdAscMatchNumberAsc(id).stream()
                .filter(match -> teamIds.contains(match.getTeam1Id()) || teamIds.contains(match.getTeam2Id())
                        || Stream.of(match.getTeam1Player1(), match.getTeam1Player2(), match.getTeam2Player1(), match.getTeam2Player2())
                        .filter(Objects::nonNull).anyMatch(player -> playerIds.contains(player.getId())))
                .map(match -> "#" + match.getId()).toList();
        if (!blocked.isEmpty()) throw new SoloRosterException("solo.error.matches", String.join(", ", blocked));
    }

    public boolean imported(long id) { return !teams.findByTournamentId(id).isEmpty(); }

    public void remove(long id, List<Long> players) {
        teams.deleteAll(involved(id, players));
        teams.flush();
    }

    public void pair(TournamentRegistration main, TournamentRegistration partner, boolean imported) {
        long id = main.getTournament().getId();
        var existing = involved(id, List.of(main.getPlayer().getId(), partner.getPlayer().getId()));
        if (main.getStatus() != RegistrationStatus.CONFIRMED) {
            teams.deleteAll(existing);
            return;
        }
        int next = teams.findMaxTeamNumber(id).orElse(0) + 1;
        Optional<AmericanoTeam> retained = existing.stream()
                .filter(team -> team.getPlayer1().getId().equals(main.getPlayer().getId())).findFirst()
                .or(() -> existing.stream().findFirst());
        var extras = existing.stream().filter(team -> retained.map(t -> !t.getId().equals(team.getId())).orElse(true)).toList();
        teams.deleteAll(extras);
        teams.flush();
        if (!imported && retained.isEmpty()) return;
        var team = retained.orElseGet(() -> AmericanoTeam.builder().tournament(main.getTournament())
                .teamNumber(next).currentPosition(0).registrationSource(RegistrationSource.WEBSITE).build());
        team.setPlayer1(main.getPlayer());
        team.setPlayer2(partner.getPlayer());
        team.setPlayer2Name(null);
        team.setPlayer2Phone(null);
        team.setStatus(AmericanoPlayerStatus.ACTIVE);
        team.setAttended(Boolean.TRUE.equals(main.getAttended()) && Boolean.TRUE.equals(partner.getAttended()));
        team.setHasPaid(paid(main) && paid(partner));
        teams.save(team);
    }

    public void refresh(long tournamentId) {
        var rows = registrations.findByTournamentId(tournamentId).stream().filter(row -> Boolean.TRUE.equals(row.getIsActive()))
                .collect(Collectors.toMap(row -> row.getPlayer().getId(), row -> row));
        var money = payments.findByTournamentId(tournamentId).stream()
                .collect(Collectors.groupingBy(payment -> payment.getRegistration().getId()));
        for (var team : teams.findByTournamentId(tournamentId)) {
            var first = Optional.ofNullable(rows.get(team.getPlayer1().getId()));
            var second = team.getPlayer2() == null ? Optional.<TournamentRegistration>empty()
                    : Optional.ofNullable(rows.get(team.getPlayer2().getId()));
            if (first.isPresent() && team.getPlayer2() == null && team.getPlayer2Name() != null
                    && !team.getPlayer2Name().isBlank()) {
                var row = first.orElseThrow();
                team.setAttended(Boolean.TRUE.equals(row.getAttended()) && Boolean.TRUE.equals(row.getPartnerAttended()));
                team.setHasPaid(paid(row, money.getOrDefault(row.getId(), List.of()))
                        && guestPaid(row, money.getOrDefault(row.getId(), List.of())));
                continue;
            }
            if (first.isEmpty() || second.isEmpty()) {
                team.setAttended(false);
                team.setHasPaid(false);
                continue;
            }
            var a = first.orElseThrow();
            var b = second.orElseThrow();
            boolean samePair = Boolean.TRUE.equals(a.getIsDoubleRegistration()) && Boolean.TRUE.equals(b.getIsDoubleRegistration())
                    && Objects.equals(a.getPairBookingId(), b.getPairBookingId())
                    && a.getPartner() != null && a.getPartner().getId().equals(b.getPlayer().getId());
            team.setAttended(samePair && Boolean.TRUE.equals(a.getAttended()) && Boolean.TRUE.equals(b.getAttended()));
            team.setHasPaid(samePair && paid(a, money.getOrDefault(a.getId(), List.of()))
                    && paid(b, money.getOrDefault(b.getId(), List.of())));
        }
    }

    private boolean guestPaid(TournamentRegistration row, List<Payment> source) {
        return source.stream().filter(payment -> Boolean.TRUE.equals(payment.getIsActive()))
                .filter(payment -> Objects.equals(payment.getPairBookingId(), row.getPairBookingId()))
                .filter(payment -> payment.getNotes() != null && payment.getNotes().startsWith("PARTNER_PAYMENT"))
                .anyMatch(payment -> payment.getStatus() == PaymentStatus.PAID);
    }

    private boolean paid(TournamentRegistration row) { return paid(row, payments.findByRegistrationId(row.getId())); }

    private boolean paid(TournamentRegistration row, List<Payment> source) {
        return source.stream().filter(payment -> Boolean.TRUE.equals(payment.getIsActive()))
                .filter(payment -> PaymentService.isOwnPayment(payment, row))
                .anyMatch(payment -> payment.getStatus() == PaymentStatus.PAID);
    }

    private List<AmericanoTeam> involved(long event, List<Long> ids) {
        return teams.findByTournamentId(event).stream().filter(team -> ids.contains(team.getPlayer1().getId())
                || (team.getPlayer2() != null && ids.contains(team.getPlayer2().getId()))).toList();
    }
}
