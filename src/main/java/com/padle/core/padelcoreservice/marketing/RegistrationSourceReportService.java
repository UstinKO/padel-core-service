package com.padle.core.padelcoreservice.marketing;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class RegistrationSourceReportService {
    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private final NamedParameterJdbcTemplate jdbc;

    public record Filter(LocalDate from, LocalDate to, String model, String source, String placement,
                         String medium, String campaign, String content, String term,
                         Long tournamentId, Long clubId, int page, int pageSize) { }

    public RegistrationSourceReport report(Filter filter) {
        var parameters = parameters(filter);
        var totals = jdbc.queryForObject(CTE + """
                SELECT COUNT(*) FILTER (WHERE unit = 'participant') participants,
                  COUNT(*) FILTER (WHERE unit = 'first') first_registrations,
                  COUNT(*) FILTER (WHERE unit = 'pair') pairs,
                  COUNT(*) FILTER (WHERE unit = 'participant' AND bucket = 'confirmed') confirmed,
                  COUNT(*) FILTER (WHERE unit = 'participant' AND bucket = 'waitlist') waitlist,
                  COUNT(*) FILTER (WHERE unit = 'participant' AND bucket = 'pending') pending,
                  COUNT(*) FILTER (WHERE unit = 'participant' AND bucket = 'cancelled') cancelled,
                  COUNT(*) FILTER (WHERE unit = 'first' AND bucket = 'confirmed') first_confirmed,
                  COUNT(*) FILTER (WHERE unit = 'first' AND bucket = 'waitlist') first_waitlist,
                  COUNT(*) FILTER (WHERE unit = 'first' AND bucket = 'pending') first_pending,
                  COUNT(*) FILTER (WHERE unit = 'first' AND bucket = 'cancelled') first_cancelled,
                  COUNT(*) FILTER (WHERE unit = 'pair' AND bucket = 'confirmed') pair_confirmed,
                  COUNT(*) FILTER (WHERE unit = 'pair' AND bucket = 'waitlist') pair_waitlist,
                  COUNT(*) FILTER (WHERE unit = 'pair' AND bucket = 'pending') pair_pending,
                  COUNT(*) FILTER (WHERE unit = 'pair' AND bucket = 'cancelled') pair_cancelled,
                  COUNT(*) FILTER (WHERE unit = 'participant' AND measured) measured,
                  COUNT(*) FILTER (WHERE unit = 'first' AND measured) measured_first,
                  COUNT(*) FILTER (WHERE unit = 'pair' AND measured) measured_pairs
                FROM filtered
                """, parameters, (rs, row) -> new Totals(counts(rs), rs.getLong("measured"), rs.getLong("measured_first"), rs.getLong("measured_pairs")));
        Long groups = jdbc.queryForObject(CTE + "SELECT COUNT(*) FROM (SELECT DISTINCT " + DIMENSIONS
                + " FROM filtered) g", parameters, Long.class);
        List<RegistrationSourceReport.Row> rows = jdbc.query(CTE + "SELECT " + DIMENSIONS + """
                ,COUNT(*) FILTER (WHERE unit = 'participant') participants,
                COUNT(*) FILTER (WHERE unit = 'first') first_registrations,
                COUNT(*) FILTER (WHERE unit = 'pair') pairs,
                COUNT(*) FILTER (WHERE unit = 'participant' AND bucket = 'confirmed') confirmed,
                COUNT(*) FILTER (WHERE unit = 'participant' AND bucket = 'waitlist') waitlist,
                COUNT(*) FILTER (WHERE unit = 'participant' AND bucket = 'pending') pending,
                COUNT(*) FILTER (WHERE unit = 'participant' AND bucket = 'cancelled') cancelled,
                COUNT(*) FILTER (WHERE unit = 'first' AND bucket = 'confirmed') first_confirmed,
                COUNT(*) FILTER (WHERE unit = 'first' AND bucket = 'waitlist') first_waitlist,
                COUNT(*) FILTER (WHERE unit = 'first' AND bucket = 'pending') first_pending,
                COUNT(*) FILTER (WHERE unit = 'first' AND bucket = 'cancelled') first_cancelled,
                COUNT(*) FILTER (WHERE unit = 'pair' AND bucket = 'confirmed') pair_confirmed,
                COUNT(*) FILTER (WHERE unit = 'pair' AND bucket = 'waitlist') pair_waitlist,
                COUNT(*) FILTER (WHERE unit = 'pair' AND bucket = 'pending') pair_pending,
                COUNT(*) FILTER (WHERE unit = 'pair' AND bucket = 'cancelled') pair_cancelled
                FROM filtered GROUP BY
                """ + DIMENSIONS + " ORDER BY participants DESC, source, tournament_id, medium, campaign, content, term, placement LIMIT :limit OFFSET :offset",
                parameters, (rs, row) -> new RegistrationSourceReport.Row(rs.getString("source"),
                        rs.getString("placement"), rs.getString("medium"), rs.getString("campaign"),
                        rs.getString("content"), rs.getString("term"), rs.getLong("tournament_id"),
                        rs.getString("tournament"), rs.getLong("club_id"), counts(rs)));
        return new RegistrationSourceReport((LocalDate) parameters.getValue("fromDate"),
                (LocalDate) parameters.getValue("toDate"), Instant.now(), filter.model(), filter.page(),
                filter.pageSize(), groups == null ? 0 : groups, totals.counts(), totals.measured(), totals.measuredFirst(), totals.measuredPairs(), rows);
    }

    private record Totals(RegistrationSourceReport.Counts counts, long measured, long measuredFirst, long measuredPairs) { }

    private static RegistrationSourceReport.Counts counts(ResultSet rs) throws SQLException {
        return new RegistrationSourceReport.Counts(rs.getLong("participants"), rs.getLong("first_registrations"),
                rs.getLong("pairs"), rs.getLong("confirmed"), rs.getLong("waitlist"),
                rs.getLong("pending"), rs.getLong("cancelled"),
                rs.getLong("first_confirmed"), rs.getLong("first_waitlist"), rs.getLong("first_pending"), rs.getLong("first_cancelled"), rs.getLong("pair_confirmed"), rs.getLong("pair_waitlist"), rs.getLong("pair_pending"), rs.getLong("pair_cancelled"));
    }

    private MapSqlParameterSource parameters(Filter filter) {
        LocalDate today = LocalDate.now(ZONE);
        LocalDate from = Optional.ofNullable(filter.from()).orElse(today.withDayOfMonth(1));
        LocalDate to = Optional.ofNullable(filter.to()).orElse(today.withDayOfMonth(today.lengthOfMonth()));
        if (Optional.ofNullable(filter.tournamentId()).filter(id -> id < 1).isPresent()
                || Optional.ofNullable(filter.clubId()).filter(id -> id < 1).isPresent()
                || to.isBefore(from) || ChronoUnit.DAYS.between(from, to) >= 366
                || filter.page() < 0 || filter.page() > 100000 || filter.pageSize() < 1 || filter.pageSize() > 100
                || !Set.of("first_known", "last_non_direct").contains(filter.model())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_attribution_filter");
        }
        var parameters = new MapSqlParameterSource().addValue("fromDate", from).addValue("toDate", to)
                .addValue("from", Timestamp.from(from.atStartOfDay(ZONE).toInstant()))
                .addValue("until", Timestamp.from(to.plusDays(1).atStartOfDay(ZONE).toInstant()))
                .addValue("legacyZone", ZoneId.systemDefault().getId()).addValue("model", filter.model()).addValue("tournament", Optional.ofNullable(filter.tournamentId()).orElse(0L))
                .addValue("club", Optional.ofNullable(filter.clubId()).orElse(0L))
                .addValue("limit", filter.pageSize()).addValue("offset", (long) filter.page() * filter.pageSize());
        List<String> names = List.of("source", "placement", "medium", "campaign", "content", "term");
        List<String> values = List.of(filter.source(), filter.placement(), filter.medium(), filter.campaign(), filter.content(), filter.term());
        IntStream.range(0, names.size()).forEach(index -> {
            String value = values.get(index);
            if (!value.isEmpty() && !value.matches("[A-Za-z0-9_-]{1,100}")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_attribution_filter");
            }
            parameters.addValue(names.get(index), value);
        });
        return parameters;
    }

    private static final String DIMENSIONS = "source, placement, medium, campaign, content, term, tournament_id, tournament, club_id";
    private static final String CTE = """
            WITH base AS (
              SELECT r.id, r.player_id, r.partner_id, r.main_player_id, r.is_double_registration,
                r.tournament_id, t.nombre tournament, t.club_id,
                COALESCE(mr.attribution_recorded_at, r.registration_date AT TIME ZONE :legacyZone) recorded,
                mr.attribution_recorded_at IS NOT NULL measured,
                CASE WHEN mr.attribution_expires_at >= CURRENT_TIMESTAMP THEN
                  CASE WHEN :model = 'first_known' THEN mr.acquisition_touch ELSE mr.registration_touch END
                END touch,
                CASE WHEN NOT r.is_active OR r.status IN ('CANCELLED','SOLO_EXPIRED') THEN 'cancelled'
                  WHEN r.status IN ('CONFIRMED','PARTICIPATED','PAIR_REGISTERED') THEN 'confirmed'
                  WHEN r.status IN ('WAITLIST','WAITLIST_INVITED') THEN 'waitlist' ELSE 'pending' END bucket
              FROM tournament_registrations_db r JOIN tournaments_db t ON t.id = r.tournament_id
              LEFT JOIN marketing_registrations_db mr ON mr.registration_id = r.id
            ), ranked AS (
              SELECT *, ROW_NUMBER() OVER (PARTITION BY player_id ORDER BY recorded, id) first_position FROM base
            ), units AS (
              SELECT *, 'participant' unit FROM ranked
              UNION ALL SELECT *, 'first' unit FROM ranked WHERE first_position = 1
              UNION ALL
              SELECT a.id, a.player_id, a.partner_id, a.main_player_id, a.is_double_registration,
                a.tournament_id, a.tournament, a.club_id, a.recorded, a.measured AND b.measured,
                CASE WHEN (a.touch::jsonb->'utm') IS NOT DISTINCT FROM (b.touch::jsonb->'utm') THEN a.touch END,
                CASE WHEN a.bucket = b.bucket THEN a.bucket ELSE 'pending' END, a.first_position, 'pair'
              FROM ranked a JOIN ranked b ON a.tournament_id = b.tournament_id
                AND a.partner_id = b.player_id AND b.partner_id = a.player_id
                AND a.is_double_registration AND b.is_double_registration
                AND a.main_player_id = b.main_player_id
                AND a.main_player_id = a.player_id
            ), dimensions AS (
              SELECT *, COALESCE(NULLIF(touch::jsonb->'utm'->>'source', ''), 'unknown') source,
                COALESCE(touch::jsonb->'utm'->>'medium', '') medium,
                COALESCE(touch::jsonb->'utm'->>'campaign', '') campaign,
                COALESCE(touch::jsonb->'utm'->>'content', '') content,
                COALESCE(touch::jsonb->'utm'->>'term', '') term
              FROM units
            ), placements AS (
              SELECT *, CASE WHEN medium = 'qr' THEN 'qr'
                WHEN campaign IN ('instagram_profile','instagram_profile_ru','threads_profile') THEN 'bio'
                WHEN campaign = 'instagram_stories' THEN 'story'
                WHEN campaign = 'telegram_posts' THEN 'post'
                WHEN campaign = 'telegram_calendar' THEN 'calendar'
                WHEN campaign = 'whatsapp_group' THEN 'group'
                WHEN medium IN ('paid_social','cpc','ppc') THEN 'paid'
                ELSE 'unknown' END placement FROM dimensions
            ), filtered AS (
              SELECT * FROM placements WHERE recorded >= :from AND recorded < :until
                AND (:source = '' OR source = :source) AND (:placement = '' OR placement = :placement)
                AND (:medium = '' OR medium = :medium) AND (:campaign = '' OR campaign = :campaign)
                AND (:content = '' OR content = :content) AND (:term = '' OR term = :term)
                AND (:tournament = 0 OR tournament_id = :tournament) AND (:club = 0 OR club_id = :club)
            )
            """;
}
