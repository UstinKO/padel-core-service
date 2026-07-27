package com.padle.core.padelcoreservice.service.americano;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * ТЗ §36/§41 (T16): когда становится известен победитель play-in (1/8 финала), команда,
 * ожидавшая его в четвертьфинале, может оказаться его "старым знакомым" по квалификации.
 * Вместо готовности сыграть повторный матч (вариант 1) система ищет среди уже
 * укомплектованных пар четвертьфинала такую, с которой можно обменяться местами (вариант 2,
 * §41: "1-е место играет против 7-го, 2-е ожидает победителя 1/8") — так, чтобы ни одна из
 * двух получившихся пар не была повторной встречей.
 */
final class PlayoffRematchSwap {

    private PlayoffRematchSwap() {
    }

    /**
     * @param waitingId       команда, ожидавшая победителя play-in в четвертьфинале
     * @param winnerId        только что определившийся победитель play-in
     * @param siblingPairs    уже полностью укомплектованные (обе команды известны) пары
     *                        других четвертьфинальных матчей — кандидаты на обмен
     * @param priorOpponents  история встреч (любая завершённая стадия турнира)
     * @return id команды-кандидата на обмен с waitingId, если нашёлся вариант без повторов
     */
    static Optional<Long> findSwap(Long waitingId, Long winnerId,
                                    List<long[]> siblingPairs,
                                    Map<Long, Set<Long>> priorOpponents) {
        if (!priorOpponents.getOrDefault(waitingId, Set.of()).contains(winnerId)) {
            return Optional.empty(); // повтора нет — обмен не нужен (вариант 1 остаётся в силе)
        }

        for (long[] pair : siblingPairs) {
            for (int i = 0; i < 2; i++) {
                long candidateId = pair[i];
                long partnerId = pair[1 - i];
                boolean candidateSafeWithWinner = !priorOpponents.getOrDefault(candidateId, Set.of()).contains(winnerId);
                boolean waitingSafeWithPartner = !priorOpponents.getOrDefault(waitingId, Set.of()).contains(partnerId);
                if (candidateSafeWithWinner && waitingSafeWithPartner) {
                    return Optional.of(candidateId);
                }
            }
        }
        return Optional.empty(); // варианта без повторов нет — остаётся исходное назначение (ТЗ §1: неизбежное исключение)
    }
}
