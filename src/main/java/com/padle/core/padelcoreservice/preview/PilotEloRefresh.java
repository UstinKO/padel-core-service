package com.padle.core.padelcoreservice.preview;

import com.padle.core.padelcoreservice.rating.EloPilotProperties;
import com.padle.core.padelcoreservice.rating.EloPilotSnapshot;
import com.padle.core.padelcoreservice.service.KingOfCourtService;
import com.padle.core.padelcoreservice.service.americano.AmericanoService;
import com.padle.core.padelcoreservice.service.americano.TeamPlayoffService;
import org.springframework.beans.factory.annotation.Value;
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

/** Чтение результатов без тестовых фикстур, записей рейтинга и таблиц исправлений preview. */
@Service
@Profile("!preview")
@ConditionalOnProperty(name = {"app.rating.pilot.enabled", "app.rating.pilot.auto-refresh"}, havingValue = "true")
public class PilotEloRefresh {
    private final EloRefreshSource source;
    private final PreviewEloLiveEvents live;
    private final EloPilotSnapshot initial;
    private final PreviewEloReplay replay;

    public PilotEloRefresh(ObjectMapper mapper, EloPilotProperties properties,
                           @Value("${app.rating.pilot.history}") Path historyPath, JdbcClient jdbc,
                           KingOfCourtService king, AmericanoService americano, TeamPlayoffService playoff)
            throws IOException {
        try (var input = Files.newInputStream(historyPath)) {
            source = mapper.readValue(input, EloRefreshSource.class);
        }
        try (var input = Files.newInputStream(properties.snapshot())) {
            initial = mapper.readValue(input, EloPilotSnapshot.class);
        }
        replay = new PreviewEloReplay(initial.startStep());
        var baseline = replay.replay(initial.snapshot().asOf(), source.history().names(), source.history().events());
        if (!baseline.equals(initial.snapshot()) || !source.history().aliases().equals(initial.aliases())
                || source.liveAfter().isAfter(initial.snapshot().asOf())) {
            throw new IllegalArgumentException("История не воспроизводит проверенный срез пилота Elo");
        }
        live = new PreviewEloLiveEvents(jdbc, king, americano, playoff);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PreviewEloRanking.Snapshot calculate() {
        var history = source.merge(live.load(source.liveAfter(), source.managedIds(), source.excludedEvents()));
        var asOf = history.events().stream().map(PreviewEloReplay.Event::day).max(LocalDate::compareTo)
                .filter(day -> day.isAfter(initial.snapshot().asOf())).orElse(initial.snapshot().asOf());
        return replay.replay(asOf, history.names(), history.events());
    }
}
