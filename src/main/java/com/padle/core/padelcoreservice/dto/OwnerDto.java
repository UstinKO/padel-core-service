package com.padle.core.padelcoreservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerDto {
    private Long id;
    private String email;
    private String firstName;
    private String lastName;
    private String phone;
    private Boolean isSuperAdmin;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;
    private Boolean isActive;

    // Для отображения в UI
    public String getFullName() {
        return firstName + " " + lastName;
    }
}