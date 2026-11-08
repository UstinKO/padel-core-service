package com.padle.core.padelcoreservice.service.conduct;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ConductChange {
    Action value();
    Subject subject() default Subject.TOURNAMENT;
    int argument() default 0;

    enum Action {
        BASELINE, START, START_ROUND, COMPLETE_ROUND, NEXT_ROUND, RESULT, FINISH,
        RESET_ROUND, RESTART, ROSTER, ROUND_SETTINGS, QUALIFICATION, CREATE_MATCH,
        QUEUE_MATCH, REMOVE_MATCH, COURT, MATCH_TEAMS, PLAYOFF, REGENERATE_PLAYOFF,
        CHECKPOINT_RESET_ROUND, CHECKPOINT_RESTART, CHECKPOINT_REGENERATE_PLAYOFF,
        CHECKPOINT_RESTORE, RESTORE, ADD_POINTS, EDIT_POINTS, CANCEL_POINTS,
        PLAYOFF_MATCH_STARTED, PLAYOFF_SWAP, PLAYOFF_PAIR_CORRECTED, CHECKPOINT_PLAYOFF_SWAP;

        boolean isPointsChange() {
            return this == ADD_POINTS || this == EDIT_POINTS || this == CANCEL_POINTS;
        }

        Action checkpoint() {
            return switch (this) {
                case RESET_ROUND -> CHECKPOINT_RESET_ROUND;
                case RESTART -> CHECKPOINT_RESTART;
                case REGENERATE_PLAYOFF -> CHECKPOINT_REGENERATE_PLAYOFF;
                case PLAYOFF_SWAP, PLAYOFF_PAIR_CORRECTED -> CHECKPOINT_PLAYOFF_SWAP;
                default -> null;
            };
        }
    }

    enum Subject {
        TOURNAMENT,
        KING,
        KING_ROUND,
        KING_RESULT,
        AMERICANO_ROUND,
        AMERICANO_MATCH,
        AMERICANO_RESULT,
        AMERICANO_TEAM
    }
}
