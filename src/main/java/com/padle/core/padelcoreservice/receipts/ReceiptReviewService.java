package com.padle.core.padelcoreservice.receipts;

import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.receipts.ReceiptModels.*;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import com.padle.core.padelcoreservice.service.PaymentService;
import com.padle.core.padelcoreservice.service.TournamentAccessService;
import com.padle.core.padelcoreservice.telegram.PlayerTelegramDelivery;
import com.padle.core.padelcoreservice.telegram.PlayerTelegramMessages;
import com.padle.core.padelcoreservice.telegram.PlayerTelegramStore;
import com.padle.core.padelcoreservice.util.AfterCommit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReceiptReviewService {
    private final TournamentAccessService access;
    private final ReceiptStore store;
    private final ReceiptRegistrations registrations;
    private final PaymentService payments;
    private final PlayerRepository players;
    private final PlayerTelegramStore connections;
    private final PlayerTelegramDelivery delivery;
    private final PlayerTelegramMessages messages;

    public List<Receipt> list(Owner owner, long tournament) {
        access.assertCanManageTournament(owner, tournament);
        return store.list(tournament);
    }

    public File file(Owner owner, long tournament, UUID id) {
        access.assertCanManageTournament(owner, tournament);
        store.require(tournament, id, false);
        return store.file(id).orElseThrow(ReceiptRegistrations::unavailable);
    }

    @Transactional
    public Receipt accept(Owner owner, long tournament, UUID id, Accept decision) {
        var receipt = lock(owner, tournament, id);
        if (receipt.state() == State.ACCEPTED) {
            return receipt;
        }
        pending(receipt);
        var row = registrations.require(receipt.registrationId(), receipt.playerId(), receipt.registrationVersion());
        long paymentId = payments.confirmReceiptPayment(row, decision, owner.getId());
        store.review(id, State.ACCEPTED, owner.getId(), paymentId, null);
        notifyPlayer(tournament, store.require(tournament, id, false));
        return store.require(tournament, id, false);
    }

    @Transactional
    public Receipt reject(Owner owner, long tournament, UUID id, Reject decision) {
        var receipt = lock(owner, tournament, id);
        if (receipt.state() == State.REJECTED) {
            return receipt;
        }
        pending(receipt);
        String reason = decision.reason().strip();
        if (reason.isEmpty() || reason.length() > 500) {
            throw ReceiptRegistrations.unavailable();
        }
        store.review(id, State.REJECTED, owner.getId(), null, reason);
        notifyPlayer(tournament, store.require(tournament, id, false));
        return store.require(tournament, id, false);
    }

    private Receipt lock(Owner owner, long tournament, UUID id) {
        access.assertCanManageTournament(owner, tournament);
        var receipt = store.require(tournament, id, false);
        // Сначала турнир/регистрация, затем чек: тот же порядок, что у приёма и сохранения платежей.
        if (receipt.state() == State.PENDING) {
            registrations.lock(tournament, receipt.registrationId(), receipt.playerId(), receipt.registrationVersion());
        }
        return store.require(tournament, id, true);
    }

    private void pending(Receipt receipt) {
        if (receipt.state() != State.PENDING || !receipt.currentCycle()) {
            throw ReceiptRegistrations.unavailable();
        }
    }

    private void notifyPlayer(long tournament, Receipt receipt) {
        var player = players.findById(receipt.playerId()).orElseThrow(ReceiptRegistrations::unavailable);
        var locale = player.getLocale();
        var text = receipt.state() == State.ACCEPTED
                ? messages.text("receipt.accepted", locale, receipt.eventName())
                : messages.text("receipt.rejected", locale, receipt.eventName(), receipt.rejectionReason());
        AfterCommit.run(() -> connections.connection(receipt.playerId()).filter(c -> c.enabled()).ifPresent(c ->
                delivery.sendOnce("receipt-review:" + receipt.id(), tournament, receipt.registrationId(),
                        c.chatId(), text, List.of())));
    }
}
