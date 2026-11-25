package com.padle.core.application.clubs;

public record PublicClub(Long id, String name, String address, String city, String description,
                         String logoUrl, String mapUrl, String websiteUrl) {
}
