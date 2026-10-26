package com.padle.core.padelcoreservice.service.americano;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Формирует пары 2-го тура квалификации: победители с победителями, проигравшие с проигравшими.
 * <p>
 * При 10 и 14 командах оба списка (победители/проигравшие) после первого тура нечётного
 * размера — по одному "хвосту" остаётся с каждой стороны. Эти два "хвоста" не сводятся друг
 * с другом напрямую: один "хвост" забирает партнёра у уже сформированной пары того же
 * результата, а освободившаяся команда становится единственным допустимым исключением
 * из правила "победитель к победителю".
 * <p>
 * Пара для обмена выбирается так, чтобы обмен не создавал ни одного повтора соперника
 * по 1-му туру (повторные встречи исключаются по возможности), а не первая попавшаяся.
 */
final class QualificationPairing {

    record Pairing(Long team1Id, Long team2Id) {
    }

    private QualificationPairing() {
    }

    static List<Pairing> pairSecondRound(List<Long> winners, List<Long> losers, Map<Long, Set<Long>> priorOpponents) {
        Deque<Long> w = new ArrayDeque<>(winners);
        Deque<Long> l = new ArrayDeque<>(losers);

        List<Pairing> winnerPairs = new ArrayList<>();
        while (w.size() >= 2) {
            winnerPairs.add(new Pairing(w.poll(), w.poll()));
        }
        List<Pairing> loserPairs = new ArrayList<>();
        while (l.size() >= 2) {
            loserPairs.add(new Pairing(l.poll(), l.poll()));
        }

        Long tailWinner = w.poll();
        Long tailLoser = l.poll();

        List<Pairing> result = new ArrayList<>();
        result.addAll(winnerPairs);
        result.addAll(loserPairs);

        if (tailWinner == null || tailLoser == null) {
            // Нечётный "хвост" только с одной стороны — партнёра сейчас нет, ждём следующего результата.
            return result;
        }

        if (applyCleanSteal(result, winnerPairs, tailWinner, tailLoser, priorOpponents)) {
            return result;
        }
        if (applyCleanSteal(result, loserPairs, tailLoser, tailWinner, priorOpponents)) {
            return result;
        }

        // Ни один обмен не даёт пары без повторов — исключение неизбежно, берём первую доступную.
        if (!winnerPairs.isEmpty()) {
            Pairing stolen = winnerPairs.get(0);
            result.remove(stolen);
            result.add(new Pairing(stolen.team1Id(), tailWinner));
            result.add(new Pairing(stolen.team2Id(), tailLoser));
        } else if (!loserPairs.isEmpty()) {
            Pairing stolen = loserPairs.get(0);
            result.remove(stolen);
            result.add(new Pairing(stolen.team1Id(), tailLoser));
            result.add(new Pairing(stolen.team2Id(), tailWinner));
        } else {
            // Единственная возможная пара — обе стороны состоят ровно из одного "хвоста".
            result.add(new Pairing(tailWinner, tailLoser));
        }
        return result;
    }

    /**
     * Ищет среди {@code candidatePairs} пару, где один член может остаться в своём "бакете"
     * (сыграть с {@code tailSameBucket}), а другой — уйти в перекрёстную пару с
     * {@code tailOtherBucket}, и ни одна из двух получившихся пар не будет повтором.
     * При успехе меняет {@code result} на месте и возвращает true.
     */
    private static boolean applyCleanSteal(List<Pairing> result, List<Pairing> candidatePairs,
                                            Long tailSameBucket, Long tailOtherBucket,
                                            Map<Long, Set<Long>> priorOpponents) {
        for (Pairing pair : candidatePairs) {
            for (int i = 0; i < 2; i++) {
                Long staying = i == 0 ? pair.team1Id() : pair.team2Id();
                Long leaving = i == 0 ? pair.team2Id() : pair.team1Id();
                boolean stayingSafe = !priorOpponents.getOrDefault(staying, Set.of()).contains(tailSameBucket);
                boolean leavingSafe = !priorOpponents.getOrDefault(leaving, Set.of()).contains(tailOtherBucket);
                if (stayingSafe && leavingSafe) {
                    result.remove(pair);
                    result.add(new Pairing(staying, tailSameBucket));
                    result.add(new Pairing(leaving, tailOtherBucket));
                    return true;
                }
            }
        }
        return false;
    }
}
