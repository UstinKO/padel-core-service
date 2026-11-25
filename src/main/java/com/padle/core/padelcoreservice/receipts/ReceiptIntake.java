package com.padle.core.padelcoreservice.receipts;

import com.padle.core.padelcoreservice.receipts.ReceiptModels.File;
import com.padle.core.padelcoreservice.receipts.ReceiptModels.Receipt;
import com.padle.core.padelcoreservice.receipts.ReceiptModels.Upload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReceiptIntake {
    private final ReceiptStore store;
    private final ReceiptRegistrations registrations;

    @Transactional
    public Upload receive(long update, long player, long chat, String fileId) {
        if (registrations.choices(player).isEmpty() || fileId == null || fileId.length() > 1024) {
            throw ReceiptRegistrations.unavailable();
        }
        return store.pending(update, player, chat, fileId);
    }

    @Transactional
    public Receipt attach(UUID uploadId, long player, long chat, long registration, long version, File file) {
        var candidate = registrations.require(registration, player, version);
        long event = candidate.getTournament().getId();
        var row = registrations.lock(event, registration, player, version);
        var upload = store.upload(uploadId, player, chat, true);
        if (upload.receiptId() != null) {
            var existing = store.require(event, upload.receiptId(), false);
            if (existing.registrationId() != registration || existing.registrationVersion() != version) {
                throw ReceiptRegistrations.unavailable();
            }
            return existing;
        }
        if (upload.expired()) {
            throw ReceiptRegistrations.unavailable();
        }
        var checked = ReceiptFiles.validate(file.content());
        var id = store.save(upload, row, checked);
        return store.require(event, id, false);
    }
}
