package com.padle.core.padelcoreservice.dto.americano;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Итог турнира после финального матча (ТЗ §16): чемпион и команда, занявшая 2-е место.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamPlayoffFinishedDto {
    private AmericanoTeamDto champion;
    private AmericanoTeamDto runnerUp;
}
