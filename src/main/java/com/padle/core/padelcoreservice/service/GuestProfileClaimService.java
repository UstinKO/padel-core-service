package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.config.GuestClaimContact;
import com.padle.core.padelcoreservice.dto.GuestProfileClaimDtos.*;
import com.padle.core.padelcoreservice.exception.AccountException;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.repository.GuestProfileClaimRepository;
import com.padle.core.padelcoreservice.repository.GuestProfileHistoryRepository;
import com.padle.core.padelcoreservice.repository.PasswordResetTokenRepository;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GuestProfileClaimService {
    private final PlayerRepository players;
    private final GuestClaimContact claimContact;
    private final GuestProfileClaimRepository claims;
    private final GuestProfileHistoryRepository history;
    private final PasswordResetTokenRepository resetTokens;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public RequestReceipt request(PlayerPadel account, Request input) {
        if (account.isGuestAccount()) {
            throw new AccountException(HttpStatus.FORBIDDEN, "PLAYER_REQUIRED");
        }
        String phone = phoneKey(input.phone());
        String whatsappUrl = claimContact.url().orElseThrow(() ->
                new AccountException(HttpStatus.SERVICE_UNAVAILABLE, "GUEST_CLAIM_CONTACT_UNAVAILABLE"));
        UUID reference = claims.saveRequest(account.getId(), phone);
        return new RequestReceipt(reference, whatsappUrl);
    }

    public Review review(UUID reference) {
        var request = claims.request(reference, false).orElseThrow(GuestProfileClaimService::missing);
        var account = players.findById(request.accountId()).orElseThrow(GuestProfileClaimService::missing);
        return new Review(account.getId(), account.getEmail(), request.resolvedPlayerId() != null);
    }

    @Transactional
    public Result approve(UUID reference, Approval input, long ownerId) {
        String phone = lockPhone(input.whatsappSenderPhone());
        var request = claims.request(reference, true).orElseThrow(GuestProfileClaimService::missing);
        if (!request.phone().equals(phone)) {
            throw conflict("GUEST_CLAIM_PHONE_MISMATCH");
        }
        if (request.resolvedPlayerId() != null) {
            return new Result(request.resolvedPlayerId());
        }
        PlayerPadel guest = findGuest(phone, request.accountId());
        var locked = players.lockPlayers(List.of(guest.getId(), request.accountId()));
        var account = locked.stream().filter(player -> player.getId() == request.accountId() && player.isActivo()
                        && !player.isGuestAccount()).findFirst().orElseThrow(GuestProfileClaimService::missing);
        if (!Objects.equals(findGuest(phone, account.getId()).getId(), guest.getId())) {
            throw conflict("GUEST_CLAIM_PHONE_MISMATCH");
        }
        if (history.hasConflict(guest.getId(), account.getId())) {
            throw conflict("GUEST_CLAIM_HISTORY_CONFLICT");
        }
        history.transfer(guest.getId(), account.getId(), account.getEmail());
        if (account.getTelefono() == null || account.getTelefono().isBlank()) {
            account.setTelefono(guest.getTelefono());
        }
        retireGuest(guest);
        claims.approve(reference, guest.getId(), account.getId(), ownerId);
        return new Result(account.getId());
    }

    @Transactional
    public Result activate(long guestId, Activation input, long ownerId) {
        String phone = lockPhone(input.whatsappSenderPhone());
        PlayerPadel profile = players.lockPlayers(List.of(guestId)).stream().findFirst()
                .orElseThrow(GuestProfileClaimService::missing);
        String email = input.email().strip().toLowerCase(Locale.ROOT);
        if (claims.resolved(guestId).filter(id -> id == guestId).isPresent()
                && profile.getEmail().equalsIgnoreCase(email) && players.claimPhoneKey(profile.getTelefono()).filter(phone::equals).isPresent()) {
            return new Result(guestId);
        }
        if (!Objects.equals(findGuest(phone, null).getId(), guestId)) {
            throw conflict("GUEST_CLAIM_PHONE_MISMATCH");
        }
        players.acquireContactLock("player-email:" + email);
        if (PlayerPadel.isGuestEmail(email) || players.existsByEmailIgnoreCase(email)) {
            throw conflict("GUEST_CLAIM_EMAIL_IN_USE");
        }
        revokeCredentials(profile);
        profile.setEmail(email);
        profile.confirmarEmail();
        claims.activate(guestId, phone, ownerId);
        history.updatePartnerEmail(guestId, email);
        return new Result(guestId);
    }

    private PlayerPadel findGuest(String phone, Long allowedAccountId) {
        var matches = players.findClaimPhoneMatches(phone).stream()
                .filter(player -> !Objects.equals(player.getId(), allowedAccountId)).toList();
        if (matches.size() != 1 || !matches.get(0).isGuestAccount()) {
            throw conflict("GUEST_CLAIM_CONTACT_CONFLICT");
        }
        return matches.get(0);
    }

    private String lockPhone(String input) {
        String key = phoneKey(input);
        players.acquireContactLock(PlayerPhoneGuard.LOCK_PREFIX + key);
        return key;
    }

    private String phoneKey(String input) {
        return players.claimPhoneKey(input).orElseThrow(() -> conflict("GUEST_CLAIM_PHONE_INVALID"));
    }

    private void retireGuest(PlayerPadel guest) {
        revokeCredentials(guest);
        guest.setTelefono(null);
        guest.setTelegramUsername(null);
        guest.setTelegramChatId(null);
        guest.setActivo(false);
    }

    private void revokeCredentials(PlayerPadel profile) {
        resetTokens.deleteByPlayer(profile);
        profile.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        profile.setPasswordChangedAt(LocalDateTime.now());
        profile.setCodigoConfirmacion(null);
    }

    private static AccountException missing() {
        return new AccountException(HttpStatus.NOT_FOUND, "GUEST_CLAIM_NOT_FOUND");
    }

    private static AccountException conflict(String code) {
        return new AccountException(HttpStatus.CONFLICT, code);
    }
}
