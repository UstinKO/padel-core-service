package com.padle.core.padelcoreservice.rating;

import com.padle.core.padelcoreservice.preview.PreviewEloRanking;
import org.springframework.ui.Model;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class EloRankingView {
    private EloRankingView() { }

    public static void populate(Model model, PreviewEloRanking.Snapshot snapshot, int startStep,
                                Map<Long, Long> aliases) {
        model.addAttribute("previewElo", snapshot);
        model.addAttribute("previewEloStartStep", startStep);
        var categories = snapshot.rows().stream().map(PreviewEloRanking.Row::category).distinct();
        model.addAttribute("previewEloCategories", (startStep > 0 ? categories : categories.sorted()).toList());
        var byPlayer = snapshot.rows().stream()
                .collect(Collectors.toMap(PreviewEloRanking.Row::id, Function.identity()));
        aliases.forEach((alias, canonical) -> Optional.ofNullable(byPlayer.get(canonical))
                .ifPresent(row -> byPlayer.put(alias, row)));
        model.addAttribute("previewEloByPlayer", Map.copyOf(byPlayer));
    }
}
