package com.padle.core.persistence.identity;

import com.padle.core.application.identity.OwnerAccount.Role;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity(name = "Owner")
@Table(name = "owners_db")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OwnerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "phone")
    private String phone;

    @Column(name = "organization_name", length = 120)
    private String organizationName;

    @Column(name = "role", nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Role role = Role.ORGANIZER;

    // Клуб, к которому привязан клубный аккаунт (CLUB_ADMIN); NULL — глобальный аккаунт,
    // сырой Long без FK — тот же паттерн, что Tournament.clubId (см. PROJECT.md)
    @Column(name = "club_id")
    private Long clubId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "owner_club_access_db", joinColumns = @JoinColumn(name = "owner_id"))
    @Column(name = "club_id", nullable = false)
    @Builder.Default
    private Set<Long> clubIds = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}
