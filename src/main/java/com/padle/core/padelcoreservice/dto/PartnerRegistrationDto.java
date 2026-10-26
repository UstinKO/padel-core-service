package com.padle.core.padelcoreservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartnerRegistrationDto {

    @NotBlank(message = "Имя обязательно")
    private String nombre;

    @NotBlank(message = "Фамилия обязательна")
    private String apellido;

    @Email(message = "Некорректный email")
    private String email;  // Опционально, если партнер не в системе

    // Не обязателен, если existingUserId указан — сервис найдёт партнёра по ID
    private String telefono;

    // Контакт партнёра без email — опционально, как и telefono
    @Size(max = 33, message = "El usuario de Telegram no puede tener más de 33 caracteres")
    @Pattern(regexp = "^$|^@?[A-Za-z][A-Za-z0-9_]{4,31}$", message = "Formato de usuario de Telegram inválido")
    private String telegramUsername;

    private Boolean isExistingUser;
    private Long existingUserId;
}