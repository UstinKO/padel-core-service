package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.padelcoreservice.dto.AccountDtos;
import com.padle.core.padelcoreservice.dto.RegistroRequestDto;
import com.padle.core.padelcoreservice.dto.PasswordResetConfirm;
import com.padle.core.padelcoreservice.exception.AccountException;
import com.padle.core.padelcoreservice.security.AccountDestination;
import com.padle.core.padelcoreservice.security.RateLimitFilter;
import com.padle.core.padelcoreservice.service.AccountService;
import com.padle.core.padelcoreservice.service.PasswordResetService;
import com.padle.core.padelcoreservice.service.RecaptchaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Locale;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AccountController {
    private final AccountService accounts;
    private final RecaptchaService captcha;
    private final PasswordResetService resets;
    private final RateLimitFilter limiter;

    @ModelAttribute
    public void noStore(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
    }

    @PostMapping("/players/registro")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountDtos.Result register(@Valid @RequestBody RegistroRequestDto request, HttpServletRequest http, Locale locale) {
        if (request.getWebsite() != null && !request.getWebsite().isBlank()) {
            limiter.markRegistrationFailed(limiter.extractIp(http));
            throw new AccountException(HttpStatus.UNPROCESSABLE_ENTITY, "CAPTCHA_INVALID", "recaptchaToken");
        }
        if (!captcha.verify(request.getRecaptchaToken())) {
            limiter.markRegistrationFailed(limiter.extractIp(http));
            throw new AccountException(HttpStatus.UNPROCESSABLE_ENTITY, "CAPTCHA_INVALID", "recaptchaToken");
        }
        request.setPreferredLocale(locale.getLanguage());
        accounts.register(request);
        return new AccountDtos.Result(true, "REGISTERED", AccountDestination.safe(request.getDestination()).orElse("/players/dashboard"));
    }

    @GetMapping("/players/me")
    public AccountDtos.Profile profile(Authentication authentication) { return accounts.profile(authentication); }

    @PutMapping("/players/me")
    public AccountDtos.Profile update(Authentication authentication, @Valid @RequestBody AccountDtos.ProfileUpdate request) {
        return accounts.update(authentication, request);
    }

    @PostMapping("/players/me/password")
    public AccountDtos.Result password(Authentication authentication, @Valid @RequestBody AccountDtos.PasswordChange request) {
        accounts.changePassword(authentication, request);
        return new AccountDtos.Result(true, "PASSWORD_CHANGED", null);
    }

    @PostMapping("/password-reset/request")
    public AccountDtos.Result requestReset(@Valid @RequestBody AccountDtos.ResetRequest request) {
        resets.requestPasswordReset(request.email(), AccountDestination.safe(request.destination()));
        return new AccountDtos.Result(true, "RESET_REQUESTED", AccountDestination.safe(request.destination()).orElse("/login"));
    }

    @GetMapping("/password-reset/validate")
    public AccountDtos.TokenStatus validate(@RequestParam String token) {
        boolean valid = token.length() <= 200 && resets.validateToken(token);
        return new AccountDtos.TokenStatus(valid, valid ? "TOKEN_VALID" : "RESET_TOKEN_INVALID");
    }

    @PostMapping("/password-reset/confirm")
    public AccountDtos.Result confirm(@Valid @RequestBody AccountDtos.ResetConfirm request) {
        if (!request.newPassword().equals(request.confirmPassword()))
            throw new AccountException(HttpStatus.UNPROCESSABLE_ENTITY, "PASSWORD_MISMATCH", "confirmPassword");
        var dto = new PasswordResetConfirm();
        dto.setToken(request.token());
        dto.setNewPassword(request.newPassword());
        dto.setConfirmPassword(request.confirmPassword());
        if (!resets.resetPassword(dto)) throw new AccountException(HttpStatus.UNPROCESSABLE_ENTITY, "RESET_TOKEN_INVALID", "token");
        return new AccountDtos.Result(true, "PASSWORD_CHANGED", AccountDestination.safe(request.destination()).orElse("/login"));
    }
}
