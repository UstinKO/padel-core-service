package com.padle.core.application.identity;

import java.util.List;
import java.util.Optional;

public interface PlayerContacts {

    Optional<String> claimPhoneKey(String phone);

    void acquireContactLock(String key);

    List<Long> findActiveIdsByClaimPhoneKey(String key);

    boolean existsByPhone(String phone);

    static boolean hasValidContact(String phone, String telegram) {
        return phone != null && !phone.isBlank() || telegram != null && !telegram.isBlank();
    }
}
