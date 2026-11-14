package com.padle.core.padelcoreservice.rating;

import com.padle.core.padelcoreservice.preview.PreviewEloRanking;

import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public record EloPilotSnapshot(PreviewEloRanking.Snapshot snapshot, int startStep, Map<Long, Long> aliases) {
    public EloPilotSnapshot {
        Objects.requireNonNull(snapshot, "Отсутствует срез пилотного рейтинга");
        if (snapshot.asOf() == null || snapshot.rows() == null || snapshot.rows().isEmpty() || startStep < 0) {
            throw new IllegalArgumentException("Некорректный срез пилотного рейтинга");
        }
        var ids = snapshot.rows().stream().map(PreviewEloRanking.Row::id).collect(Collectors.toSet());
        if (ids.size() != snapshot.rows().size() || ids.stream().anyMatch(id -> id <= 0)) {
            throw new IllegalArgumentException("Дубли или некорректные ID в срезе пилотного рейтинга");
        }
        aliases = Map.copyOf(Objects.requireNonNullElse(aliases, Map.of()));
        for (var alias : aliases.entrySet()) {
            if (alias.getKey() <= 0 || ids.contains(alias.getKey()) || aliases.containsKey(alias.getValue())
                    || !ids.contains(alias.getValue())) {
                throw new IllegalArgumentException("Некорректная связь профилей пилота: " + alias.getKey());
            }
        }
        snapshot = new PreviewEloRanking.Snapshot(snapshot.asOf(), List.copyOf(snapshot.rows()));
    }
}
