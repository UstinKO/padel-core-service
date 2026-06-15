package com.padle.core.padelcoreservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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

    private Boolean isExistingUser;
    private Long existingUserId;
}