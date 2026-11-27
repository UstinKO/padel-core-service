package com.padle.core.application.identity;

import java.util.List;

public interface GuestProfileHistory {

    List<String> conflicts(long guestId, long accountId);

    Counts counts(long playerId);

    void transfer(long guestId, long accountId, String accountEmail);

    void updatePartnerEmail(long accountId, String accountEmail);

    record Counts(long registrations, long results) {}
}
