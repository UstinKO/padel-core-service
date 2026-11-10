package com.padle.core.padelcoreservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record SpaSessionResponse(
        boolean authenticated,
        SpaSessionUserType userType,
        Long id,
        String fullName,
        String email,
        List<String> roles,
        Long clubId,
        Boolean hasValidContact,
        String preferredLocale
) {
    public SpaSessionResponse {
        roles = List.copyOf(roles);
    }

    public static SpaSessionResponse anonymous() {
        return new SpaSessionResponse(false, null, null, null, null, List.of(), null, null, null);
    }
}
