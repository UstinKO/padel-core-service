package com.padle.core.padelcoreservice.preview;

import com.padle.core.padelcoreservice.rating.EloPilotProperties;
import com.padle.core.padelcoreservice.rating.EloPilotSnapshot;
import com.padle.core.padelcoreservice.service.KingOfCourtService;
import com.padle.core.padelcoreservice.service.americano.AmericanoService;
import com.padle.core.padelcoreservice.service.americano.TeamPlayoffService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Чтение результатов без тестовых фикстур, записей рейтинга и таблиц исправлений preview. */
@Service
@Profile("!preview")
@ConditionalOnProperty(name = {"app.rating.pilot.enabled", "app.rating.pilot.auto-refresh"}, havingValue = "true")
public class PilotEloRefresh {
    private final EloRefreshSource source;
    private final PreviewEloLiveEvents live;
    private final EloPilotSnapshot initial;
    private final PreviewEloReplay replay;
    private final boolean formatWeighted;
    private final JdbcClient jdbc;

    public PilotEloRefresh(ObjectMapper mapper, EloPilotProperties properties, Path historyPath, JdbcClient jdbc,
                           KingOfCourtService king, AmericanoService americano, TeamPlayoffService playoff)
            throws IOException {
        this(mapper, properties, historyPath, jdbc, king, americano, playoff, false);
    }

    @Autowired
    public PilotEloRefresh(ObjectMapper mapper, EloPilotProperties properties,
                           @Value("${app.rating.pilot.history}") Path historyPath, JdbcClient jdbc,
                           KingOfCourtService king, AmericanoService americano, TeamPlayoffService playoff,
                           @Value("${app.rating.pilot.format-weighted:false}") boolean formatWeighted)
            throws IOException {
        try (var input = Files.newInputStream(historyPath)) {
            source = mapper.readValue(input, EloRefreshSource.class);
        }
        try (var input = Files.newInputStream(properties.snapshot())) {
            initial = mapper.readValue(input, EloPilotSnapshot.class);
        }
        var originalReplay = new PreviewEloReplay(initial.startStep(), initial.formatWeighted());
        var baseline = originalReplay.replay(initial.snapshot().asOf(), source.history().names(), source.history().events());
        boolean matches = baseline.equals(initial.snapshot()) || PreviewEloReplay
                .forLegacySnapshot(initial.startStep(), initial.formatWeighted())
                .replay(initial.snapshot().asOf(), source.history().names(), source.history().events())
                .equals(initial.snapshot());
        if (!matches || !source.history().aliases().equals(initial.aliases())
                || source.liveAfter().isAfter(initial.snapshot().asOf())) {
            throw new IllegalArgumentException("История не воспроизводит проверенный срез пилота Elo");
        }
        this.formatWeighted = formatWeighted || initial.formatWeighted();
        this.jdbc = jdbc;
        replay = new PreviewEloReplay(initial.startStep(), this.formatWeighted);
        live = new PreviewEloLiveEvents(jdbc, king, americano, playoff);
    }

    public boolean isFormatWeighted() {
        return formatWeighted;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PreviewEloRanking.Snapshot calculate() {
        var testIds = Set.copyOf(jdbc.sql("SELECT id FROM tournaments_db WHERE is_test_tournament = true")
                .query(Long.class).list());
        var excluded = new HashSet<>(source.excludedEvents());
        testIds.forEach(id -> excluded.add("S-" + id));
        var history = excludeTestTournaments(correctJuneQuarterfinal(source.merge(
                live.load(source.liveAfter(), source.managedIds(), excluded))), testIds);
        var asOf = history.events().stream().map(PreviewEloReplay.Event::day).max(LocalDate::compareTo)
                .filter(day -> day.isAfter(initial.snapshot().asOf())).orElse(initial.snapshot().asOf());
        return replay.replay(asOf, history.names(), history.events());
    }

    static PreviewEloReplay.History excludeTestTournaments(PreviewEloReplay.History history, Set<Long> testIds) {
        var keys = testIds.stream().map(id -> "S-" + id).collect(java.util.stream.Collectors.toSet());
        return new PreviewEloReplay.History(history.names(), history.events().stream()
                .filter(event -> !keys.contains(event.key())).toList(), history.aliases());
    }

    static PreviewEloReplay.History correctJuneQuarterfinal(PreviewEloReplay.History history) {
        var events = history.events().stream().map(event -> {
            if (!event.key().equals("B-1297006686")) return event;
            var games = event.games().stream().map(game -> {
                if (!game.key().equals("B-1297006686:N4:N5")) return game;
                if (!game.ids().equals(List.of(166L, 461L, 225L, 221L))) {
                    throw new IllegalStateException("Unexpected lineup in corrected June quarterfinal");
                }
                return new PreviewEloReplay.Game(game.key(), game.batch(), List.of(166L, 407L, 225L, 221L),
                        game.left(), game.right(), game.verifiedFinal());
            }).toList();
            return new PreviewEloReplay.Event(event.key(), event.day(), event.category(), games,
                    event.champions(), event.runnersUp(), event.title(), event.format(), event.resultsUrl());
        }).toList();
        return new PreviewEloReplay.History(history.names(), events, history.aliases());
    }

}
