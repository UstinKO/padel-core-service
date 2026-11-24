package com.padle.core.padelcoreservice.receipts;

import com.padle.core.padelcoreservice.exception.AccountException;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;

class ReceiptFilesTest {
    @Test void identifiesBytesInsteadOfTrustingTheFilenameOrTelegramMime() {
        assertThat(ReceiptFiles.validate(new byte[]{(byte)255,(byte)216,(byte)255,0}).contentType()).isEqualTo("image/jpeg");
        assertThat(ReceiptFiles.validate(new byte[]{(byte)137,80,78,71,13,10,26,10}).contentType()).isEqualTo("image/png");
        assertThat(ReceiptFiles.validate("%PDF-1.7\nfixture".getBytes(StandardCharsets.US_ASCII)).contentType()).isEqualTo("application/pdf");
        assertThatThrownBy(() -> ReceiptFiles.validate("<html>not a receipt</html>".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(AccountException.class).hasMessage("RECEIPT_INVALID_FILE");
    }
    @Test void emptyAndOversizedFilesCannotEnterStorage() {
        assertThatThrownBy(() -> ReceiptFiles.validate(new byte[0])).isInstanceOf(AccountException.class);
        assertThatThrownBy(() -> ReceiptFiles.validate(new byte[ReceiptFiles.MAX_BYTES + 1])).isInstanceOf(AccountException.class);
    }
    @Test void deduplicationUsesTheActualContent() {
        assertThat(ReceiptFiles.hash(new byte[]{1,2,3})).hasSize(64).isEqualTo(ReceiptFiles.hash(new byte[]{1,2,3}))
                .isNotEqualTo(ReceiptFiles.hash(new byte[]{1,2,4}));
    }
}
