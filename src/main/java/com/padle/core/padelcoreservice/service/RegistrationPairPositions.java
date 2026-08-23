package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.model.TournamentRegistration;
import com.padle.core.padelcoreservice.model.enums.RegistrationStatus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LFPT-357: единая точка нормализации позиций пар турнира — устраняет дубли/пропуски
 * в сыром {@link TournamentRegistration#getPosition()} (следствие отменённых и повторно
 * созданных регистраций), пересчитывая последовательный номер по каждой паре в порядке
 * следования. Используется и {@link PaymentService#getPaymentManagementData} (колонка
 * "Posición" на странице оплат), и {@code TeamPlayoffService#resolveTeamNumberFromRegistration}
 * (номер команды при добавлении в Team Playoff) — чтобы оба места гарантированно совпадали.
 */
public final class RegistrationPairPositions {

    private RegistrationPairPositions() {
    }

    /**
     * @param orderedRegistrations регистрации турнира, отсортированные так же, как для
     *                              отображения на странице оплат
     *                              ({@code findByTournamentIdOrderByPositionAscWaitlistPositionAsc})
     * @return pairGroupId (см. {@link #pairGroupId}) -> последовательная позиция (1, 2, 3...)
     */
    public static Map<Long, Integer> computeDisplayedPositions(List<TournamentRegistration> orderedRegistrations) {
        Map<Long, Integer> groupToPos = new LinkedHashMap<>();
        int[] nextPos = {1};
        for (TournamentRegistration reg : orderedRegistrations) {
            if (reg.getStatus() != RegistrationStatus.CONFIRMED
                    && reg.getStatus() != RegistrationStatus.PARTNER_INVITED) {
                continue;
            }
            groupToPos.computeIfAbsent(pairGroupId(reg), k -> nextPos[0]++);
        }
        return groupToPos;
    }

    /** Главный игрок пары — общий ключ для обеих регистраций пары (главного и партнёра в БД). */
    public static Long pairGroupId(TournamentRegistration reg) {
        return reg.getMainPlayerId() != null ? reg.getMainPlayerId() : reg.getPlayer().getId();
    }
}
