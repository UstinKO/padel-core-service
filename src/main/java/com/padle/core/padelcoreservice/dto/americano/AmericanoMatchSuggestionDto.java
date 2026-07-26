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
}
