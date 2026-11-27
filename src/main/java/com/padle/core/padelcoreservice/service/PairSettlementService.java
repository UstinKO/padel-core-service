package com.padle.core.padelcoreservice.service;

import com.padle.core.application.registration.PairSettlements;
import com.padle.core.application.registration.PairSettlements.Cancellation;
import com.padle.core.application.registration.PairSettlements.Settlement;
import com.padle.core.persistence.tournament.TournamentRegistration;
import com.padle.core.application.registration.RegistrationStatus;
import com.padle.core.application.tournament.TournamentStatus;
import com.padle.core.persistence.tournament.TournamentRegistrationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PairSettlementService {
    private final PairSettlements ledger;
    private final PairTermsService terms;
    private final TournamentRegistrationRepository registrations;

    public record Pair(UUID bookingId, String participants) {}

    public record LegacyBooker(Long playerId, String playerName, String participants) {}

    @Transactional(readOnly = true)
    public List<Settlement> settlements(Long tournamentId) {
        return ledger.forTournament(tournamentId);
    }

    @Transactional(readOnly = true)
    public List<Pair> confirmedPairs(Long tournamentId, boolean noShowOnly) {
        var settled = settlements(tournamentId);
        return groups(tournamentId).entrySet().stream().filter(entry -> entry.getValue().size() == 2)
                .filter(entry -> !noShowOnly || (eligibleForNoShow(entry.getValue())
                        && !PairTermsService.startsAt(entry.getValue().get(0).getTournament()).isAfter(PairTermsService.now())
                        && settled.stream().noneMatch(s -> s.bookingId().equals(entry.getKey()))))
                .map(entry -> new Pair(entry.getKey(), names(entry.getValue())))
                .sorted(java.util.Comparator.comparing(Pair::participants))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LegacyBooker> legacyBookers(Long tournamentId) {
        return registrations.findByTournamentId(tournamentId).stream()
                .filter(this::unverifiedPairMember)
                .filter(row -> terms.canOffer(row.getTournament()))
                .map(row -> Map.entry(row, terms.members(row)))
                .filter(entry -> isReviewable(entry.getKey(), entry.getValue()))
                .map(entry -> new LegacyBooker(entry.getKey().getPlayer().getId(),
                        entry.getKey().getPlayer().getNombreCompleto(), names(entry.getValue())))
                .toList();
    }

    @Transactional
    public void verifyLegacyBooker(Long tournamentId, Long playerId, Long ownerId) {
        var tournament = terms.lock(tournamentId);
        var row = terms.activeRegistration(tournamentId, playerId);
        var group = terms.members(row);
        if (!terms.canOffer(tournament) || !isReviewable(row, group)) {
            throw new IllegalStateException("PAIR_REVIEW_UNAVAILABLE");
        }
        UUID bookingId = row.getPairBookingId() == null ? UUID.randomUUID() : row.getPairBookingId();
        group.forEach(member -> {
            member.setPairBookingId(bookingId);
            member.setPairBookedBy(playerId);
        });
        ledger.recordBookerReview(bookingId, tournamentId, playerId, ownerId, PairTermsService.now());
    }

    private boolean unverifiedPairMember(TournamentRegistration row) {
        return Boolean.TRUE.equals(row.getIsActive()) && Boolean.TRUE.equals(row.getIsDoubleRegistration())
                && row.getPartner() != null && row.getPairBookedBy() == null
                && (row.getStatus() == RegistrationStatus.CONFIRMED || row.getStatus() == RegistrationStatus.WAITLIST);
    }

    private boolean isReviewable(TournamentRegistration row, List<TournamentRegistration> group) {
        return group.size() == 2 && group.stream().allMatch(this::unverifiedPairMember)
                && group.stream().anyMatch(other -> row.getPartner().getId().equals(other.getPlayer().getId())
                        && other.getPartner().getId().equals(row.getPlayer().getId()))
                && !terms.hasUnassignedPayments(group);
    }

    @Transactional
    public void recordNoShow(Long tournamentId, UUID bookingId, Long ownerId) {
        var tournament = terms.lock(tournamentId);
        var group = groups(tournamentId).getOrDefault(bookingId, List.of());
        if (PairTermsService.startsAt(tournament).isAfter(PairTermsService.now())
                || tournament.getEstado() == TournamentStatus.CANCELADO || !eligibleForNoShow(group)) {
            throw new IllegalStateException("PAIR_NO_SHOW_UNAVAILABLE");
        }
        if (settlements(tournamentId).stream().anyMatch(s -> s.bookingId().equals(bookingId))) return;
        var row = group.get(0);
        String currency = row.getPairTermsCurrency();
        var quote = PairCancellationPolicy.quoteFromTerms(row.getPairTermsFreeUntil(),
                PairTermsService.now().isAfter(row.getPairTermsFreeUntil()) ? PairTermsService.now() : row.getPairTermsFreeUntil().plusSeconds(1),
                row.getPairTermsPricePerPlayer(), row.getPairTermsLateFraction(), terms.amountPaid(bookingId, currency), false);
        ledger.recordCancellation(new Cancellation(bookingId, tournamentId, ownerId, PairTermsService.now(),
                row.getPairTermsVersion(), currency, quote.obligation(), quote.alreadyPaid(), quote.remainingDue(),
                quote.refundable(), names(group), true));
    }

    @Transactional
    public void recordPaidReplacement(Long tournamentId, UUID bookingId, UUID replacementId, Long ownerId) {
        var tournament = terms.lock(tournamentId);
        var existing = settlements(tournamentId);
        var settlement = existing.stream().filter(s -> s.bookingId().equals(bookingId)).findFirst()
                .orElseThrow(() -> new IllegalStateException("PAIR_REPLACEMENT_UNAVAILABLE"));
        if (Objects.equals(settlement.replacementBookingId(), replacementId)) return;
        var group = groups(tournamentId).getOrDefault(replacementId, List.of());
        if (bookingId.equals(replacementId) || settlement.noShow() || settlement.replacementBookingId() != null
                || group.size() != 2 || existing.stream().anyMatch(s -> replacementId.equals(s.replacementBookingId()))) {
            throw new IllegalStateException("PAIR_REPLACEMENT_UNAVAILABLE");
        }
        var row = group.get(0);
        boolean accepted = PairTermsService.accepted(row);
        BigDecimal price = accepted ? row.getPairTermsPricePerPlayer() : tournament.getPrecio();
        String currency = accepted ? row.getPairTermsCurrency() : tournament.getMoneda();
        if (terms.amountPaid(replacementId, currency).compareTo(price.multiply(BigDecimal.valueOf(2))) < 0) {
            throw new IllegalStateException("PAIR_REPLACEMENT_NOT_PAID");
        }
        BigDecimal originalPaid = terms.amountPaid(bookingId, settlement.currency());
        ledger.recordReplacement(bookingId, tournamentId, replacementId, ownerId, PairTermsService.now(), originalPaid);
    }

    private Map<UUID, List<TournamentRegistration>> groups(Long tournamentId) {
        return registrations.findByTournamentId(tournamentId).stream()
                .filter(row -> Boolean.TRUE.equals(row.getIsActive()) && row.getPairBookingId() != null)
                .filter(row -> row.getStatus() == RegistrationStatus.CONFIRMED || row.getStatus() == RegistrationStatus.PARTICIPATED)
                .collect(Collectors.groupingBy(TournamentRegistration::getPairBookingId));
    }

    private boolean eligibleForNoShow(List<TournamentRegistration> group) {
        return group.size() == 2 && group.stream().allMatch(row -> PairTermsService.accepted(row)
                && row.getStatus() == RegistrationStatus.CONFIRMED && !Boolean.TRUE.equals(row.getAttended()));
    }

    static String names(List<TournamentRegistration> group) {
        return group.stream().map(row -> row.getPlayer().getNombreCompleto()).collect(Collectors.joining(" / "));
    }
}
