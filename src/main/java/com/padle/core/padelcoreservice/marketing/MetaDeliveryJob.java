package com.padle.core.padelcoreservice.marketing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MetaDeliveryJob {
    private final MetaProperties properties;
    private final MarketingOutbox outbox;
    private final MetaCapiClient client;

    @Scheduled(fixedDelayString = "${app.analytics.meta.delivery-delay-ms:10000}")
    public void deliver() {
        if (!properties.serverEnabled()) {
            return;
        }
        try {
            for (int index = 0; index < 20; index++) {
                var next = outbox.claim();
                if (next.isEmpty()) {
                    return;
                }
                next.ifPresent(event -> {
                    MetaCapiClient.Result result = client.send(event);
                    outbox.finish(event, result.accepted(), result.httpStatus());
                    if (result.messageCount() > 0) {
                        log.warn("Meta вернула сообщения для события {} (HTTP {}, сообщений: {}); проверьте настройки и Test Events",
                                event.eventId(), result.httpStatus(), result.messageCount());
                    }
                    if (!result.accepted()) {
                        log.warn("Meta не приняла событие {}, HTTP {}", event.eventId(), result.httpStatus());
                    }
                });
            }
        } catch (RuntimeException exception) {
            // На границе фоновой задачи не выводим HTTP-запросы с секретом и данными сопоставления.
            log.error("Доставка Meta прервана; аренда события истечёт для повторной попытки ({})", exception.getClass().getSimpleName());
        }
    }
}
