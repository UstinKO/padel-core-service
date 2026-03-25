package com.padle.core.padelcoreservice.dto.americano;

import com.padle.core.padelcoreservice.model.enums.AmericanoFormatType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AmericanoRankingDto {
    private Long tournamentId;
    private String tournamentName;
    private Integer totalRounds;
    private Integer completedRounds;

    /**
     * Итоговый тип формата после пост-проверки: FULL или FLEX.
     * Показывается пользователю согласно п. 15 / 16 ТЗ.
     */
    private AmericanoFormatType formatType;

    /**
     * Человекочитаемое сообщение о качестве сетки.
     * Примеры:
     *   "Сгенерирован Full Americano"
     *   "Full Americano невозможен для данных параметров, сгенерирован Flex Americano"
     *   "Flex Americano: 4 повтора партнёров, max разница bye = 1"
     */
    private String formatMessage;

    /** Число повторов партнёрских пар (0 для Full) */
    private Integer partnerRepeatCount;

    /** Максимальная разница по bye между игроками */
    private Integer maxByeDifference;

    private List<AmericanoPlayerRankingDto> ranking;
}