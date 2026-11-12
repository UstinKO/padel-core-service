package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.TournamentRegistration;
import com.padle.core.padelcoreservice.util.AfterCommit;

import java.util.Objects;
import java.util.Optional;

final class GuestPartnerNotice {
    enum Kind { CONFIRMED, WAITLIST, CANCELLED, REMINDER }

    private GuestPartnerNotice() {}

    static void forward(EmailService email, TournamentRegistration registration, String eventName, Kind kind) {
        var guest = registration.getPlayer();
        var bookerId = Optional.ofNullable(registration.getPairBookedBy()).orElse(registration.getMainPlayerId());
        Optional.ofNullable(registration.getPartner())
                .filter(partner -> Objects.equals(bookerId, partner.getId()))
                .filter(PlayerPadel::isActivo)
                .filter(partner -> !partner.isGuestAccount() && partner.getEmail() != null && !partner.getEmail().isBlank())
                .ifPresent(booker -> {
                    String recipient = booker.getEmail();
                    String partnerName = guest.getNombreCompleto();
                    var locale = booker.getLocale();
                    AfterCommit.run(() -> email.sendGuestPartnerNotice(recipient, partnerName, eventName, kind, locale));
                });
    }
}
