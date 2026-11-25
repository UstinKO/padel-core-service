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
import java.util.Optional;
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
        var claim = existing.orElseGet(() -> claims.saveRequest(account.getId(), phone));
        if (!claim.phone().equals(phone)) {
            throw conflict("GUEST_CLAIM_OPEN_FOR_OTHER_PHONE");
        }
        return receipt(claim, account);
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
        var blockers = guest == null ? List.of(candidateFailure(candidates)) : blockers(guest, account, request.phone());
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
        return comparison(0, Status.PENDING, phone, account, guest, profileBlockers(guest, account), null);
    }

    private Review comparison(long number, Status status, String phone, PlayerPadel account, PlayerPadel guest,
                              List<String> blockers, String reviewReason) {
        var differences = new ArrayList<String>();
        if (guest != null) {
            if (!hasText(guest.getTelefono())) differences.add("GUEST_PHONE_MISSING");
            else if (players.claimPhoneKey(guest.getTelefono()).isEmpty()) differences.add("GUEST_PHONE_INVALID");
            else if (candidates(players.claimPhoneKey(guest.getTelefono()).orElseThrow(), account.getId()).size() > 1) {
                differences.add("GUEST_PHONE_AMBIGUOUS");
            }
            if (differentPhone(guest, account)) differences.add("PHONE_DIFFERS");
            if (sideConflict(guest, account)) differences.add("PLAYING_SIDE_DIFFERS");
            if (guest.getNivelJugador() != null && account.getNivelJugador() != guest.getNivelJugador()) {
                differences.add("LEVEL_DIFFERS");
            }
            if (hasText(guest.getTelegramUsername())
                    && !Objects.equals(guest.getTelegramUsername(), account.getTelegramUsername())) {
                differences.add("TELEGRAM_DIFFERS");
            }
        }
        return new Review(account.getId(), account.getEmail(), status == Status.MERGED, number, status, phone,
                profile(account), guest == null ? null : profile(guest), blockers, List.copyOf(differences), reviewReason,
                readiness(status, guest, blockers, differences));
    }

    private Readiness readiness(Status status, PlayerPadel guest, List<String> blockers, List<String> differences) {
        if (status == Status.MERGED) return Readiness.COMPLETED;
        if (status == Status.REJECTED || guest == null || !blockers.isEmpty()) return Readiness.FORBIDDEN;
        return differences.stream().anyMatch(List.of("PHONE_DIFFERS", "GUEST_PHONE_INVALID", "GUEST_PHONE_MISSING",
                "PLAYING_SIDE_DIFFERS", "GUEST_PHONE_AMBIGUOUS")::contains) ? Readiness.NEEDS_RESOLUTION : Readiness.AVAILABLE;
    }

    private Profile profile(PlayerPadel player) {
        var counts = history.counts(player.getId());
        return new Profile(player.getId(), player.getNombreCompleto(), player.getEmail(), player.getTelefono(),
                player.getTelegramUsername(), player.getNivelJugador() == null ? null : player.getNivelJugador().name(),
                counts.registrations(), counts.results(), player.getPlayingSide(), player.isGuestAccount(), player.isActivo());
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
        // Старая форма уже требует вручную ввести фактический номер отправителя.
        // Новая форма также передаёт явную отметку и способ проверки.
        boolean legacyForm = input.verified() == null && input.verificationMethod() == null
                && input.guestId() == null && !input.replacePhone() && hasText(input.whatsappSenderPhone());
        if (!legacyForm) requireVerified(input.verified());
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
            throw needDetails(reference, ownerId, candidateFailure(matches));
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
        merge(profiles, input.sideChoice());
        claims.approve(reference, guestId, request.accountId(), ownerId, method, false);
        return new Result(request.accountId());
    }

    @Transactional
    public Result manualMerge(ManualMerge input, long ownerId) {
        requireVerified(input.verified());
        if (input.guestId() == input.accountId()) throw conflict("GUEST_CLAIM_SAME_PROFILE");
        var guest = players.findById(input.guestId()).orElseThrow(GuestProfileClaimService::missing);
        var previous = claims.resolved(input.guestId());
        if (previous.isPresent()) return alreadyMerged(previous.orElseThrow(), input.accountId());
        String phone = players.claimPhoneKey(guest.getTelefono()).orElse(null);
        if (phone != null) players.acquireContactLock(PlayerPhoneGuard.LOCK_PREFIX + phone);
        var openClaim = phone == null ? Optional.<Claim>empty()
                : claims.matchingRequest(input.accountId(), phone, true)
                    .filter(claim -> claim.status() == Status.PENDING || claim.status() == Status.NEEDS_DETAILS);
        var pair = lockPair(input.guestId(), input.accountId());
        previous = claims.resolved(input.guestId());
        if (previous.isPresent()) return alreadyMerged(previous.orElseThrow(), input.accountId());
        if (!Objects.equals(phone, players.claimPhoneKey(pair.guest().getTelefono()).orElse(null))) {
            throw conflict("GUEST_CLAIM_REVIEW_CHANGED");
        }
        var blockers = profileBlockers(pair.guest(), pair.account());
        if (!blockers.isEmpty()) throw conflict(blockers.get(0));
        ContactChoice choice = manualContact(input, pair, phone);
        if (input.verificationMethod() == Verification.WHATSAPP && phone == null) {
            throw conflict("GUEST_CLAIM_PHONE_VERIFICATION_UNAVAILABLE");
        }
        requireSender(null, input.verificationMethod(), input.whatsappSenderPhone(), phone, ownerId);
        String keptPhone = choice == ContactChoice.KEEP_ACCOUNT ? pair.account().getTelefono() : pair.guest().getTelefono();
        merge(pair, input.sideChoice(), keptPhone);
        if (openClaim.isPresent()) {
            claims.approve(openClaim.orElseThrow().reference(), input.guestId(), input.accountId(), ownerId,
                    input.verificationMethod(), true);
        } else {
            claims.recordManual(input.guestId(), input.accountId(), input.accountId(), phone, ownerId,
                    input.verificationMethod());
        }
        claims.contactResolution(input.guestId(), choice);
        return new Result(input.accountId());
    }

    private Result alreadyMerged(long resolvedId, long accountId) {
        if (resolvedId != accountId) throw conflict("GUEST_CLAIM_ALREADY_MERGED");
        return new Result(accountId);
    }

    private ContactChoice manualContact(ManualMerge input, ProfilePair pair, String phone) {
        ContactChoice choice = input.contactChoice();
        if (choice == ContactChoice.KEEP_ACCOUNT) return choice;
        if (phone == null) throw conflict("GUEST_CLAIM_CONTACT_CHOICE_REQUIRED");
        var matches = candidates(phone, pair.account().getId());
        if (!matches.stream().map(PlayerPadel::getId).toList().equals(List.of(pair.guest().getId()))) {
            throw conflict("GUEST_CLAIM_PHONE_AMBIGUOUS");
        }
        if (differentPhone(pair.guest(), pair.account()) && !input.replacePhone()) {
            throw conflict("GUEST_CLAIM_ACCOUNT_PHONE_DIFFERS");
        }
        return ContactChoice.USE_GUEST_PHONE;
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

    private List<String> profileBlockers(PlayerPadel guest, PlayerPadel account) {
        if (Objects.equals(guest.getId(), account.getId())) return List.of("GUEST_CLAIM_SAME_PROFILE");
        if (!guest.isGuestAccount()) return List.of("GUEST_CLAIM_NOT_GUEST");
        if (!guest.isActivo()) return List.of("GUEST_CLAIM_GUEST_INACTIVE");
        if (account.isGuestAccount()) return List.of("GUEST_CLAIM_ACCOUNT_IS_GUEST");
        if (!account.isActivo()) return List.of("GUEST_CLAIM_ACCOUNT_INACTIVE");
        return history.conflicts(guest.getId(), account.getId());
    }

    private List<String> blockers(PlayerPadel guest, PlayerPadel account, String phone) {
        var profiles = profileBlockers(guest, account);
        if (!profiles.isEmpty()) return profiles;
        if (phone == null || !players.claimPhoneKey(guest.getTelefono()).filter(phone::equals).isPresent()) {
            return List.of("GUEST_CLAIM_PHONE_INVALID");
        }
        var matches = candidates(phone, account.getId());
        if (!matches.stream().map(PlayerPadel::getId).toList().equals(List.of(guest.getId()))) {
            return List.of(candidateFailure(matches));
        }
        return List.of();
    }

    private String candidateFailure(List<PlayerPadel> matches) {
        if (matches.isEmpty()) return "GUEST_CLAIM_GUEST_NOT_FOUND";
        if (matches.size() > 1) return "GUEST_CLAIM_PHONE_AMBIGUOUS";
        return "GUEST_CLAIM_NOT_GUEST";
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

    private void merge(ProfilePair pair, SideChoice sideChoice) {
        merge(pair, sideChoice, pair.guest().getTelefono());
    }

    private void merge(ProfilePair pair, SideChoice sideChoice, String phone) {
        var guest = pair.guest();
        var account = pair.account();
        if (sideConflict(guest, account) && sideChoice == null) throw conflict("GUEST_CLAIM_PLAYING_SIDE_CONFLICT");
        if (account.getPlayingSide() == null || (sideConflict(guest, account) && sideChoice == SideChoice.GUEST)) {
            account.setPlayingSide(guest.getPlayingSide());
        }
        history.transfer(guest.getId(), account.getId(), account.getEmail());
        retireGuest(guest);
        players.flush(); // Освобождаем уникальный контакт гостя до записи его основному аккаунту.
        account.setTelefono(phone);
    }

    private boolean sideConflict(PlayerPadel guest, PlayerPadel account) {
        return guest.getPlayingSide() != null && account.getPlayingSide() != null
                && guest.getPlayingSide() != account.getPlayingSide();
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
            throw conflict(candidateFailure(matches));
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
