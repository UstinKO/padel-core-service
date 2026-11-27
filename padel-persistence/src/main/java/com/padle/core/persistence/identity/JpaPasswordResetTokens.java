package com.padle.core.persistence.identity;

import com.padle.core.application.identity.PasswordResetTokens;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JpaPasswordResetTokens implements PasswordResetTokens {
    private final PasswordResetTokenRepository rows;
    private final PasswordResetTokenMapper mapper;

    @Override
    public void issue(Long playerId, String token, LocalDateTime expiryDate) {
        rows.save(PasswordResetToken.builder().playerId(playerId).token(token).expiryDate(expiryDate).build());
    }

    @Override
    public Optional<Token> findByToken(String token) {
        return rows.findByToken(token).map(mapper::toToken);
    }

    @Override
    public Optional<Token> lockByToken(String token) {
        return rows.findForUpdateByToken(token).map(mapper::toToken);
    }

    @Override
    public void markUsed(Long id) {
        var row = rows.getReferenceById(id);
        row.setUsed(true);
        rows.save(row);
    }

    @Override
    public void revokeForPlayer(Long playerId) {
        rows.deleteByPlayerId(playerId);
    }

    @Override
    public void deleteExpiredOrUsed(LocalDateTime now) {
        rows.deleteAllExpiredOrUsed(now);
    }
}
