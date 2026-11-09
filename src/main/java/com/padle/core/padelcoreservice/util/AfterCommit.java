package com.padle.core.padelcoreservice.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Выполняет внешнее уведомление только после успешной фиксации текущей транзакции. */
@Slf4j
public final class AfterCommit {
    private AfterCommit() {
    }

    public static void run(Runnable notification) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deliver(notification);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deliver(notification);
            }
        });
    }

    private static void deliver(Runnable notification) {
        try {
            notification.run();
        } catch (Exception e) {
            log.error("Не удалось отправить уведомление после фиксации операции", e);
        }
    }
}
