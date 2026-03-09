package com.padle.core.padelcoreservice.repository;

import com.padle.core.padelcoreservice.model.Payment;
import com.padle.core.padelcoreservice.model.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // Найти все платежи по регистрации
    List<Payment> findByRegistrationId(Long registrationId);

    // Найти все платежи по статусу
    List<Payment> findByStatus(PaymentStatus status);

    // Найти платежи по турниру
    @Query("SELECT p FROM Payment p WHERE p.registration.tournament.id = :tournamentId")
    List<Payment> findByTournamentId(@Param("tournamentId") Long tournamentId);

    // Найти платежи по игроку
    @Query("SELECT p FROM Payment p WHERE p.registration.player.id = :playerId")
    List<Payment> findByPlayerId(@Param("playerId") Long playerId);

    // Получить сумму всех оплат по турниру
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p " +
            "WHERE p.registration.tournament.id = :tournamentId AND p.status = 'PAID'")
    BigDecimal getTotalPaidByTournament(@Param("tournamentId") Long tournamentId);

    // Проверить, есть ли оплата по транзакции
    Optional<Payment> findByTransactionId(String transactionId);

    // Найти платежи, которые нужно проверить (для онлайн-платежей)
    @Query("SELECT p FROM Payment p WHERE p.status = 'PROCESSING' AND p.createdAt < :timeout")
    List<Payment> findProcessingPaymentsOlderThan(@Param("timeout") LocalDateTime timeout);
}