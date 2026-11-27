package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.config.GuestClaimContact;
import com.padle.core.padelcoreservice.exception.ClaimPhoneInUseException;
import com.padle.core.application.identity.PlayerContacts;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
class PlayerPhoneGuard {
    static final String LOCK_PREFIX = "partner-phone:";

    private final PlayerContacts players;
    private final GuestClaimContact claimContact;
    private final MessageSource messages;

    void requireAvailable(String phone, Long currentId) {
        if (phone == null || phone.isBlank()) {
            return;
        }
        boolean used = players.claimPhoneKey(phone).map(key -> {
            players.acquireContactLock(LOCK_PREFIX + key);
            return players.findActiveIdsByClaimPhoneKey(key).stream()
                    .anyMatch(playerId -> !Objects.equals(playerId, currentId));
        }).orElseGet(() -> players.existsByPhone(phone));
        if (used) {
            throw new ClaimPhoneInUseException(messages.getMessage("account.PHONE_IN_USE", null, LocaleContextHolder.getLocale()),
                    claimContact.url());
        }
    }
}
