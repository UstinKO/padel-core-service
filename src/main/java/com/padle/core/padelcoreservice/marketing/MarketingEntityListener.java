package com.padle.core.padelcoreservice.marketing;

import com.padle.core.padelcoreservice.model.Payment;
import com.padle.core.padelcoreservice.model.TournamentRegistration;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostUpdate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Записывает аналитический журнал в той же транзакции, что и изменение сущности. */
@Component
@RequiredArgsConstructor
public class MarketingEntityListener {
    private final MarketingJournal journal;

    @PostPersist
    @PostUpdate
    public void observed(Object entity) {
        if (entity instanceof TournamentRegistration registration) {
            journal.registration(registration);
        } else if (entity instanceof Payment payment) {
            journal.payment(payment);
        }
    }
}
