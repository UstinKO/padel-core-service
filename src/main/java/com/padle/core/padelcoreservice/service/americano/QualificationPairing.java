package com.padle.core.padelcoreservice.service.americano;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Формирует пары 2-го тура квалификации: победители с победителями, проигравшие с проигравшими (ТЗ §1).
 * <p>
 * Итоговое ТЗ §2 (T15): при 10 и 14 командах оба списка (победители/проигравшие) после первого
 * тура нечётного размера — по одному "хвосту" остаётся с каждой стороны. Система не должна
 * автоматически сводить эти два "хвоста" друг с другом напрямую: вместо этого один "хвост"
 * забирает партнёра у уже сформированной чистой пары того же результата, а освободившаяся
 * команда становится тем единственным исключением из правила "победитель к победителю"
 * ("В одном из матчей допускается нарушение правила").
 */
final class QualificationPairing {

    record Pairing(Long team1Id, Long team2Id) {
    }

    private QualificationPairing() {
    }

    static List<Pairing> pairSecondRound(List<Long> winners, List<Long> losers) {
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

        if (!winnerPairs.isEmpty()) {
            Pairing stolen = winnerPairs.get(winnerPairs.size() - 1);
            result.remove(stolen);
            result.add(new Pairing(stolen.team1Id(), tailWinner));
            result.add(new Pairing(stolen.team2Id(), tailLoser));
        } else if (!loserPairs.isEmpty()) {
            Pairing stolen = loserPairs.get(loserPairs.size() - 1);
            result.remove(stolen);
            result.add(new Pairing(stolen.team1Id(), tailLoser));
            result.add(new Pairing(stolen.team2Id(), tailWinner));
        } else {
            // Единственная возможная пара — обе стороны состоят ровно из одного "хвоста".
            result.add(new Pairing(tailWinner, tailLoser));
        }
        return result;
    }
}
