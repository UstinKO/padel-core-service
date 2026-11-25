package com.padle.core.padelcoreservice.receipts;

import com.padle.core.padelcoreservice.exception.AccountException;
import org.springframework.http.HttpStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

public final class ReceiptFiles {
    public static final int MAX_BYTES = 5 * 1024 * 1024;
    private ReceiptFiles() {}

    public static ReceiptModels.File validate(byte[] bytes) {
        if (bytes.length == 0 || bytes.length > MAX_BYTES) {
            throw invalid();
        }
        String type = type(bytes);
        return new ReceiptModels.File(type, bytes);
    }

    private static String type(byte[] bytes) {
        if (bytes.length >= 8 && Arrays.equals(Arrays.copyOf(bytes, 8),
                new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10})) {
            return "image/png";
        }
        if (bytes.length >= 3 && bytes[0] == (byte) 255 && bytes[1] == (byte) 216 && bytes[2] == (byte) 255) {
            return "image/jpeg";
        }
        if (bytes.length >= 5 && new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) {
            return "application/pdf";
        }
        throw invalid();
    }

    public static String hash(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 недоступен", error);
        }
    }

    public static AccountException invalid() {
        return new AccountException(HttpStatus.UNPROCESSABLE_ENTITY, "RECEIPT_INVALID_FILE");
    }
}
