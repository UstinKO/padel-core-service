package com.padle.core.application.identity;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PasswordResetTokens {
    record Token(Long id, Long playerId, LocalDateTime expiryDate, boolean used) {
    }

    void issue(Long playerId, String token, LocalDateTime expiryDate);

    Optional<Token> findByToken(String token);

    Optional<Token> lockByToken(String token);

    void markUsed(Long id);

    void revokeForPlayer(Long playerId);

    void deleteExpiredOrUsed(LocalDateTime now);
}
