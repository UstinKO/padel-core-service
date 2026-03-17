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

    @NotBlank(message = "Телефон обязателен")
    private String telefono;

    private Boolean isExistingUser; // Флаг, найден ли пользователь в системе
    private Long existingUserId;    // ID если найден
}