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
import java.util.Set;

/** Чтение результатов без тестовых фикстур, записей рейтинга и таблиц исправлений preview. */
@Service
@Profile("!preview")
@ConditionalOnProperty(name = {"app.rating.pilot.enabled", "app.rating.pilot.auto-refresh"}, havingValue = "true")
public class PilotEloRefresh {
    // Tournament 189 was a production QA fixture, not a played competition.
    private static final Set<String> VERIFIED_TEST_EVENTS = Set.of("S-189");
    private final EloRefreshSource source;
    private final PreviewEloLiveEvents live;
    private final EloPilotSnapshot initial;
    private final PreviewEloReplay replay;
    private final boolean formatWeighted;

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
        if (!baseline.equals(initial.snapshot()) || !source.history().aliases().equals(initial.aliases())
                || source.liveAfter().isAfter(initial.snapshot().asOf())) {
            throw new IllegalArgumentException("История не воспроизводит проверенный срез пилота Elo");
        }
        this.formatWeighted = formatWeighted || initial.formatWeighted();
        replay = new PreviewEloReplay(initial.startStep(), this.formatWeighted);
        live = new PreviewEloLiveEvents(jdbc, king, americano, playoff);
    }

    public boolean isFormatWeighted() {
        return formatWeighted;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PreviewEloRanking.Snapshot calculate() {
        var history = excludeDisputedMatch(source.merge(live.load(source.liveAfter(), source.managedIds(), excludedEvents(source.excludedEvents()))));
        var asOf = history.events().stream().map(PreviewEloReplay.Event::day).max(LocalDate::compareTo)
                .filter(day -> day.isAfter(initial.snapshot().asOf())).orElse(initial.snapshot().asOf());
        return replay.replay(asOf, history.names(), history.events());
    }

    static PreviewEloReplay.History excludeDisputedMatch(PreviewEloReplay.History history) {
        var events = history.events().stream().map(event -> {
            if (!event.key().equals("B-1297006686")) return event;
            var games = event.games().stream()
                    .filter(game -> !game.key().equals("B-1297006686:N4:N5")).toList();
            return new PreviewEloReplay.Event(event.key(), event.day(), event.category(), games,
                    event.champions(), event.runnersUp(), event.title(), event.format(), event.resultsUrl());
        }).toList();
        return new PreviewEloReplay.History(history.names(), events, history.aliases());
    }

    static Set<String> excludedEvents(Set<String> configured) {
        var excluded = new HashSet<>(configured);
        excluded.addAll(VERIFIED_TEST_EVENTS);
        return Set.copyOf(excluded);
    }
}
