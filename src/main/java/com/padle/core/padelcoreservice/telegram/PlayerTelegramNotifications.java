package com.padle.core.padelcoreservice.telegram;

import com.padle.core.padelcoreservice.model.ParticipationRequest;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.TournamentRegistration;
import com.padle.core.padelcoreservice.model.enums.ParticipationStatus;
import com.padle.core.padelcoreservice.repository.ClubRepository;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import com.padle.core.padelcoreservice.repository.TournamentRegistrationRepository;
import com.padle.core.padelcoreservice.service.TelegramParticipationCoordinator;
import com.padle.core.padelcoreservice.telegram.TelegramModels.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class PlayerTelegramNotifications {
    private final TournamentRegistrationRepository registrations;
    private final PlayerRepository players;
    private final ClubRepository clubs;
    private final TelegramParticipationCoordinator participation;
    private final PlayerTelegramStore store;
    private final PlayerTelegramMessages messages;
    private final PlayerTelegramDelivery delivery;
    private final PlayerTelegramAccess access;
    private final PlayerTelegramReceipts receipts;
    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Transactional(readOnly = true)
    public void send(long registrationId, boolean reminder) {
        registrations.findById(registrationId)
                .filter(row -> Boolean.TRUE.equals(row.getIsActive()) && row.isConfirmed())
                .filter(row -> access.allowsEvent(row.getTournament().getId()))
                .filter(row -> TelegramParticipationCoordinator.upcoming(row.getTournament()))
                .ifPresent(row -> recipient(row).ifPresent(player -> send(row, player, reminder)));
    }

    private Optional<PlayerPadel> recipient(TournamentRegistration row) {
        return participation.recipient(row, participation.current(row)).flatMap(players::findById)
                .filter(player -> access.allowsPlayer(player.getId())).filter(PlayerPadel::isActivo).filter(player -> !player.isGuestAccount());
    }

    private void send(TournamentRegistration row, PlayerPadel player, boolean reminder) {
        store.connection(player.getId()).filter(Connection::enabled).ifPresent(connection -> {
            var request = participation.current(row);
            boolean pending = request.map(ParticipationRequest::getStatus)
                    .filter(status -> status == ParticipationStatus.PENDING).isPresent();
            if (!reminder && !pending) {
                return;
            }
            var locale = player.getLocale();
            var tournament = row.getTournament();
            var club = clubs.findById(tournament.getClubId());
            String clubName = club.map(c -> c.getNombre()).orElse("");
            String address = club.map(c -> c.getDireccion()).orElse("");
            String when = tournament.getFechaHoraInicioCompleta().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
            String text = messages.text(reminder ? "reminder" : "request", locale,
                    row.getPlayer().getNombreCompleto(), tournament.getNombre(), when, clubName, address);
            if (!receipts.buttons(row, locale).isEmpty()) {
                text += "\n\n" + messages.text("receipt.instruction", locale);
            }
            var buttons = buttons(row, player, request.filter(r -> pending));
            String key = row.getId() + ":" + row.getCancellationVersion() + ":" + player.getId()
                    + ":" + (reminder ? "REMINDER" : "REQUEST") + ":"
                    + TelegramParticipationCoordinator.startsAt(tournament);
            delivery.sendOnce(key, tournament.getId(), row.getId(), connection.chatId(), text, buttons);
        });
    }

    private List<List<Button>> buttons(TournamentRegistration row, PlayerPadel player,
                                       Optional<ParticipationRequest> pending) {
        var locale = player.getLocale();
        var links = List.of(Button.link(messages.text("view", locale), baseUrl + "/torneo/" + row.getTournament().getId()),
                Button.link(messages.text("cancel", locale), baseUrl + "/players/dashboard"));
        var original = pending.map(request -> List.of(
                List.of(Button.action(messages.text("confirm", locale), "c:" + request.getId() + ":" + Long.toString(
                        TelegramParticipationCoordinator.startsAt(row.getTournament()).getEpochSecond(), 36))), links))
                .orElseGet(() -> List.of(links));
        return Stream.concat(original.stream(), receipts.buttons(row, locale).stream()).toList();
    }
}
