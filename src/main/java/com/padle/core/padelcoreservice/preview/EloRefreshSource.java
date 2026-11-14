package com.padle.core.padelcoreservice.preview;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Проверенная граница между архивом и автоматически читаемыми результатами сайта. */
record EloRefreshSource(PreviewEloReplay.History history, LocalDate liveAfter,
                        Set<String> managedEvents, Set<String> excludedEvents) {
    EloRefreshSource {
        Objects.requireNonNull(history);
        Objects.requireNonNull(liveAfter);
        managedEvents = Set.copyOf(managedEvents);
        excludedEvents = Set.copyOf(excludedEvents);
        validateKeys(history.events());
        var keys = history.events().stream().map(PreviewEloReplay.Event::key).collect(Collectors.toSet());
        if (history.events().isEmpty() || history.events().stream().anyMatch(event -> event.day().isAfter(liveAfter))
                || !keys.containsAll(managedEvents)
                || managedEvents.stream().anyMatch(key -> !key.matches("S-[1-9][0-9]*"))
                || excludedEvents.stream().anyMatch(keys::contains)) {
            throw new IllegalArgumentException("Некорректная граница автоматического пересчёта Elo");
        }
    }

    Set<Long> managedIds() {
        return managedEvents.stream().map(key -> Long.parseLong(key.substring(2))).collect(Collectors.toSet());
    }

    PreviewEloReplay.History merge(PreviewEloLiveEvents.LiveHistory live) {
        var base = history.events().stream().collect(Collectors.toMap(PreviewEloReplay.Event::key, Function.identity()));
        var events = Stream.concat(
                history.events().stream().filter(event -> !managedEvents.contains(event.key())),
                live.events().stream().filter(event -> !excludedEvents.contains(event.key()))
                        .filter(event -> managedEvents.contains(event.key())
                                || (!base.containsKey(event.key()) && event.day().isAfter(liveAfter)))
                        .map(event -> base.containsKey(event.key()) ? withVerifiedMetadata(base.get(event.key()), event) : event)
        ).toList();
        validateKeys(events);
        var names = new HashMap<>(live.names());
        names.putAll(history.names());
        return new PreviewEloReplay.History(names, events, history.aliases());
    }

    private static PreviewEloReplay.Event withVerifiedMetadata(PreviewEloReplay.Event base, PreviewEloReplay.Event live) {
        return new PreviewEloReplay.Event(base.key(), base.day(), base.category(), live.games(), live.champions(),
                live.runnersUp(), base.title(), base.format(), base.resultsUrl());
    }

    private static void validateKeys(List<PreviewEloReplay.Event> events) {
        var eventKeys = events.stream().map(PreviewEloReplay.Event::key).toList();
        var gameKeys = events.stream().flatMap(event -> event.games().stream())
                .map(game -> game.key().replaceFirst("^pair:", "americano:")).toList();
        if (Set.copyOf(eventKeys).size() != eventKeys.size() || Set.copyOf(gameKeys).size() != gameKeys.size()) {
            throw new IllegalArgumentException("Повтор события или матча в источнике Elo");
        }
    }
}
