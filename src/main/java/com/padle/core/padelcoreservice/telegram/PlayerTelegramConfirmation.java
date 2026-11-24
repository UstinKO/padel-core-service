package com.padle.core.padelcoreservice.telegram;

import com.padle.core.padelcoreservice.dto.ParticipationDtos.Review;
import com.padle.core.padelcoreservice.model.enums.ParticipationStatus;
import com.padle.core.padelcoreservice.repository.TournamentRegistrationRepository;
import com.padle.core.padelcoreservice.telegram.TelegramModels.Button;
import com.padle.core.padelcoreservice.telegram.TelegramModels.Message;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class PlayerTelegramConfirmation {
    private final PlayerTelegramDelivery delivery;
    private final PlayerTelegramApi api;
    private final PlayerTelegramMessages messages;
    private final TournamentRegistrationRepository registrations;
    private final PlayerTelegramReceipts receipts;
    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public void show(Review review, long playerId, Locale locale, long expectedStart, Message original) {
        if (review.status() != ParticipationStatus.CONFIRMED || review.outdated()) {
            return;
        }
        String key = "confirmed:" + review.requestId() + ":" + expectedStart + ":" + playerId;
        try {
            delivery.sendOnce(key, review.tournamentId(), null, original.chat().id(),
                    messages.text("confirmationMessage", locale, review.tournamentName()), List.of());
        } finally {
            var links = List.of(List.of(
                    Button.link(messages.text("view", locale), baseUrl + "/torneo/" + review.tournamentId()),
                    Button.link(messages.text("cancel", locale), baseUrl + "/players/dashboard")));
            var upload = registrations.findByTournamentIdAndPlayerId(review.tournamentId(), playerId)
                    .map(row -> receipts.buttons(row, locale)).orElseGet(List::of);
            api.editKeyboard(original.chat().id(), original.messageId(),
                    Stream.concat(links.stream(), upload.stream()).toList());
        }
    }
}
