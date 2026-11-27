package com.padle.core.application.identity;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record OwnerAccount(Long id, String email, String firstName, String lastName, String phone,
                           String organizationName, Role role, Long clubId, Set<Long> clubIds,
                           LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime lastLoginAt,
                           Boolean isActive) {

    public OwnerAccount {
        clubIds = Set.copyOf(clubIds);
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public boolean isSuperAdmin() {
        return role == Role.SUPER_ADMIN;
    }

    public boolean isClubAdmin() {
        return role == Role.CLUB_ADMIN;
    }

    public Set<Long> assignedClubIds() {
        return Stream.concat(clubIds.stream(), Stream.ofNullable(clubId)).collect(Collectors.toUnmodifiableSet());
    }

    public enum Role {
        SUPER_ADMIN, ORGANIZER, ADMIN, CLUB_ADMIN
    }
}
