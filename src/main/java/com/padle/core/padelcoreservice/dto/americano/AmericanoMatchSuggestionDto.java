package com.padle.core.padelcoreservice.dto.americano;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Предложенная координатору пара для 2-го тура квалификации:
 * "победитель против победителя, проигравший против проигравшего" (ТЗ §5).
 * Не обязательна к принятию — координатор может назначить любую другую пару (T4).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AmericanoMatchSuggestionDto {
    private AmericanoTeamDto team1;
    private AmericanoTeamDto team2;

    /** ТЗ §22: при нечётном числе команд матч "победитель — дополнительная команда" желательно запустить как можно раньше. */
    private boolean priority;

    /** LFPT-306: пара из двух команд без единого сыгранного матча (1-й матч), а не W-W/L-L пара 2-го тура. */
    private boolean firstMatch;
}
