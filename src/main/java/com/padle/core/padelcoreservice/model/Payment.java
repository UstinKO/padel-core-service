package com.padle.core.padelcoreservice.model;

import com.padle.core.padelcoreservice.model.enums.PaymentMethod;
import com.padle.core.padelcoreservice.model.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments_db")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"registration"})
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Связь с регистрацией (один платеж может быть связан с одной регистрацией)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registration_id", nullable = false)
    private TournamentRegistration registration;

    // Сумма платежа
    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    // Валюта (на случай разных валют)
    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "ARS";

    // Статус платежа
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    // Способ оплаты
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 30)
    private PaymentMethod paymentMethod;

    // Дата платежа
    @Column(name = "payment_date")
    private LocalDateTime paymentDate;

    // Для онлайн-платежей - ID транзакции
    @Column(name = "transaction_id", length = 100)
    private String transactionId;

    // Для онлайн-платежей - провайдер (MercadoPago, Stripe, etc.)
    @Column(name = "payment_provider", length = 50)
    private String paymentProvider;

    // Комментарии/примечания
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    // Кто создал/подтвердил платеж (админ или система)
    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Для мягкого удаления
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    // Вспомогательные методы
    public void markAsPaid(PaymentMethod method, String transactionId, String provider) {
        this.status = PaymentStatus.PAID;
        this.paymentMethod = method;
        this.paymentDate = LocalDateTime.now();
        this.transactionId = transactionId;
        this.paymentProvider = provider;
    }

    public void markAsFailed(String errorNotes) {
        this.status = PaymentStatus.FAILED;
        this.notes = errorNotes;
    }

    public void refund(String reason) {
        this.status = PaymentStatus.REFUNDED;
        this.notes = (this.notes != null ? this.notes + "\n" : "") + "Refund: " + reason;
    }
}