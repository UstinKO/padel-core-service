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
    private static final String PARTNER_PAYMENT_MARKER = "PARTNER_PAYMENT";

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

        List<TournamentRegistration> registrations = registrationRepository
                .findByTournamentIdOrderByPositionAscWaitlistPositionAsc(tournamentId);

        List<Payment> payments = paymentRepository.findByTournamentId(tournamentId);

        List<PaymentManagementViewDto> result = new java.util.ArrayList<>();

        registrations.stream()
                .filter(reg -> reg.getStatus() == RegistrationStatus.CONFIRMED
                        || reg.getStatus() == RegistrationStatus.PARTNER_INVITED)
                .forEach(reg -> {

                    boolean isDouble = Boolean.TRUE.equals(reg.getIsDoubleRegistration());
                    boolean isMainPlayer = !isDouble
                            || (reg.getMainPlayerId() != null
                            && reg.getMainPlayerId().equals(reg.getPlayer().getId()));
                    boolean isPartnerInDb = isDouble
                            && reg.getMainPlayerId() != null
                            && !reg.getMainPlayerId().equals(reg.getPlayer().getId());

                    // ── Строка для любого игрока (главный или партнёр в БД) ──
                    PaymentManagementViewDto dto = new PaymentManagementViewDto();
                    dto.setRegistrationId(reg.getId());
                    dto.setPlayerId(reg.getPlayer().getId());
                    dto.setPlayerName(reg.getPlayer().getNombre()
                            + (reg.getPlayer().getApellido() != null
                            ? " " + reg.getPlayer().getApellido() : "")
                            + (isPartnerInDb ? " (compañero)" : ""));
                    dto.setPlayerEmail(reg.getPlayer().getEmail());
                    dto.setPlayerPhone(reg.getPlayer().getTelefono());
                    dto.setPosition(reg.getPosition());
                    dto.setAttended(reg.getAttended() != null ? reg.getAttended() : false);
                    dto.setParticipationConfirmed(reg.getParticipationConfirmed() != null ? reg.getParticipationConfirmed() : false);
                    dto.setPartnerRow(false); // у него есть своя регистрация — не виртуальная строка

                    // Платёж — без маркера PARTNER_PAYMENT (у каждого своя запись)
                    payments.stream()
                            .filter(p -> p.getRegistration().getId().equals(reg.getId()))
                            .filter(p -> p.getNotes() == null
                                    || !p.getNotes().startsWith(PARTNER_PAYMENT_MARKER))
                            .findFirst()
                            .ifPresentOrElse(p -> {
                                dto.setPaymentId(p.getId());
                                dto.setAmount(p.getAmount());
                                dto.setCurrency(p.getCurrency());
                                dto.setPaymentStatus(p.getStatus());
                                dto.setPaymentMethod(p.getPaymentMethod());
                                dto.setTransactionId(p.getTransactionId());
                                dto.setNotes(cleanNotes(p.getNotes()));
                                dto.setHasPayment(true);
                            }, () -> {
                                dto.setHasPayment(false);
                                dto.setCurrency("ARS");
                            });

                    result.add(dto);

                    // ── Виртуальная строка партнёра (только если партнёр НЕ в БД) ──
                    // Условие: главный игрок + партнёр не зарегистрирован (partner == null)
                    boolean partnerNotInDb = isMainPlayer
                            && isDouble
                            && reg.getPartner() == null
                            && reg.getPartnerFirstName() != null
                            && !reg.getPartnerFirstName().isBlank();

                    if (partnerNotInDb) {
                        PaymentManagementViewDto partnerDto = new PaymentManagementViewDto();
                        partnerDto.setRegistrationId(reg.getId()); // та же запись
                        partnerDto.setMainRegistrationId(reg.getId());
                        partnerDto.setPartnerRow(true);

                        String partnerName = reg.getPartnerFirstName()
                                + (reg.getPartnerLastName() != null
                                ? " " + reg.getPartnerLastName() : "");
                        partnerDto.setPlayerName(partnerName + " (compañero)");
                        partnerDto.setPlayerEmail(reg.getPartnerEmail());
                        partnerDto.setPartnerPhone(reg.getPartnerPhone());
                        partnerDto.setPartnerEmail(reg.getPartnerEmail());
                        partnerDto.setPosition(reg.getPosition());
                        partnerDto.setAttended(false);
                        partnerDto.setParticipationConfirmed(false);

                        // Платёж партнёра — с маркером PARTNER_PAYMENT
                        payments.stream()
                                .filter(p -> p.getRegistration().getId().equals(reg.getId()))
                                .filter(p -> p.getNotes() != null
                                        && p.getNotes().startsWith(PARTNER_PAYMENT_MARKER))
                                .findFirst()
                                .ifPresentOrElse(p -> {
                                    partnerDto.setPaymentId(p.getId());
                                    partnerDto.setAmount(p.getAmount());
                                    partnerDto.setCurrency(p.getCurrency());
                                    partnerDto.setPaymentStatus(p.getStatus());
                                    partnerDto.setPaymentMethod(p.getPaymentMethod());
                                    partnerDto.setTransactionId(p.getTransactionId());
                                    partnerDto.setNotes(cleanNotes(p.getNotes()));
                                    partnerDto.setHasPayment(true);
                                }, () -> {
                                    partnerDto.setHasPayment(false);
                                    partnerDto.setCurrency("ARS");
                                });

                        result.add(partnerDto);
                    }
                });

        return result;
    }

    @Transactional
    public void savePaymentManagementData(Long tournamentId,
                                          List<PaymentManagementViewDto> updates,
                                          Long updatedBy) {
        log.info("Saving payment management data for tournament: {}", tournamentId);

        for (PaymentManagementViewDto dto : updates) {
            TournamentRegistration registration = registrationRepository
                    .findById(dto.getRegistrationId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Registration not found: " + dto.getRegistrationId()));

            // Посещение и подтверждение участия — для основной строки
            if (!dto.isPartnerRow()) {
                registration.setAttended(dto.getAttended());
                registration.setParticipationConfirmed(dto.getParticipationConfirmed() != null ? dto.getParticipationConfirmed() : false);
                registrationRepository.save(registration);

                // Если это парная регистрация — синхронизируем attended партнёру (если он в БД)
                if (Boolean.TRUE.equals(registration.getIsDoubleRegistration())
                        && registration.getMainPlayerId() != null) {
                    registrationRepository
                            .findByTournamentIdOrderByPositionAscWaitlistPositionAsc(
                                    registration.getTournament().getId())
                            .stream()
                            .filter(r -> r.getIsActive()
                                    && !r.getId().equals(registration.getId())
                                    && Boolean.TRUE.equals(r.getIsDoubleRegistration())
                                    && registration.getMainPlayerId().equals(r.getMainPlayerId()))
                            .forEach(partnerReg -> {
                                partnerReg.setAttended(dto.getAttended());
                                registrationRepository.save(partnerReg);
                                log.debug("Synced attended={} to partner registration {}",
                                        dto.getAttended(), partnerReg.getId());
                            });
                }
            }

            // Нет суммы — пропускаем
            if (dto.getAmount() == null || dto.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                log.debug("No amount for registration {}, skipping", dto.getRegistrationId());
                continue;
            }

            // Для партнёра добавляем маркер в notes
            String notesValue = dto.isPartnerRow()
                    ? PARTNER_PAYMENT_MARKER
                    + (dto.getNotes() != null && !dto.getNotes().isBlank()
                    ? "|" + dto.getNotes() : "")
                    : dto.getNotes();

            if (dto.isHasPayment() && dto.getPaymentId() != null) {
                // Обновляем существующий платёж
                Payment payment = paymentRepository.findById(dto.getPaymentId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Payment not found: " + dto.getPaymentId()));

                payment.setAmount(dto.getAmount());
                payment.setPaymentMethod(dto.getPaymentMethod());
                payment.setStatus(dto.getPaymentStatus() != null
                        ? dto.getPaymentStatus() : PaymentStatus.PAID);
                payment.setNotes(notesValue);

                if (dto.getCurrency() != null) {
                    payment.setCurrency(dto.getCurrency());
                }

                if (dto.getTransactionId() != null && !dto.getTransactionId().isBlank()) {
                    if (!dto.getTransactionId().equals(payment.getTransactionId())) {
                        paymentRepository.findByTransactionId(dto.getTransactionId())
                                .ifPresent(existing -> {
                                    if (!existing.getId().equals(payment.getId())) {
                                        throw new IllegalArgumentException(
                                                "El ID de transacción '" + dto.getTransactionId()
                                                        + "' ya está en uso");
                                    }
                                });
                    }
                    payment.setTransactionId(dto.getTransactionId());
                } else {
                    payment.setTransactionId(null);
                }

                if (dto.getPaymentStatus() == PaymentStatus.PAID
                        && payment.getPaymentDate() == null) {
                    payment.setPaymentDate(java.time.LocalDateTime.now());
                }

                paymentRepository.save(payment);
                log.info("Updated payment {} for registration {} (partnerRow={})",
                        payment.getId(), dto.getRegistrationId(), dto.isPartnerRow());

            } else {
                // Создаём новый платёж
                Payment payment = Payment.builder()
                        .registration(registration)
                        .amount(dto.getAmount())
                        .currency(dto.getCurrency() != null ? dto.getCurrency() : "ARS")
                        .status(dto.getPaymentStatus() != null
                                ? dto.getPaymentStatus() : PaymentStatus.PAID)
                        .paymentMethod(dto.getPaymentMethod())
                        .notes(notesValue)
                        .createdBy(updatedBy)
                        .build();

                if (dto.getTransactionId() != null && !dto.getTransactionId().isBlank()) {
                    paymentRepository.findByTransactionId(dto.getTransactionId())
                            .ifPresent(existing -> {
                                throw new IllegalArgumentException(
                                        "El ID de transacción '" + dto.getTransactionId()
                                                + "' ya está en uso");
                            });
                    payment.setTransactionId(dto.getTransactionId());
                }

                if (payment.getStatus() == PaymentStatus.PAID) {
                    payment.setPaymentDate(java.time.LocalDateTime.now());
                }

                paymentRepository.save(payment);
                log.info("Created payment for registration {} (partnerRow={})",
                        dto.getRegistrationId(), dto.isPartnerRow());
            }
        }
    }

    // Убирает маркер PARTNER_PAYMENT из notes для отображения в UI
    private String cleanNotes(String notes) {
        if (notes == null) return null;
        if (notes.startsWith(PARTNER_PAYMENT_MARKER + "|")) {
            return notes.substring(PARTNER_PAYMENT_MARKER.length() + 1);
        }
        if (notes.equals(PARTNER_PAYMENT_MARKER)) {
            return null;
        }
        return notes;
    }

    @lombok.Builder
    @lombok.Data
    public static class PaymentStats {
        private long totalPayments;
        private long paidPayments;
        private BigDecimal totalAmount;
    }
}