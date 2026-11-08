package com.padle.core.padelcoreservice.service.conduct;

import com.padle.core.padelcoreservice.dto.americano.AmericanoMatchResultDto;
import com.padle.core.padelcoreservice.exception.InvalidStateException;
import com.padle.core.padelcoreservice.exception.ResourceNotFoundException;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.TournamentKingOfCourt;
import com.padle.core.padelcoreservice.model.americano.AmericanoMatch;
import com.padle.core.padelcoreservice.model.americano.AmericanoRound;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.service.TournamentAccessService;
import com.padle.core.padelcoreservice.service.conduct.ConductChange.Action;
import com.padle.core.padelcoreservice.service.conduct.ConductChange.Subject;
import com.padle.core.padelcoreservice.service.conduct.ConductSnapshotStore.Target;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class TournamentConductHistory {

    private static final RowMapper<Version> VERSION_MAPPER = (rs, n) -> new Version(
            rs.getLong("id"), rs.getObject("recorded_at", OffsetDateTime.class), rs.getString("actor_name"),
            Action.valueOf(rs.getString("action")), rs.getObject("subject_id", Long.class),
            rs.getObject("round_number", Integer.class), rs.getObject("court_number", Integer.class),
            rs.getString("stage"), rs.getObject("restored_from", Long.class));
    private static final String VERSION_COLUMNS =
            "id, recorded_at, actor_name, action, subject_id, round_number, court_number, stage, restored_from";

    private final JdbcClient jdbc;
    private final EntityManager entityManager;
    private final ConductSnapshotStore snapshots;
    private final TournamentAccessService access;

    Object record(ConductChange change, Object[] args, Supplier<Object> operation) {
        Subject subject = change.subject();
        long subjectId = subject == Subject.AMERICANO_RESULT
                ? ((AmericanoMatchResultDto) args[change.argument()]).getMatchId()
                : (Long) args[change.argument()];
        long tournamentId = snapshots.target(subject, subjectId).tournamentId();
        TournamentType type = snapshots.lockTournament(tournamentId);
        entityManager.flush();
        Target target = snapshots.target(subject, subjectId);
        if (target.tournamentId() != tournamentId) {
            throw new InvalidStateException("Tournament action target changed");
        }

        Owner owner = currentOwner(args);
        UUID key = requestKey();
        if (owner != null && key != null && alreadyRecorded(tournamentId, owner.getId(), key)) {
            markResponse("X-Conduct-Replayed");
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tournament action already saved");
        }

        long currentVersion = revision(tournamentId);
        checkExpectedVersion(currentVersion, change.value());
        String before = snapshots.capture(tournamentId, type);
        if (currentVersion == 0) {
            save(tournamentId, null, Action.BASELINE, null, target, before, null, null);
        }
        Action checkpoint = change.value().checkpoint();
        if (checkpoint != null) {
            save(tournamentId, owner, checkpoint, subjectId, withPosition(target, type), before, null, null);
        }
        if (change.value() == Action.ROSTER && type != TournamentType.KING_OF_COURT) {
            snapshots.keepTeamDetails(tournamentId);
        }

        Object result = operation.get();
        entityManager.flush();
        String after = snapshots.capture(tournamentId, type);
        if (!before.equals(after) || key != null || checkpoint != null) {
            if (result instanceof TournamentKingOfCourt king) {
                subjectId = king.getId();
                target = snapshots.target(Subject.KING, subjectId);
            } else if (result instanceof AmericanoMatch match) {
                subjectId = match.getId();
                target = snapshots.target(Subject.AMERICANO_MATCH, subjectId);
            } else if (result instanceof AmericanoRound round) {
                subjectId = round.getId();
                target = snapshots.target(Subject.AMERICANO_ROUND, subjectId);
            } else if (result instanceof AdditionalPointsStore.Entry entry) {
                subjectId = entry.playerId();
                target = entry.roundNumber() == 0
                        ? new Target(tournamentId, null, null, "BEFORE_FIRST_ROUND")
                        : new Target(tournamentId, entry.roundNumber(), null, null);
            } else if (change.value() == Action.NEXT_ROUND) {
                target = snapshots.target(Subject.KING, subjectId);
            } else if (change.value() == Action.RESULT && subject == Subject.KING_ROUND) {
                target = new Target(tournamentId, target.roundNumber(), (Integer) args[1], null);
            }
            long version = save(tournamentId, owner, change.value(), subjectId, withPosition(target, type), after, null, key);
            if (change.value().isPointsChange()) {
                responseHeader("X-Conduct-Version", Long.toString(version));
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<Version> list(long tournamentId, Owner owner) {
        access.assertCanManageTournament(owner, tournamentId);
        return jdbc.sql("SELECT " + VERSION_COLUMNS + """
                 FROM tournament_conduct_history WHERE tournament_id = :id ORDER BY id DESC
                """).param("id", tournamentId).query(VERSION_MAPPER).list();
    }

    @Transactional(readOnly = true)
    public Preview preview(long tournamentId, long versionId, Owner owner) {
        access.assertCanManageTournament(owner, tournamentId);
        StoredVersion chosen = find(tournamentId, versionId);
        TournamentType type = snapshots.type(tournamentId);
        boolean activeRosterChanged = !snapshots.activeRoster(chosen.snapshot(), type)
                .equals(snapshots.activeRoster(snapshots.capture(tournamentId, type), type));
        var differences = snapshots.registrationDifferences(chosen.registrations(), snapshots.registrations(tournamentId));
        return new Preview(chosen.version(), revision(tournamentId), differences,
                activeRosterChanged, snapshots.cancelled(tournamentId));
    }

    @Transactional
    public void restore(long tournamentId, long versionId, long expectedLatest, Owner owner) {
        TournamentType type = snapshots.lockTournament(tournamentId);
        access.assertCanManageTournament(owner, tournamentId);
        if (expectedLatest != revision(tournamentId)) {
            throw new InvalidStateException("conduct.history.stale");
        }
        if (snapshots.cancelled(tournamentId)) {
            throw new InvalidStateException("conduct.history.cancelled");
        }
        snapshots.lockRegistrations(tournamentId);
        StoredVersion chosen = find(tournamentId, versionId);
        if (!chosen.registrations().equals(snapshots.registrations(tournamentId))) {
            throw new InvalidStateException("conduct.history.roster_changed");
        }
        if (chosen.schemaVersion() != 1) {
            throw new InvalidStateException("conduct.history.unsupported");
        }
        save(tournamentId, owner, Action.CHECKPOINT_RESTORE, null, snapshots.position(tournamentId, type),
                snapshots.capture(tournamentId, type), null, null);
        snapshots.restore(tournamentId, type, chosen.snapshot());
        entityManager.clear();
        save(tournamentId, owner, Action.RESTORE, null, snapshots.position(tournamentId, type),
                snapshots.capture(tournamentId, type), versionId, null);
    }

    public long revision(long tournamentId) {
        return jdbc.sql("SELECT COALESCE(max(id), 0) FROM tournament_conduct_history WHERE tournament_id = :id")
                .param("id", tournamentId).query(Long.class).single();
    }

    private StoredVersion find(long tournamentId, long versionId) {
        return jdbc.sql("SELECT " + VERSION_COLUMNS + """
                 , snapshot, registrations, schema_version FROM tournament_conduct_history
                 WHERE tournament_id = :id AND id = :version
                """).param("id", tournamentId).param("version", versionId)
                .query((rs, n) -> new StoredVersion(VERSION_MAPPER.mapRow(rs, n),
                        rs.getString("snapshot"), rs.getString("registrations"), rs.getInt("schema_version")))
                .optional().orElseThrow(() -> new ResourceNotFoundException("Tournament version not found"));
    }

    private boolean alreadyRecorded(long tournamentId, long ownerId, UUID key) {
        return jdbc.sql("""
                SELECT EXISTS (SELECT 1 FROM tournament_conduct_history
                    WHERE tournament_id = :id AND actor_id = :actor AND request_key = :key)
                """).param("id", tournamentId).param("actor", ownerId).param("key", key)
                .query(Boolean.class).single();
    }

    private long save(long tournamentId, Owner owner, Action action, Long subjectId, Target target,
                      String snapshot, Long restoredFrom, UUID key) {
        return jdbc.sql("""
                INSERT INTO tournament_conduct_history
                    (tournament_id, actor_id, actor_name, action, subject_id, round_number, court_number,
                     stage, restored_from, request_key, snapshot, registrations)
                VALUES (:id, :actor, :name, :action, :subject, :round, :court, :stage, :source,
                        :key, CAST(:snapshot AS jsonb), CAST(:registrations AS jsonb))
                RETURNING id
                """)
                .param("id", tournamentId).param("actor", owner == null ? null : owner.getId())
                .param("name", owner == null ? null : actorName(owner))
                .param("action", action.name()).param("subject", subjectId)
                .param("round", target.roundNumber()).param("court", target.courtNumber())
                .param("stage", target.stage()).param("source", restoredFrom)
                .param("key", key).param("snapshot", snapshot)
                .param("registrations", snapshots.registrations(tournamentId)).query(Long.class).single();
    }

    private Owner currentOwner(Object[] args) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Owner owner) {
            return owner;
        }
        return Stream.of(args).filter(Owner.class::isInstance).map(Owner.class::cast).findFirst().orElse(null);
    }

    private String actorName(Owner owner) {
        String name = Stream.of(owner.getFirstName(), owner.getLastName())
                .filter(Objects::nonNull).filter(part -> !part.isBlank())
                .reduce((first, last) -> first + " " + last).orElse("#" + owner.getId());
        return name.substring(0, Math.min(name.length(), 255));
    }

    private Target withPosition(Target target, TournamentType type) {
        if (target.roundNumber() != null || target.stage() != null) {
            return target;
        }
        return snapshots.position(target.tournamentId(), type);
    }

    private void checkExpectedVersion(long currentVersion, Action action) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            String expected = attributes.getRequest().getParameter("expectedConductVersion");
            if (expected != null && !expected.equals(Long.toString(currentVersion))) {
                markResponse("X-Conduct-Stale");
                if (action.isPointsChange()) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "conduct.history.stale");
                }
                throw new InvalidStateException("conduct.history.stale");
            }
        }
    }

    private void markResponse(String header) {
        responseHeader(header, "true");
    }

    private void responseHeader(String header, String value) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                && attributes.getResponse() != null) {
            attributes.getResponse().setHeader(header, value);
        }
    }

    private UUID requestKey() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        String key = attributes.getRequest().getHeader("Idempotency-Key");
        if (key == null) {
            key = attributes.getRequest().getParameter("conductRequestKey");
        }
        if (key == null || key.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(key);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid idempotency key");
        }
    }

    public record Version(long id, OffsetDateTime recordedAt, String actorName, Action action, Long subjectId,
                          Integer roundNumber, Integer courtNumber, String stage, Long restoredFrom) {}

    public record Preview(Version version, long latestVersion, List<RegistrationDifference> registrationDifferences,
                          boolean activeRosterChanged, boolean cancelled) {
        public boolean registrationsChanged() {
            return !registrationDifferences.isEmpty();
        }
    }

    public record RegistrationDifference(long registrationId, String playerName, String oldStatus, String currentStatus) {}

    private record StoredVersion(Version version, String snapshot, String registrations, int schemaVersion) {}
}
