package com.padle.core.padelcoreservice.dto;

import com.padle.core.padelcoreservice.model.enums.Nivel;
import jakarta.validation.constraints.*;
import java.util.List;
import com.padle.core.padelcoreservice.validation.NoBotPattern;

public final class AccountDtos {
    private AccountDtos() {}
    public record Result(boolean success, String code, String destination) {}
    public record Login(boolean success, SpaSessionResponse session, String destination) {}
    public record PublicConfig(boolean captchaEnabled, String captchaSiteKey, boolean googleEnabled,
                               List<String> supportedLocales, String locale) {}
    public record ResetRequest(@NotBlank @Email @Size(max=150) String email, String destination) {}
    public record ResetConfirm(@NotBlank @Size(max=200) String token,
                               @NotBlank @Size(min=6,max=100) String newPassword,
                               @NotBlank @Size(max=100) String confirmPassword, String destination) {}
    public record TokenStatus(boolean valid, String code) {}
    public record PasswordChange(@NotBlank @Size(max=100) String currentPassword,
                                 @NotBlank @Size(min=6,max=100) String newPassword,
                                 @NotBlank @Size(max=100) String confirmPassword) {}
    public record Profile(Long id, String nombre, String apellido, String email, String telefono,
                          String telegramUsername, Nivel nivelJugador, String preferredLocale,
                          boolean guestAccount, boolean oauth2User) {}
    public record ProfileUpdate(@NotBlank @Size(min=3,max=100) @NoBotPattern @Pattern(regexp="^[\\p{L} .'-]{3,}$") String nombre,
                                @NotBlank @Size(min=3,max=100) @NoBotPattern @Pattern(regexp="^[\\p{L} .'-]{3,}$") String apellido,
                                @NotBlank @Email @Size(max=150) String email,
                                @Size(max=20) @Pattern(regexp="^$|^[+]?[0-9\\s-]{8,20}$") String telefono,
                                @Size(max=33) @Pattern(regexp="^$|^@?[A-Za-z][A-Za-z0-9_]{4,31}$") String telegramUsername,
                                @NotNull Nivel nivelJugador,
                                @NotBlank @Pattern(regexp="es|ru|en") String preferredLocale) {}
}
