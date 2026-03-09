package com.padle.core.padelcoreservice.model;

import com.padle.core.padelcoreservice.model.enums.PaymentStatus;
import com.padle.core.padelcoreservice.model.enums.RegistrationStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Entity
@Table(name = "tournament_registrations_db")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TournamentRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "tournament_id", nullable = false)
    private Tournament tournament;

    @ManyToOne
    @JoinColumn(name = "player_id", nullable = false)
    private PlayerPadel player;

    @Column(name = "registration_date", nullable = false)
    @CreationTimestamp
    private LocalDateTime registrationDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RegistrationStatus status;

    @Column(name = "position")
    private Integer position;

    @ManyToOne
    @JoinColumn(name = "partner_id")
    private PlayerPadel partner;

    @Column(name = "cancellation_date")
    private LocalDateTime cancellationDate;

    @Column(name = "cancellation_reason", length = 255)
    private String cancellationReason;

    @Column(name = "waitlist_position")
    private Integer waitlistPosition;

    @Column(name = "notified_about_vacancy")
    private Boolean notifiedAboutVacancy;

    @Column(name = "invitation_expires_at")
    private LocalDateTime invitationExpiresAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "attended", nullable = false)
    @Builder.Default
    private Boolean attended = false;

    @OneToMany(mappedBy = "registration", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Payment> payments = new ArrayList<>();

    // Хелпер-метод для получения последнего платежа
    public Optional<Payment> getLatestPayment() {
        return payments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.PAID)
                .max(Comparator.comparing(Payment::getPaymentDate));
    }

    // Проверка, оплачено ли
    public boolean isPaid() {
        return payments.stream()
                .anyMatch(p -> p.getStatus() == PaymentStatus.PAID);
    }

    // Вспомогательные методы
    public boolean isConfirmed() {
        return status == RegistrationStatus.CONFIRMED;
    }

    public boolean isInWaitlist() {
        return status == RegistrationStatus.WAITLIST;
    }

    public boolean isInvited() {
        return status == RegistrationStatus.WAITLIST_INVITED;
    }

    public boolean isCancelled() {
        return status == RegistrationStatus.CANCELLED;
    }

    public void confirm() {
        this.status = RegistrationStatus.CONFIRMED;
        this.position = null;
        this.waitlistPosition = null;
        this.invitationExpiresAt = null;
        this.notifiedAboutVacancy = null;
    }

    public void moveToWaitlist(int waitlistPosition) {
        this.status = RegistrationStatus.WAITLIST;
        this.waitlistPosition = waitlistPosition;
        this.position = null;
        this.invitationExpiresAt = null;
        this.notifiedAboutVacancy = false;
    }

    public void inviteToConfirm(LocalDateTime expiresAt) {
        this.status = RegistrationStatus.WAITLIST_INVITED;
        this.invitationExpiresAt = expiresAt;
        this.notifiedAboutVacancy = true;
    }

    public void cancel(String reason) {
        this.status = RegistrationStatus.CANCELLED;
        this.cancellationDate = LocalDateTime.now();
        this.cancellationReason = reason;
        this.isActive = false;
    }
}