package com.padle.core.padelcoreservice.dto.americano;

import com.padle.core.padelcoreservice.model.enums.AmericanoFormatType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AmericanoConfigDto {

    /** ID турнира — используется в форме инициализации из админ-панели */
    private Long tournamentId;

    /** Лимит очков в матче (тай-брейк до этой суммы). Пример: 15 / 21 / 25 / 31 / 32 */
    private Integer pointsPerMatch;

    /** Количество кортов */
    private Integer courts;

    /** Количество раундов */
    private Integer totalRounds;

    /**
     * Режим генерации:
     * AUTO  — система сама выбирает Full или Flex
     * FULL  — принудительно Full Americano (ошибка если невозможно)
     * FLEX  — принудительно Flex Americano
     */
    private AmericanoFormatType generationMode;

    /** Устаревшее поле shuffleAlgorithm — оставлено для обратной совместимости с формами */
    @Deprecated
    private String shuffleAlgorithm;

    // ── Геттеры с дефолтными значениями ─────────────────────────────────────

    public Integer getPointsPerMatch() {
        return pointsPerMatch != null ? pointsPerMatch : 32;
    }

    public Integer getCourts() {
        return courts != null ? courts : 2;
    }

    public Integer getTotalRounds() {
        return totalRounds != null ? totalRounds : 5;
    }

    public AmericanoFormatType getGenerationMode() {
        return generationMode != null ? generationMode : AmericanoFormatType.AUTO;
    }
}