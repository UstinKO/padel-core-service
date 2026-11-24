package com.padle.core.padelcoreservice.telegram;

import com.padle.core.padelcoreservice.exception.AccountException;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.TournamentRegistration;
import com.padle.core.padelcoreservice.receipts.ReceiptFiles;
import com.padle.core.padelcoreservice.receipts.ReceiptIntake;
import com.padle.core.padelcoreservice.receipts.ReceiptRegistrations;
import com.padle.core.padelcoreservice.receipts.ReceiptStore;
import com.padle.core.padelcoreservice.receipts.ReceiptModels.Upload;
import com.padle.core.padelcoreservice.receipts.ReceiptModels.Receipt;
import com.padle.core.padelcoreservice.receipts.ReceiptModels.State;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import com.padle.core.padelcoreservice.telegram.TelegramModels.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
public class PlayerTelegramReceipts {
    private static final int PAGE_SIZE = 8;
    private final ReceiptRegistrations registrations;
    private final ReceiptStore receipts;
    private final ReceiptIntake intake;
    private final PlayerTelegramStore connections;
    private final PlayerRepository players;
    private final PlayerTelegramFiles files;
    private final PlayerTelegramApi api;
    private final PlayerTelegramDelivery delivery;
    private final PlayerTelegramMessages messages;

    public List<List<Button>> buttons(TournamentRegistration row, long recipientId, Locale locale) {
        if (!registrations.enabled() || row.getPlayer().getId() != recipientId) {
            return List.of();
        }
        return List.of(List.of(Button.action(messages.text("receipt.button", locale), "r:" + number(row.getId())
                + ":" + number(row.getCancellationVersion()))));
    }

    public boolean callback(Callback callback) {
        var data = Optional.ofNullable(callback.data()).orElse("");
        if (!data.startsWith("r:") && !data.startsWith("q:") && !data.startsWith("p:")) {
            return false;
        }
        var player = player(callback.from().id());
        if (player.isEmpty() || !registrations.enabled()) {
            api.acknowledge(callback.id(), messages.text("receipt.unavailable", locale(player)));
            return true;
        }
        var sender = player.orElseThrow();
        api.acknowledge(callback.id(), "");
        try {
            handle(callback, sender, data.split(":", -1));
        } catch (AccountException | IllegalArgumentException error) {
            reply("receipt-error:" + callback.id(), sender,
                    "RECEIPT_INVALID_FILE".equals(error.getMessage()) ? "receipt.invalidFile" : "receipt.unavailable");
        } catch (RestClientException error) {
            reply("receipt-network:" + callback.id(), sender, "receipt.retry");
        }
        return true;
    }

    private void handle(Callback callback, PlayerPadel player, String[] parts) {
        if (parts[0].equals("r") && parts.length == 3) {
            var row = registrations.require(parse(parts[1]), player.getId(), parse(parts[2]));
            reply("receipt-prompt:" + callback.id(), player, "receipt.prompt", row.getTournament().getNombre());
        } else if (parts[0].equals("p") && parts.length == 4) {
            var upload = requireUpload(parts[1], player, callback.from().id());
            choices(upload, player, Integer.parseInt(parts[2]), "receipt-page:" + callback.id());
        } else if (parts[0].equals("q") && parts.length == 4) {
            attach(callback, player, parts);
        } else {
            throw ReceiptRegistrations.unavailable();
        }
    }

    private void attach(Callback callback, PlayerPadel player, String[] parts) {
        var upload = requireUpload(parts[1], player, callback.from().id());
        var row = registrations.require(parse(parts[2]), player.getId(), parse(parts[3]));
        // Повторное нажатие читает сохранённый результат без повторного скачивания файла.
        if (upload.receiptId() != null) {
            var existing = receipts.require(row.getTournament().getId(), upload.receiptId(), false);
            if (existing.registrationId() != row.getId() || existing.registrationVersion() != row.getCancellationVersion()) {
                throw ReceiptRegistrations.unavailable();
            }
            received(callback, player, upload.id(), existing);
            return;
        }
        var file = files.download(upload.fileId());
        var receipt = intake.attach(upload.id(), player.getId(), callback.from().id(), row.getId(),
                row.getCancellationVersion(), file);
        received(callback, player, upload.id(), receipt);
    }

    private void received(Callback callback, PlayerPadel player, UUID upload, Receipt receipt) {
        reply("receipt-received:" + upload, player,
                receipt.state() == State.PENDING ? "receipt.received" : "receipt.alreadyReviewed", receipt.eventName());
        api.editKeyboard(callback.message().chat().id(), callback.message().messageId(), List.of());
    }

    public void message(long update, Message message) {
        var player = player(message.from().id());
        if (player.isEmpty() || !registrations.enabled()) {
            return;
        }
        var sender = player.orElseThrow();
        if ("/receipts".equals(message.text())) {
            reply("receipt-help:" + update, sender, "receipt.help");
            return;
        }
        if (message.document() == null && (message.photo() == null || message.photo().isEmpty())) {
            return;
        }
        try {
            var file = attachment(message);
            var upload = intake.receive(update, sender.getId(), message.chat().id(), file);
            choices(upload, sender, 0, "receipt-choice:" + update);
        } catch (AccountException error) {
            reply("receipt-invalid:" + update, sender,
                    "RECEIPT_INVALID_FILE".equals(error.getMessage()) ? "receipt.invalidFile" : "receipt.unavailable");
        }
    }

    private String attachment(Message message) {
        if (message.document() != null) {
            var doc = message.document();
            if (!List.of("application/pdf", "image/jpeg", "image/png").contains(Optional.ofNullable(doc.mimeType()).orElse(""))
                    || (doc.fileSize() != null && doc.fileSize() > ReceiptFiles.MAX_BYTES)) {
                throw ReceiptFiles.invalid();
            }
            return doc.fileId();
        }
        var photo = message.photo().stream().max(Comparator.comparingLong(p -> (long) p.width() * p.height()))
                .orElseThrow(ReceiptFiles::invalid);
        if (photo.fileSize() != null && photo.fileSize() > ReceiptFiles.MAX_BYTES) {
            throw ReceiptFiles.invalid();
        }
        return photo.fileId();
    }

    private void choices(Upload upload, PlayerPadel player, int page, String key) {
        var rows = registrations.choices(player.getId());
        int pages = Math.max(1, (rows.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (upload.expired() || page < 0 || page >= pages || rows.isEmpty()) {
            throw ReceiptRegistrations.unavailable();
        }
        var choices = IntStream.range(page * PAGE_SIZE, Math.min(rows.size(), (page + 1) * PAGE_SIZE))
                .mapToObj(i -> List.of(choice(upload, rows.get(i)))).toList();
        var navigation = IntStream.rangeClosed(Math.max(0, page - 1), Math.min(pages - 1, page + 1))
                .filter(i -> i != page).mapToObj(i -> Button.action(Integer.toString(i + 1),
                        "p:" + compact(upload.id()) + ":" + i + ":page")).toList();
        var keyboard = navigation.isEmpty() ? choices : Stream.concat(choices.stream(), Stream.of(navigation)).toList();
        delivery.sendOnce(key, null, null, upload.chatId(), messages.text("receipt.choose", player.getLocale()), keyboard);
    }

    private Button choice(Upload upload, TournamentRegistration row) {
        var tournament = row.getTournament();
        String label = tournament.getNombre() + " · " + tournament.getFechaHoraInicioCompleta()
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        return Button.action(label, "q:" + compact(upload.id()) + ":" + number(row.getId()) + ":" + number(row.getCancellationVersion()));
    }

    private Upload requireUpload(String value, PlayerPadel player, long chat) {
        var id = UUID.fromString(value.replaceFirst("([0-9a-f]{8})([0-9a-f]{4})([0-9a-f]{4})([0-9a-f]{4})([0-9a-f]{12})", "$1-$2-$3-$4-$5"));
        var upload = receipts.upload(id, player.getId(), chat, false);
        if (upload.expired() && upload.receiptId() == null) {
            throw ReceiptRegistrations.unavailable();
        }
        return upload;
    }

    private Optional<PlayerPadel> player(long user) {
        return connections.byUser(user).flatMap(c -> players.findById(c.playerId()))
                .filter(PlayerPadel::isActivo).filter(p -> !p.isGuestAccount());
    }

    private Locale locale(Optional<PlayerPadel> player) {
        return player.map(PlayerPadel::getLocale).orElse(Locale.forLanguageTag("es"));
    }

    private void reply(String key, PlayerPadel player, String text, Object... args) {
        connections.connection(player.getId()).filter(Connection::enabled).ifPresent(c -> delivery.sendOnce(
                key, null, null, c.chatId(), messages.text(text, player.getLocale(), args), List.of()));
    }

    private static String number(long value) { return Long.toString(value, 36); }
    private static long parse(String value) { return Long.parseLong(value, 36); }
    private static String compact(UUID value) { return value.toString().replace("-", ""); }
}
