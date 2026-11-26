package com.padle.core.padelcoreservice.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TelegramCampaignWorker {
    private final CampaignAudienceGuard guard;
    private final TelegramCampaignStore store;
    private final TelegramCampaignDispatch dispatch;

    @Scheduled(fixedDelay = 1000)
    public void tick() {
        if (!guard.enabled()) return;
        store.recover();
        for (int index=0;index<10;index++) {
            var next = store.claim();
            if (next.isEmpty()) return;
            var item = next.orElseThrow();
            try { dispatch.send(item); }
            catch (RuntimeException exception) {
                store.uncertain(item);
                log.warn("Рассылка {}: результат отправки не определён",item.campaignId());
            }
        }
        store.finishCampaigns();
    }
}
