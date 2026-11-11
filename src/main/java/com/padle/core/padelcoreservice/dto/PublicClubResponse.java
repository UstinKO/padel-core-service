package com.padle.core.padelcoreservice.dto;

public record PublicClubResponse(Long id, String name, String address, String city,
        String description, String logoUrl, String mapUrl, String websiteUrl) {}
