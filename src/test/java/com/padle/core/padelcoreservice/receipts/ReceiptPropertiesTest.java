package com.padle.core.padelcoreservice.receipts;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

class ReceiptPropertiesTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(ReceiptProperties.Binding.class);

    @Test void independentPilotIsClosedByDefault() {
        context.run(app -> {
            var properties = app.getBean(ReceiptProperties.class);
            assertThat(properties.enabled()).isFalse();
            assertThat(properties.pilotEnabled()).isTrue();
            assertThat(properties.pilotPlayerId()).isNull();
            assertThat(properties.pilotTournamentId()).isNull();
        });
    }

    @Test void previousPilotIdsStillLimitReceiptsWhenGeneralPilotIsLifted() {
        context.withPropertyValues("TELEGRAM_PLAYERS_RECEIPTS_ENABLED=true", "TELEGRAM_PLAYERS_PILOT_ENABLED=false",
                "TELEGRAM_PLAYERS_PILOT_PLAYER_ID=42", "TELEGRAM_PLAYERS_PILOT_TOURNAMENT_ID=10").run(app -> {
            var properties = app.getBean(ReceiptProperties.class);
            assertThat(properties.enabled()).isTrue();
            assertThat(properties.pilotEnabled()).isTrue();
            assertThat(properties.pilotPlayerId()).isEqualTo(42L);
            assertThat(properties.pilotTournamentId()).isEqualTo(10L);
        });
    }

    @Test void explicitReceiptIdsOverrideThePreviousSharedIds() {
        context.withPropertyValues("TELEGRAM_PLAYERS_PILOT_PLAYER_ID=42", "TELEGRAM_PLAYERS_PILOT_TOURNAMENT_ID=10",
                "TELEGRAM_PLAYERS_RECEIPTS_PILOT_PLAYER_ID=99", "TELEGRAM_PLAYERS_RECEIPTS_PILOT_TOURNAMENT_ID=11").run(app -> {
            var properties = app.getBean(ReceiptProperties.class);
            assertThat(properties.pilotPlayerId()).isEqualTo(99L);
            assertThat(properties.pilotTournamentId()).isEqualTo(11L);
            assertThat(properties.pilotEnabled()).isTrue();
        });
    }

    @Test void explicitlyEmptyReceiptIdsDoNotFallBackToSharedIds() {
        context.withPropertyValues("TELEGRAM_PLAYERS_PILOT_PLAYER_ID=42", "TELEGRAM_PLAYERS_PILOT_TOURNAMENT_ID=10",
                "TELEGRAM_PLAYERS_RECEIPTS_PILOT_PLAYER_ID=", "TELEGRAM_PLAYERS_RECEIPTS_PILOT_TOURNAMENT_ID=").run(app -> {
            var properties = app.getBean(ReceiptProperties.class);
            assertThat(properties.pilotPlayerId()).isNull();
            assertThat(properties.pilotTournamentId()).isNull();
            assertThat(properties.pilotEnabled()).isTrue();
        });
    }
}
