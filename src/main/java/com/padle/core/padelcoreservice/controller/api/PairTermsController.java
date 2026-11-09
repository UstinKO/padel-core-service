package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.padelcoreservice.dto.PartnerRegistrationDto;
import com.padle.core.padelcoreservice.exception.TournamentRegistrationException;
import com.padle.core.padelcoreservice.service.DoubleTournamentRegistrationService;
import com.padle.core.padelcoreservice.service.PairTermsService;
import com.padle.core.padelcoreservice.service.TournamentService;
import com.padle.core.padelcoreservice.util.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/tournaments/double/{tournamentId}")
@RequiredArgsConstructor
public class PairTermsController {
    private final PairTermsService pairTerms;
    private final TournamentService tournaments;
    private final DoubleTournamentRegistrationService doubleRegistrations;

    public record Confirmation(@NotBlank @Size(max = 36) String token) {}

    @GetMapping("/pair-terms")
    public ResponseEntity<PairTermsService.Offer> offer(@PathVariable Long tournamentId,
                                                       @AuthenticationPrincipal Object principal) {
        return pairTerms.offer(tournamentId, playerId(principal))
                .map(offer -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(offer))
                .orElseGet(() -> ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build());
    }

    @GetMapping("/my-pair-terms")
    public ResponseEntity<PairTermsService.BookingTerms> bookingTerms(@PathVariable Long tournamentId,
                                                                      @AuthenticationPrincipal Object principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(pairTerms.bookingTerms(tournamentId, playerId(principal)));
    }

    @PostMapping("/my-pair-terms")
    public PairTermsService.BookingTerms accept(@PathVariable Long tournamentId,
                                                @AuthenticationPrincipal Object principal,
                                                @Valid @RequestBody Confirmation request) {
        return pairTerms.accept(tournamentId, playerId(principal), request.token());
    }

    @GetMapping("/pair-cancellation")
    public ResponseEntity<PairTermsService.Cancellation> cancellation(@PathVariable Long tournamentId,
                                                                      @AuthenticationPrincipal Object principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(pairTerms.cancellation(tournamentId, playerId(principal)));
    }

    @PostMapping("/pair-cancellation")
    public PairTermsService.Cancellation cancel(@PathVariable Long tournamentId,
                                               @AuthenticationPrincipal Object principal,
                                               @Valid @RequestBody Confirmation request) {
        return tournaments.confirmPairCancellation(tournamentId, playerId(principal), request.token());
    }

    public record Replacement(
            @NotBlank @Size(max = 100) String nombre,
            @NotBlank @Size(max = 100) String apellido,
            @Pattern(regexp = "(?=(?:[^0-9]*[0-9]){8})[+]?[0-9 ()-]{8,19}") String telefono,
            @Positive Long existingUserId) {}

    @PostMapping("/replace-self")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void replaceSelf(@PathVariable Long tournamentId, @AuthenticationPrincipal Object principal,
                            @Valid @RequestBody Replacement replacement) {
        Long playerId = playerId(principal);
        PartnerRegistrationDto candidate = replacementDto(replacement);
        var tournament = tournaments.getTournamentDtoById(tournamentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        try {
            doubleRegistrations.replacePlayerInPair(tournament, playerId, candidate, null);
        } catch (TournamentRegistrationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "PAIR_REPLACEMENT_UNAVAILABLE");
        }
    }

    public record PartnerReplacement(@NotNull @Positive Long currentPartnerId,
                                     @NotNull @Valid Replacement replacement) {}

    @PostMapping("/replace-partner")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void replacePartner(@PathVariable Long tournamentId, @AuthenticationPrincipal Object principal,
                               @Valid @RequestBody PartnerReplacement request) {
        Long playerId = playerId(principal);
        PartnerRegistrationDto candidate = replacementDto(request.replacement());
        var tournament = tournaments.getTournamentDtoById(tournamentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        try {
            doubleRegistrations.replaceCurrentPartner(tournament, playerId, request.currentPartnerId(), candidate);
        } catch (TournamentRegistrationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "PAIR_REPLACEMENT_UNAVAILABLE");
        }
    }

    private PartnerRegistrationDto replacementDto(Replacement replacement) {
        if (replacement.existingUserId() == null && replacement.telefono() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PAIR_REPLACEMENT_CONTACT_REQUIRED");
        }
        return PartnerRegistrationDto.builder()
                .nombre(replacement.nombre().strip()).apellido(replacement.apellido().strip())
                .telefono(replacement.telefono()).existingUserId(replacement.existingUserId()).build();
    }

    private Long playerId(Object principal) {
        var player = SecurityUtils.extractPlayer(principal);
        if (player == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return player.getId();
    }
}
