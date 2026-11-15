package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.config.GuestClaimContact;
import com.padle.core.padelcoreservice.dto.GuestProfileClaimDtos.*;
import com.padle.core.padelcoreservice.exception.AccountException;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.repository.GuestProfileClaimRepository;
import com.padle.core.padelcoreservice.repository.GuestProfileClaimRepository.Claim;
import com.padle.core.padelcoreservice.repository.GuestProfileHistoryRepository;
import com.padle.core.padelcoreservice.repository.PasswordResetTokenRepository;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
        String phone = lockPhone(input.phone());
        var existing = claims.matchingRequest(account.getId(), phone, false);
        return receipt(existing.orElseGet(() -> claims.saveRequest(account.getId(), phone)), account);
    }

    public List<RequestReceipt> ownRequests(PlayerPadel account) {
        return claims.forAccount(account.getId()).stream().map(claim -> receipt(claim, account)).toList();
    }

    private RequestReceipt receipt(Claim claim, PlayerPadel account) {
        return new RequestReceipt(claim.reference(), claim.number(), claimContact.url().orElse(null),
                account.getNombreCompleto(), claim.status(), claim.createdAt());
    }

    public Queue queue(String search, Status status, int page) {
        String query = search.strip();
        int pageSize = 30;
        var rows = claims.queue(query, players.claimPhoneKey(query).orElse(null), status, pageSize + 1,
                (long) page * pageSize);
        return new Queue(claims.pendingCount(), rows.stream().limit(pageSize).toList(), rows.size() > pageSize);
    }

    public Review review(UUID reference) {
        var request = claims.request(reference, false).orElseThrow(GuestProfileClaimService::missing);
        var account = players.findById(request.accountId()).orElseThrow(GuestProfileClaimService::missing);
        if (request.status() == Status.MERGED || request.status() == Status.REJECTED) {
            var guest = request.guestId() == null ? null : players.findById(request.guestId()).orElse(null);
            return comparison(request.number(), request.status(), request.phone(), account, guest, List.of(), request.reviewReason());
        }
        var candidates = candidates(request.phone(), account.getId());
        var guest = candidates.size() == 1 && candidates.get(0).isGuestAccount() ? candidates.get(0) : null;
        var blockers = guest == null ? List.of("GUEST_CLAIM_CONTACT_CONFLICT") : blockers(guest, account, request.phone());
        return comparison(request.number(), request.status(), request.phone(), account, guest, blockers, request.reviewReason());
    }

    public List<ProfileChoice> profileChoices(String search, boolean guest) {
        return claims.profileChoices(search.strip(), guest);
    }

    public Review manualReview(long guestId, long accountId) {
        var guest = players.findById(guestId).orElseThrow(GuestProfileClaimService::missing);
        var account = players.findById(accountId).orElseThrow(GuestProfileClaimService::missing);
        var previous = claims.resolved(guestId);
        if (previous.filter(id -> id == accountId && guestId != accountId).isPresent()) {
            return comparison(0, Status.MERGED, null, account, guest, List.of(), null);
        }
        String phone = players.claimPhoneKey(guest.getTelefono()).orElse(null);
        return comparison(0, Status.PENDING, phone, account, guest, blockers(guest, account, phone), null);
    }

    private Review comparison(long number, Status status, String phone, PlayerPadel account, PlayerPadel guest,
                              List<String> blockers, String reviewReason) {
        var differences = new ArrayList<String>();
        if (guest != null) {
            if (differentPhone(guest, account)) differences.add("PHONE_DIFFERS");
            if (guest.getNivelJugador() != null && account.getNivelJugador() != guest.getNivelJugador()) {
                differences.add("LEVEL_DIFFERS");
            }
            if (hasText(guest.getTelegramUsername())
                    && !Objects.equals(guest.getTelegramUsername(), account.getTelegramUsername())) {
                differences.add("TELEGRAM_DIFFERS");
            }
        }
        return new Review(account.getId(), account.getEmail(), status == Status.MERGED, number, status, phone,
                profile(account), guest == null ? null : profile(guest), blockers, List.copyOf(differences), reviewReason);
    }

    private Profile profile(PlayerPadel player) {
        var counts = history.counts(player.getId());
        return new Profile(player.getId(), player.getNombreCompleto(), player.getEmail(), player.getTelefono(),
                player.getTelegramUsername(), player.getNivelJugador() == null ? null : player.getNivelJugador().name(),
                counts.registrations(), counts.results());
    }

    @Transactional
    public Review decide(UUID reference, ReviewDecision input, long ownerId) {
        var claim = claims.request(reference, true).orElseThrow(GuestProfileClaimService::missing);
        if (claim.status() == Status.MERGED || claim.status() == Status.REJECTED) {
            if (claim.status().name().equals(input.decision().name())) return review(reference);
            throw conflict("GUEST_CLAIM_ALREADY_DECIDED");
        }
        claims.decide(reference, Status.valueOf(input.decision().name()), ownerId, null);
        return review(reference);
    }

    @Transactional(noRollbackFor = NeedsDetails.class)
    public Result approve(UUID reference, Approval input, long ownerId) {
        requireVerified(input.verified());
        var method = input.verificationMethod() == null ? Verification.WHATSAPP : input.verificationMethod();
        // Сначала блокируем телефон: ручное подтверждение и подтверждение заявки используют тот же порядок.
        var snapshot = claims.request(reference, false).orElseThrow(GuestProfileClaimService::missing);
        String phone = lockPhone(snapshot.phone());
        var request = claims.request(reference, true).orElseThrow(GuestProfileClaimService::missing);
        if (request.status() == Status.MERGED) return new Result(request.resolvedPlayerId());
        if (request.status() == Status.REJECTED) throw conflict("GUEST_CLAIM_ALREADY_DECIDED");
        requireSender(reference, method, input.whatsappSenderPhone(), phone, ownerId);
        var matches = candidates(phone, request.accountId());
        if (matches.size() != 1 || !matches.get(0).isGuestAccount()) {
            throw needDetails(reference, ownerId, "GUEST_CLAIM_CONTACT_CONFLICT");
        }
        long guestId = matches.get(0).getId();
        if (input.guestId() != null && input.guestId() != guestId) {
            throw needDetails(reference, ownerId, "GUEST_CLAIM_CONTACT_CONFLICT");
        }
        var profiles = lockPair(guestId, request.accountId());
        var blockers = blockers(profiles.guest(), profiles.account(), phone);
        if (!blockers.isEmpty()) throw needDetails(reference, ownerId, blockers.get(0));
        if (differentPhone(profiles.guest(), profiles.account()) && !input.replacePhone()) {
            throw needDetails(reference, ownerId, "GUEST_CLAIM_ACCOUNT_PHONE_DIFFERS");
        }
        merge(profiles);
        claims.approve(reference, guestId, request.accountId(), ownerId, method, false);
        return new Result(request.accountId());
    }

    @Transactional
    public Result manualMerge(ManualMerge input, long ownerId) {
        requireVerified(input.verified());
        if (input.guestId() == input.accountId()) throw conflict("GUEST_CLAIM_PROFILE_CONFLICT");
        var guest = players.findById(input.guestId()).orElseThrow(GuestProfileClaimService::missing);
        var previous = claims.resolved(input.guestId());
        if (previous.filter(id -> id == input.accountId()).isPresent()) return new Result(input.accountId());
        String phone = lockPhone(guest.getTelefono());
        var openClaim = claims.matchingRequest(input.accountId(), phone, true)
                .filter(claim -> claim.status() == Status.PENDING || claim.status() == Status.NEEDS_DETAILS);
        var pair = lockPair(input.guestId(), input.accountId());
        previous = claims.resolved(input.guestId());
        if (previous.filter(id -> id == input.accountId()).isPresent()) return new Result(input.accountId());
        requireSender(null, input.verificationMethod(), input.whatsappSenderPhone(), phone, ownerId);
        var blockers = blockers(pair.guest(), pair.account(), phone);
        if (!blockers.isEmpty()) throw conflict(blockers.get(0));
        if (differentPhone(pair.guest(), pair.account()) && !input.replacePhone()) {
            throw conflict("GUEST_CLAIM_ACCOUNT_PHONE_DIFFERS");
        }
        merge(pair);
        if (openClaim.isPresent()) {
            claims.approve(openClaim.get().reference(), input.guestId(), input.accountId(), ownerId,
                    input.verificationMethod(), true);
        } else {
            claims.recordManual(input.guestId(), input.accountId(), input.accountId(), phone, ownerId,
                    input.verificationMethod());
        }
        return new Result(input.accountId());
    }

    private void requireVerified(Boolean verified) {
        if (!Boolean.TRUE.equals(verified)) throw conflict("GUEST_CLAIM_VERIFICATION_REQUIRED");
    }

    private void requireSender(UUID reference, Verification method, String sender, String phone, long ownerId) {
        if (method == Verification.WHATSAPP && !Objects.equals(players.claimPhoneKey(sender).orElse(null), phone)) {
            if (reference == null) throw conflict("GUEST_CLAIM_PHONE_MISMATCH");
            throw needDetails(reference, ownerId, "GUEST_CLAIM_PHONE_MISMATCH");
        }
    }

    private NeedsDetails needDetails(UUID reference, long ownerId, String reason) {
        claims.decide(reference, Status.NEEDS_DETAILS, ownerId, reason);
        return new NeedsDetails(reason);
    }

    private List<String> blockers(PlayerPadel guest, PlayerPadel account, String phone) {
        if (Objects.equals(guest.getId(), account.getId()) || !guest.isActivo() || !guest.isGuestAccount()
                || !account.isActivo() || account.isGuestAccount()) {
            return List.of("GUEST_CLAIM_PROFILE_CONFLICT");
        }
        if (phone == null || !players.claimPhoneKey(guest.getTelefono()).filter(phone::equals).isPresent()
                || !candidates(phone, account.getId()).stream().map(PlayerPadel::getId).toList().equals(List.of(guest.getId()))) {
            return List.of("GUEST_CLAIM_CONTACT_CONFLICT");
        }
        return history.conflicts(guest.getId(), account.getId());
    }

    private List<PlayerPadel> candidates(String phone, Long accountId) {
        return players.findClaimPhoneMatches(phone).stream()
                .filter(player -> !Objects.equals(player.getId(), accountId)).toList();
    }

    private ProfilePair lockPair(long guestId, long accountId) {
        var locked = players.lockPlayers(List.of(guestId, accountId));
        return new ProfilePair(locked.stream().filter(p -> p.getId() == guestId).findFirst()
                        .orElseThrow(GuestProfileClaimService::missing),
                locked.stream().filter(p -> p.getId() == accountId).findFirst()
                        .orElseThrow(GuestProfileClaimService::missing));
    }

    private void merge(ProfilePair pair) {
        var guest = pair.guest();
        var account = pair.account();
        history.transfer(guest.getId(), account.getId(), account.getEmail());
        var phone = guest.getTelefono();
        retireGuest(guest);
        players.flush(); // Освобождаем уникальный контакт гостя до записи его основному аккаунту.
        account.setTelefono(phone);
    }

    private boolean differentPhone(PlayerPadel guest, PlayerPadel account) {
        return hasText(account.getTelefono()) && !Objects.equals(players.claimPhoneKey(guest.getTelefono()).orElse(null),
                players.claimPhoneKey(account.getTelefono()).orElse(null));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record ProfilePair(PlayerPadel guest, PlayerPadel account) {}

    // Только проверенные отказы до переноса фиксируют статус уточнения.
    private static final class NeedsDetails extends AccountException {
        private NeedsDetails(String code) {
            super(HttpStatus.CONFLICT, code);
        }
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
        claims.recordManual(guestId, null, guestId, phone, ownerId, Verification.WHATSAPP);
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
