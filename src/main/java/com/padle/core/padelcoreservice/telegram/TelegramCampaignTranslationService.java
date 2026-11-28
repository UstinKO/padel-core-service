package com.padle.core.padelcoreservice.telegram;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.padle.core.padelcoreservice.exception.AccountException;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.service.TelegramMessagePreviewService;
import com.padle.core.padelcoreservice.telegram.CampaignModels.Texts;
import com.padle.core.padelcoreservice.telegram.TelegramAudienceModels.Selection;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.micrometer.core.annotation.Timed;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TelegramCampaignTranslationService {
    private final CampaignAudienceGuard guard;
    private final TelegramMessagePreviewService preview;
    private final TelegramCampaignTranslator translator;
    private final Cache<Long, Bucket> limits = CacheBuilder.newBuilder().maximumSize(10000)
            .expireAfterAccess(1, TimeUnit.HOURS).build();

    @Timed("telegram.campaign.translation")
    public Translated translate(Owner session, Selection selection) {
        var actor = guard.actor(session.getId());
        var audience = preview.preview(actor, selection);
        if (audience.recipients().isEmpty()) {
            return new Translated("other", Texts.empty(), List.of());
        }
        var bucket = limits.asMap().computeIfAbsent(actor.getId(), key -> Bucket.builder()
                .addLimit(Bandwidth.builder().capacity(6).refillGreedy(6, Duration.ofMinutes(1)).build())
                .addLimit(Bandwidth.builder().capacity(60).refillGreedy(60, Duration.ofHours(1)).build()).build());
        if (!bucket.tryConsume(1)) {
            throw new AccountException(HttpStatus.TOO_MANY_REQUESTS, "TELEGRAM_TRANSLATION_LIMIT");
        }
        var translation = translator.translate(selection.text());
        var counts = audience.recipients().stream().collect(Collectors.groupingBy(
                TelegramAudienceModels.Candidate::language, Collectors.counting()));
        var languages = counts.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> new LanguageCount(entry.getKey(), entry.getValue())).toList();
        return new Translated(translation.sourceLanguage(), translation.texts(), languages);
    }

    public record LanguageCount(String language, long count) {}
    public record Translated(String sourceLanguage, Texts texts, List<LanguageCount> languages) {}
}
