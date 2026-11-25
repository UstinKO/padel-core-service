package com.padle.core.padelcoreservice.telegram;

import com.padle.core.padelcoreservice.config.PlayerTelegramProperties;
import com.padle.core.padelcoreservice.repository.OwnerRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import com.padle.core.padelcoreservice.service.TelegramParticipationCoordinator;
import com.padle.core.padelcoreservice.telegram.TelegramModels.ContactPreference;
import com.padle.core.padelcoreservice.telegram.TelegramModels.Recipient;
import com.padle.core.padelcoreservice.telegram.TelegramModels.Summary;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PlayerTelegramSummaryTest {
    @Test void manualContactsIncludeWhatsappAndExcludeConnectedPlayers() {
        var source = new ResourceBundleMessageSource();
        source.setBasename("i18n/messages");
        source.setDefaultEncoding("UTF-8");
        var summary = new PlayerTelegramSummary(mock(PlayerTelegramProperties.class), mock(PlayerTelegramApi.class),
                mock(OwnerRepository.class), mock(TournamentRepository.class), mock(TelegramParticipationCoordinator.class),
                mock(PlayerTelegramDelivery.class), new PlayerTelegramMessages(source), mock(PlayerTelegramAccess.class));
        String result = summary.format(new Summary(42L, "Fixture", List.of(
                new Recipient(1, 1, "Ana", "PENDING", false, "NOT_SENT", ContactPreference.WHATSAPP),
                new Recipient(2, 2, "Beto", "CONFIRMED", true, "SENT", ContactPreference.TELEGRAM),
                new Recipient(3, 3, "Carlos", "PENDING", false, "NOT_SENT", ContactPreference.UNSPECIFIED))));
        assertThat(result).contains("Подтвердили: 1", "Связаться вручную: Ana (WhatsApp), Carlos");
        assertThat(result.substring(result.indexOf("Связаться вручную:"))).doesNotContain("Beto");
    }
}
