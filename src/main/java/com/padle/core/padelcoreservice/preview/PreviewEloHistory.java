package com.padle.core.padelcoreservice.preview;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Profile("preview")
@ConditionalOnProperty("app.preview.elo-history")
public class PreviewEloHistory {
    private final PreviewEloReplay.History history;
    private final Map<String, PreviewEloReplay.Game> historicalGames;
    private final JdbcClient jdbc;
    private final PreviewEloLiveEvents liveEvents;
    private final PreviewEloReplay replay;
    private final boolean includeTestResults;
    private final int startStep;

    public PreviewEloHistory(ObjectMapper mapper, @Value("${app.preview.elo-history}") Path path, JdbcClient jdbc,
                             PreviewEloLiveEvents liveEvents, PreviewConfiguration.DatabaseIdentity identity,
                             @Value("${app.preview.elo-start-step:0}") int startStep,
                             @Value("${app.preview.elo-include-test-results:true}") boolean includeTestResults)
            throws IOException {
        this.jdbc = jdbc;
        this.liveEvents = liveEvents;
        this.startStep = startStep;
        this.includeTestResults = includeTestResults;
        this.replay = new PreviewEloReplay(startStep);
        try (var input = Files.newInputStream(path)) {
            history = mapper.readValue(input, PreviewEloReplay.History.class);
        }
        historicalGames = history.events().stream().flatMap(event -> event.games().stream())
                .collect(Collectors.toUnmodifiableMap(PreviewEloReplay.Game::key, Function.identity()));
        if (historicalGames.isEmpty()) {
            throw new IllegalArgumentException("История Elo не содержит матчей");
        }
        jdbc.sql("""
                CREATE TABLE IF NOT EXISTS ui_preview.elo_score_corrections (
                    match_key text PRIMARY KEY,
                    left_score integer NOT NULL CHECK (left_score >= 0),
                    right_score integer NOT NULL CHECK (right_score >= 0),
                    reason text NOT NULL,
                    updated_at timestamptz NOT NULL DEFAULT now()
                )
                """).update();
    }

    private record Score(int left, int right) { }

    int startStep() {
        return startStep;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PreviewEloRanking.Snapshot calculate(LocalDate cutoff) {
        if (!includeTestResults) {
            return replay.replay(cutoff, history.names(), history.events());
        }
        var corrections = jdbc.sql("SELECT match_key,left_score,right_score FROM ui_preview.elo_score_corrections")
                .query((row, index) -> Map.entry(row.getString(1), new Score(row.getInt(2), row.getInt(3))))
                .list().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        var live = liveEvents.load(cutoff);
        var events = new ArrayList<PreviewEloReplay.Event>(history.events().size() + live.events().size());
        history.events().forEach(event -> events.add(corrected(event, corrections)));
        events.addAll(live.events());
        var names = new HashMap<>(live.names());
        names.putAll(history.names());
        return replay.replay(cutoff, names, events);
    }

    private PreviewEloReplay.Event corrected(PreviewEloReplay.Event event, Map<String, Score> corrections) {
        var games = event.games().stream().map(game -> {
            var score = corrections.get(game.key());
            return score == null ? game : game.withScore(score.left(), score.right());
        }).toList();
        return new PreviewEloReplay.Event(event.key(), event.day(), event.category(), games, event.champions(),
                event.runnersUp());
    }

    @Transactional
    public void correctScore(String matchKey, int left, int right, String reason) {
        var game = historicalGames.get(matchKey);
        if (game == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Матч отсутствует в проверенной истории");
        }
        try {
            game.withScore(left, right);
        } catch (IllegalArgumentException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Недопустимый счёт");
        }
        jdbc.sql("""
                INSERT INTO ui_preview.elo_score_corrections(match_key,left_score,right_score,reason)
                VALUES (:key,:left,:right,:reason)
                ON CONFLICT (match_key) DO UPDATE
                  SET left_score=excluded.left_score,right_score=excluded.right_score,
                      reason=excluded.reason,updated_at=now()
                """).param("key", matchKey).param("left", left).param("right", right)
                .param("reason", reason).update();
    }
}
