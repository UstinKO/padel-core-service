package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.dto.PaymentDto;
import com.padle.core.padelcoreservice.dto.PaymentManagementViewDto;
import com.padle.core.padelcoreservice.exception.ResourceNotFoundException;
import com.padle.core.padelcoreservice.mapper.PaymentMapper;
import com.padle.core.padelcoreservice.model.Payment;
import com.padle.core.padelcoreservice.model.TournamentRegistration;
import com.padle.core.padelcoreservice.model.enums.PaymentMethod;
import com.padle.core.padelcoreservice.model.enums.PaymentStatus;
import com.padle.core.padelcoreservice.model.enums.RegistrationStatus;
import com.padle.core.padelcoreservice.repository.PaymentRepository;
import com.padle.core.padelcoreservice.repository.TournamentRegistrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final TournamentRegistrationRepository registrationRepository;
    private final PaymentMapper paymentMapper;

    // ==================== Базовые методы ====================

    public List<PaymentDto> getPaymentsByRegistration(Long registrationId) {
        return paymentRepository.findByRegistrationId(registrationId).stream()
                .map(paymentMapper::toDto)
                .collect(Collectors.toList());
    }

    public List<PaymentDto> getPaymentsByTournament(Long tournamentId) {
        return paymentRepository.findByTournamentId(tournamentId).stream()
                .map(paymentMapper::toDto)
                .collect(Collectors.toList());
    }

    public List<PaymentDto> getPaymentsByPlayer(Long playerId) {
        return paymentRepository.findByPlayerId(playerId).stream()
                .map(paymentMapper::toDto)
                .collect(Collectors.toList());
    }

    public BigDecimal getTotalPaidByTournament(Long tournamentId) {
        return paymentRepository.getTotalPaidByTournament(tournamentId);
    }

    // ==================== Создание платежей ====================

    @Transactional
    public PaymentDto createPayment(Long registrationId, BigDecimal amount,
                                    PaymentMethod method, Long createdBy) {
        log.info("Creating payment for registration {}: amount={}, method={}",
                registrationId, amount, method);

        TournamentRegistration registration = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new ResourceNotFoundException("Registration not found"));

        Payment payment = Payment.builder()
                .registration(registration)
                .amount(amount)
                .currency("ARS") // Можно брать из турнира
                .status(PaymentStatus.PENDING)
                .paymentMethod(method)
                .createdBy(createdBy)
                .build();

        Payment savedPayment = paymentRepository.save(payment);
        log.info("Payment created with id: {}", savedPayment.getId());

        return paymentMapper.toDto(savedPayment);
    }

    // ==================== Обработка платежей ====================

    @Transactional
    public PaymentDto confirmPayment(Long paymentId, String transactionId, String provider) {
        log.info("Confirming payment {} with transaction {}", paymentId, transactionId);

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        payment.markAsPaid(payment.getPaymentMethod(), transactionId, provider);
        Payment updatedPayment = paymentRepository.save(payment);

        // Обновляем статус регистрации (если нужно)
        TournamentRegistration registration = payment.getRegistration();
        // Можно добавить логику: если есть оплата, то статус регистрации меняется
        // Например, registration.setPaymentStatus(PaymentStatus.PAID);

        log.info("Payment {} confirmed", paymentId);
        return paymentMapper.toDto(updatedPayment);
    }

    @Transactional
    public PaymentDto markAsFailed(Long paymentId, String errorMessage) {
        log.info("Marking payment {} as failed", paymentId);

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        payment.markAsFailed(errorMessage);
        Payment updatedPayment = paymentRepository.save(payment);

        return paymentMapper.toDto(updatedPayment);
    }

    @Transactional
    public PaymentDto refundPayment(Long paymentId, String reason, Long updatedBy) {
        log.info("Refunding payment {}: {}", paymentId, reason);

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        if (payment.getStatus() != PaymentStatus.PAID) {
            throw new IllegalStateException("Cannot refund non-paid payment");
        }

        payment.refund(reason);
        Payment updatedPayment = paymentRepository.save(payment);

        return paymentMapper.toDto(updatedPayment);
    }

    // ==================== Для офлайн платежей (ручной ввод) ====================

    @Transactional
    public PaymentDto registerOfflinePayment(Long registrationId, BigDecimal amount,
                                             PaymentMethod method, String notes, Long createdBy) {
        log.info("Registering offline payment for registration {}", registrationId);

        PaymentDto payment = createPayment(registrationId, amount, method, createdBy);

        // Сразу подтверждаем офлайн платеж
        return confirmPayment(payment.getId(), "OFFLINE_" + System.currentTimeMillis(), "MANUAL");
    }

    // ==================== Статистика ====================

    public PaymentStats getPaymentStatsByTournament(Long tournamentId) {
        List<Payment> payments = paymentRepository.findByTournamentId(tournamentId);

        long totalCount = payments.size();
        long paidCount = payments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.PAID)
                .count();
        BigDecimal totalAmount = payments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.PAID)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return PaymentStats.builder()
                .totalPayments(totalCount)
                .paidPayments(paidCount)
                .totalAmount(totalAmount)
                .build();
    }

    @Transactional(readOnly = true)
    public List<PaymentManagementViewDto> getPaymentManagementData(Long tournamentId) {
        log.info("Getting payment management data for tournament: {}", tournamentId);

        // Получаем все подтвержденные регистрации
        List<TournamentRegistration> registrations = registrationRepository
                .findByTournamentIdOrderByPositionAscWaitlistPositionAsc(tournamentId);

        // Получаем все платежи по турниру
        List<Payment> payments = paymentRepository.findByTournamentId(tournamentId);

        return registrations.stream()
                .filter(reg -> reg.getStatus() == RegistrationStatus.CONFIRMED)
                .map(reg -> {
                    PaymentManagementViewDto dto = new PaymentManagementViewDto();

                    // Данные регистрации
                    dto.setRegistrationId(reg.getId());
                    dto.setPlayerId(reg.getPlayer().getId());
                    dto.setPlayerName(reg.getPlayer().getNombre());
                    dto.setPlayerEmail(reg.getPlayer().getEmail());
                    dto.setPosition(reg.getPosition());
                    dto.setAttended(reg.getAttended() != null ? reg.getAttended() : false);

                    // Ищем платеж для этой регистрации
                    payments.stream()
                            .filter(p -> p.getRegistration().getId().equals(reg.getId()))
                            .findFirst()
                            .ifPresentOrElse(p -> {
                                dto.setPaymentId(p.getId());
                                dto.setAmount(p.getAmount());
                                dto.setCurrency(p.getCurrency());
                                dto.setPaymentStatus(p.getStatus());
                                dto.setPaymentMethod(p.getPaymentMethod());
                                dto.setTransactionId(p.getTransactionId());
                                dto.setNotes(p.getNotes());
                                dto.setHasPayment(true);
                            }, () -> {
                                dto.setHasPayment(false);
                                dto.setCurrency("ARS"); // Дефолтная валюта
                            });

                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void savePaymentManagementData(Long tournamentId, List<PaymentManagementViewDto> updates, Long updatedBy) {
        log.info("Saving payment management data for tournament: {}", tournamentId);

        for (PaymentManagementViewDto dto : updates) {
            // Обновляем отметку о посещении
            TournamentRegistration registration = registrationRepository.findById(dto.getRegistrationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Registration not found: " + dto.getRegistrationId()));

            registration.setAttended(dto.getAttended());
            registrationRepository.save(registration);

            // Если есть данные платежа
            if (dto.getAmount() != null && dto.getAmount().compareTo(BigDecimal.ZERO) > 0) {
                if (dto.isHasPayment() && dto.getPaymentId() != null) {
                    // Обновляем существующий платеж
                    Payment payment = paymentRepository.findById(dto.getPaymentId())
                            .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + dto.getPaymentId()));

                    payment.setAmount(dto.getAmount());
                    payment.setPaymentMethod(dto.getPaymentMethod());
                    payment.setStatus(dto.getPaymentStatus() != null ? dto.getPaymentStatus() : PaymentStatus.PAID);
                    payment.setTransactionId(dto.getTransactionId());
                    payment.setNotes(dto.getNotes());

                    if (dto.getPaymentStatus() == PaymentStatus.PAID && payment.getPaymentDate() == null) {
                        payment.setPaymentDate(LocalDateTime.now());
                    }

                    paymentRepository.save(payment);

                } else {
                    // Создаем новый платеж
                    Payment payment = Payment.builder()
                            .registration(registration)
                            .amount(dto.getAmount())
                            .currency(dto.getCurrency() != null ? dto.getCurrency() : "ARS")
                            .status(dto.getPaymentStatus() != null ? dto.getPaymentStatus() : PaymentStatus.PAID)
                            .paymentMethod(dto.getPaymentMethod())
                            .transactionId(dto.getTransactionId())
                            .notes(dto.getNotes())
                            .createdBy(updatedBy)
                            .build();

                    if (payment.getStatus() == PaymentStatus.PAID) {
                        payment.setPaymentDate(LocalDateTime.now());
                    }

                    paymentRepository.save(payment);
                }
            }
        }
    }

    @lombok.Builder
    @lombok.Data
    public static class PaymentStats {
        private long totalPayments;
        private long paidPayments;
        private BigDecimal totalAmount;
    }
}