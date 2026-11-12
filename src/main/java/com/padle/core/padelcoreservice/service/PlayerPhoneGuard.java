package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.config.GuestClaimContact;
import com.padle.core.padelcoreservice.exception.ClaimPhoneInUseException;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
class PlayerPhoneGuard {
    static final String LOCK_PREFIX = "partner-phone:";

    private final PlayerRepository players;
    private final GuestClaimContact claimContact;
    private final MessageSource messages;

    void requireAvailable(String phone, Long currentId) {
        if (phone == null || phone.isBlank()) {
            return;
        }
        boolean used = players.claimPhoneKey(phone).map(key -> {
            players.acquireContactLock(LOCK_PREFIX + key);
            return players.findClaimPhoneMatches(key).stream()
                    .anyMatch(player -> !Objects.equals(player.getId(), currentId));
        }).orElseGet(() -> players.existsByTelefono(phone));
        if (used) {
            throw new ClaimPhoneInUseException(messages.getMessage("account.PHONE_IN_USE", null, LocaleContextHolder.getLocale()),
                    claimContact.url());
        }
    }
}
