package com.padle.core.padelcoreservice.service;

import com.fasterxml.jackson.annotation.JsonFormat;

import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.TournamentRegistration;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.PaymentStatus;
import com.padle.core.padelcoreservice.model.enums.RegistrationStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.repository.PaymentRepository;
import com.padle.core.padelcoreservice.repository.TournamentRegistrationRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PairTermsService {
    private static final ZoneId EVENT_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    private final TournamentRepository tournaments;
    private final TournamentRegistrationRepository registrations;
    private final PaymentRepository payments;
    private final JdbcTemplate jdbc;
    private final TournamentNotificationService notifications;

    @Value("${app.pair-terms.enabled:false}")
    private boolean enabled;

    public record Offer(String token, int version, String eventName,
                        @JsonFormat(shape = JsonFormat.Shape.STRING) OffsetDateTime startsAt,
                        @JsonFormat(shape = JsonFormat.Shape.STRING) OffsetDateTime freeUntil, BigDecimal pricePerPlayer, BigDecimal pairPrice,
                        BigDecimal lateObligation, String currency, String category, String regulationUrl) {}

    public record Cancellation(String token, boolean legacy, boolean late, boolean canLeave, boolean canReplace,
                               @JsonFormat(shape = JsonFormat.Shape.STRING) OffsetDateTime freeUntil, String currency, BigDecimal obligation,
                               BigDecimal alreadyPaid, BigDecimal remainingDue, BigDecimal refundable,
                               String paymentInstructions, String organizerContact) {}

    public record BookingTerms(Offer terms, boolean canAccept, boolean accepted) {}

    @Transactional(readOnly = true)
    public Optional<Offer> offer(Long tournamentId) {
        return tournaments.findById(tournamentId).filter(this::canOffer).map(this::offerFor);
    }

    @Transactional(readOnly = true)
    public Optional<Offer> offer(Long tournamentId, Long playerId) {
        return offer(tournamentId).filter(ignored -> registrations
                .findByTournamentIdAndPlayerIdAndIsActiveTrue(tournamentId, playerId)
                .map(row -> canFormFromSolo(row)).orElse(true));
    }

    boolean canFormFromSolo(TournamentRegistration row) {
        return row.getMainPlayerId() == null && row.getPartner() == null
                && (row.getStatus() == RegistrationStatus.SOLO_SEARCH || row.getStatus() == RegistrationStatus.SOLO_ADD_LATER
                    || row.getStatus() == RegistrationStatus.WAITLIST)
                && !hasUnassignedPayments(List.of(row));
    }

    boolean canOffer(Tournament tournament) {
        return enabled && tournament.getTipo() == TournamentType.AMERICANO_TEAMS
                && isCancellable(tournament)
                && tournament.getEstado() == TournamentStatus.REGISTRO_ABIERTO;
    }

    Offer validateNewBooking(Long tournamentId, String token) {
        if (!enabled && token == null) return null;
        Tournament tournament = lock(tournamentId);
        if (!canOffer(tournament)) {
            if (token != null) throw conflict("PAIR_TERMS_CHANGED");
            return null;
        }
        Offer offer = offerFor(tournament);
        requireToken(offer.token(), token);
        return offer;
    }

    @Transactional(readOnly = true)
    public BookingTerms bookingTerms(Long tournamentId, Long playerId) {
        TournamentRegistration registration = activeRegistration(tournamentId, playerId);
        Tournament tournament = registration.getTournament();
        if (accepted(registration)) {
            return new BookingTerms(acceptedOffer(tournament, registration), false, true);
        }
        boolean eligible = canAccept(tournament, registration, playerId);
        return new BookingTerms(eligible ? offerFor(tournament) : null, eligible, false);
    }

    @Transactional
    public BookingTerms accept(Long tournamentId, Long playerId, String token) {
        Tournament tournament = lock(tournamentId);
        TournamentRegistration registration = activeRegistration(tournamentId, playerId);
        if (accepted(registration) && Objects.equals(registration.getPairBookedBy(), playerId)) {
            return new BookingTerms(acceptedOffer(tournament, registration), false, true);
        }
        if (!canAccept(tournament, registration, playerId)) throw conflict("PAIR_TERMS_UNAVAILABLE");
        Offer offer = offerFor(tournament);
        requireToken(offer.token(), token);
        LocalDateTime acceptedAt = now();
        var rows = members(registration);
        rows.forEach(row -> recordAcceptance(row, offer, playerId, acceptedAt));
        rows.stream().filter(row -> !row.getPlayer().getId().equals(playerId)).forEach(row ->
                notifications.sendPairTermsNotice(row.getPlayer(), offer, tournament.getClubId(), row.getStatus()));
        return new BookingTerms(offer, false, true);
    }

    static void recordAcceptance(TournamentRegistration row, Offer offer, Long booker, LocalDateTime acceptedAt) {
        row.setPairTermsVersion(offer.version());
        row.setPairTermsAcceptedAt(acceptedAt);
        row.setPairTermsAcceptedBy(booker);
        row.setPairTermsPricePerPlayer(offer.pricePerPlayer());
        row.setPairTermsCurrency(offer.currency());
        row.setPairTermsFreeUntil(offer.freeUntil().toLocalDateTime());
        row.setPairTermsLateFraction(PairCancellationPolicy.LATE_FRACTION);
    }

    static void copyAcceptance(TournamentRegistration source, TournamentRegistration target) {
        target.setPairTermsVersion(source.getPairTermsVersion());
        target.setPairTermsAcceptedAt(source.getPairTermsAcceptedAt());
        target.setPairTermsAcceptedBy(source.getPairTermsAcceptedBy());
        target.setPairTermsPricePerPlayer(source.getPairTermsPricePerPlayer());
        target.setPairTermsCurrency(source.getPairTermsCurrency());
        target.setPairTermsFreeUntil(source.getPairTermsFreeUntil());
        target.setPairTermsLateFraction(source.getPairTermsLateFraction());
    }

    static boolean accepted(TournamentRegistration row) {
        return row.getPairBookingId() != null && row.getPairBookedBy() != null
                && row.getPairTermsAcceptedAt() != null && row.getPairTermsVersion() != null
                && row.getPairTermsVersion() > 0 && Objects.equals(row.getPairBookedBy(), row.getPairTermsAcceptedBy());
    }

    @Transactional(readOnly = true)
    public Cancellation cancellation(Long tournamentId, Long playerId) {
        TournamentRegistration row = activeRegistration(tournamentId, playerId);
        return cancellation(row.getTournament(), row);
    }

    Cancellation cancellation(Tournament tournament, TournamentRegistration row) {
        if (!isCancellable(tournament)) throw conflict("PAIR_CANCELLATION_UNAVAILABLE");
        var participants = members(row).stream().map(r -> r.getPlayer().getId()).sorted().toList();
        boolean hasPartner = participants.size() > 1;
        boolean agreed = accepted(row);
        boolean canLeave = hasPartner && (agreed || tournament.canCancelRegistration());
        if (!agreed) {
            String token = token(row.getId(), row.getRegistrationDate(), row.getPairBookingId(), participants, "legacy");
            return new Cancellation(token, true, false, canLeave, hasPartner, null, null,
                    null, null, null, null, null, null);
        }

        String currency = row.getPairTermsCurrency();
        BigDecimal total = amountPaid(row.getPairBookingId(), currency);
        var quote = PairCancellationPolicy.quoteFromTerms(row.getPairTermsFreeUntil(), now(),
                row.getPairTermsPricePerPlayer(), row.getPairTermsLateFraction(), total, false);
        String token = token(row.getPairBookingId(), row.getPairTermsVersion(), quote, currency, participants,
                tournament.getPaymentInstructions(), tournament.getContactoOrganizador());
        return new Cancellation(token, false, quote.late(), canLeave, hasPartner, offset(quote.freeUntil()), currency,
                quote.obligation(), quote.alreadyPaid(), quote.remainingDue(), quote.refundable(),
                tournament.getPaymentInstructions(), tournament.getContactoOrganizador());
    }

    BigDecimal amountPaid(UUID bookingId, String currency) {
        var paid = payments.findByPairBookingId(bookingId).stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()) && p.getStatus() == PaymentStatus.PAID)
                .toList();
        if (paid.stream().anyMatch(p -> !currency.equalsIgnoreCase(p.getCurrency()))) {
            throw conflict("PAIR_PAYMENT_REVIEW_REQUIRED");
        }
        return paid.stream().map(p -> p.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    boolean isCancellable(Tournament tournament) {
        return tournament.getModalidad() == Modalidad.DOBLES && startsAt(tournament).isAfter(now())
                && Boolean.TRUE.equals(tournament.getIsActive())
                && tournament.getEstado() != TournamentStatus.CANCELADO
                && tournament.getEstado() != TournamentStatus.FINALIZADO;
    }

    void saveCancellation(TournamentRegistration row, Long playerId, Cancellation quote) {
        jdbc.update("""
                INSERT INTO pair_cancellations_db
                    (booking_id, tournament_id, cancelled_by, cancelled_at, terms_version, currency,
                     obligation, already_paid, remaining_due, refundable, participants)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, row.getPairBookingId(), row.getTournament().getId(), playerId, now(),
                row.getPairTermsVersion(), quote.currency(), quote.obligation(), quote.alreadyPaid(),
                quote.remainingDue(), quote.refundable(), PairSettlementService.names(members(row)));
    }

    List<TournamentRegistration> members(TournamentRegistration row) {
        List<TournamentRegistration> rows = registrations.findByTournamentId(row.getTournament().getId());
        return rows.stream().filter(r -> Boolean.TRUE.equals(r.getIsActive()))
                .filter(r -> Objects.equals(r.getId(), row.getId())
                        || (row.getPairBookingId() != null
                                ? row.getPairBookingId().equals(r.getPairBookingId())
                                : r.getPairBookingId() == null && row.getPartner() != null
                                        && row.getPartner().getId().equals(r.getPlayer().getId())))
                .toList();
    }

    Tournament lock(Long tournamentId) {
        return tournaments.lockById(tournamentId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    TournamentRegistration activeRegistration(Long tournamentId, Long playerId) {
        return registrations.findByTournamentIdAndPlayerIdAndIsActiveTrue(tournamentId, playerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    static void requireToken(String expected, String actual) {
        if (!expected.equals(actual)) throw conflict("PAIR_TERMS_CHANGED");
    }

    private boolean canAccept(Tournament tournament, TournamentRegistration row, Long playerId) {
        if (!canOffer(tournament) || row.getPairBookingId() == null || !Objects.equals(row.getPairBookedBy(), playerId)
                || row.getStatus() != RegistrationStatus.CONFIRMED && row.getStatus() != RegistrationStatus.WAITLIST) {
            return false;
        }
        var rows = members(row);
        return rows.size() == 2 && rows.stream().noneMatch(PairTermsService::accepted)
                && !hasUnassignedPayments(rows);
    }

    boolean hasUnassignedPayments(List<TournamentRegistration> rows) {
        return rows.stream().flatMap(member -> payments.findByRegistrationId(member.getId()).stream())
                .anyMatch(payment -> Boolean.TRUE.equals(payment.getIsActive()) && payment.getPairBookingId() == null
                        && (payment.getStatus() == PaymentStatus.PAID || payment.getStatus() == PaymentStatus.PROCESSING
                            || payment.getStatus() == PaymentStatus.PENDING));
    }

    private Offer offerFor(Tournament tournament) {
        LocalDateTime start = startsAt(tournament);
        LocalDateTime deadline = start.minusHours(3);
        BigDecimal price = tournament.getPrecio();
        return offer(tournament, start, deadline, tournament.getPairTermsVersion(), price, tournament.getMoneda(),
                PairCancellationPolicy.LATE_FRACTION);
    }

    Offer acceptedOffer(Tournament tournament, TournamentRegistration row) {
        return offer(tournament, startsAt(tournament), row.getPairTermsFreeUntil(), row.getPairTermsVersion(),
                row.getPairTermsPricePerPlayer(), row.getPairTermsCurrency(), row.getPairTermsLateFraction());
    }

    private Offer offer(Tournament tournament, LocalDateTime start, LocalDateTime deadline, int version,
                        BigDecimal price, String currency, BigDecimal fraction) {
        String category = Optional.ofNullable(tournament.getCategoriaNivel()).map(Enum::name).orElse(null);
        var quote = PairCancellationPolicy.quoteFromTerms(deadline, deadline.plusSeconds(1), price,
                fraction, BigDecimal.ZERO, false);
        String token = token(tournament.getId(), version, tournament.getNombre(), start, deadline, price, currency,
                category, tournament.getFaqUrl(), fraction);
        return new Offer(token, version, tournament.getNombre(), offset(start), offset(deadline), price,
                quote.pairPrice(), quote.obligation(), currency, category, tournament.getFaqUrl());
    }

    static LocalDateTime now() { return LocalDateTime.now(EVENT_ZONE); }

    static LocalDateTime startsAt(Tournament tournament) {
        return tournament.getFechaInicio().atTime(tournament.getHoraInicio());
    }

    private static OffsetDateTime offset(LocalDateTime time) {
        return time.atZone(EVENT_ZONE).toOffsetDateTime();
    }

    private static String token(Object... values) {
        return UUID.nameUUIDFromBytes(java.util.Arrays.deepToString(values).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static ResponseStatusException conflict(String code) {
        return new ResponseStatusException(HttpStatus.CONFLICT, code);
    }
}
