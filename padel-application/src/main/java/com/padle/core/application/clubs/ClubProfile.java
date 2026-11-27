package com.padle.core.application.clubs;

import java.time.LocalDateTime;

public record ClubProfile(Long id, Fields fields, LocalDateTime createdAt, LocalDateTime updatedAt,
                          Long createdBy, boolean active) {
    public record Fields(String name, String address, String city, String phone, String email,
                         String mapUrl, String websiteUrl, String description, String logoUrl) {
    }
}
