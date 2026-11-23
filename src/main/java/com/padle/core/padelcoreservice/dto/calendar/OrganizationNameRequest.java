package com.padle.core.padelcoreservice.dto.calendar;

import jakarta.validation.constraints.Size;

public record OrganizationNameRequest(@Size(max = 120) String name) {
    public OrganizationNameRequest {
        name = name == null || name.isBlank() ? null : name.strip();
    }
}
