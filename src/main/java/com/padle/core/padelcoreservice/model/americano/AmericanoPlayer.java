package com.padle.core.padelcoreservice.model.americano;

import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.enums.AmericanoPlayerStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "americano_players_db")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AmericanoPlayer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "tournament_id", nullable = false)
    private Tournament tournament;

    @ManyToOne
    @JoinColumn(name = "player_id", nullable = false)
    private PlayerPadel player;

    @Column(name = "initial_position", nullable = false)
    private Integer initialPosition;

    @Column(name = "current_position")
    private Integer currentPosition;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private AmericanoPlayerStatus status = AmericanoPlayerStatus.ACTIVE;

    // ── Основная статистика ──────────────────────────────────────────────────

    /** Сумма набранных очков (= totalScore по ТЗ) */
    @Column(name = "total_score", nullable = false)
    @Builder.Default
    private Integer totalScore = 0;

    @Column(name = "matches_played", nullable = false)
    @Builder.Default
    private Integer matchesPlayed = 0;

    @Column(name = "matches_won", nullable = false)
    @Builder.Default
    private Integer matchesWon = 0;

    @Column(name = "matches_lost", nullable = false)
    @Builder.Default
    private Integer matchesLost = 0;

    /** Ничьи — опциональны по ТЗ, но поле должно быть */
    @Column(name = "matches_drawn", nullable = false)
    @Builder.Default
    private Integer matchesDrawn = 0;

    @Column(name = "points_scored", nullable = false)
    @Builder.Default
    private Integer pointsScored = 0;

    @Column(name = "points_conceded", nullable = false)
    @Builder.Default
    private Integer pointsConceded = 0;

    // ── Bye / пропуски ───────────────────────────────────────────────────────

    /** Общее число пропущенных раундов (bye) */
    @Column(name = "bye_count", nullable = false)
    @Builder.Default
    private Integer byeCount = 0;

    /** Номер последнего раунда, в котором игрок получил bye (для детектирования подряд-bye) */
    @Column(name = "last_bye_round")
    private Integer lastByeRound;

    // ── Разнообразие партнёров / соперников ─────────────────────────────────

    /**
     * Число уникальных партнёров, с которыми игрок был в одной команде.
     * Пересчитывается после каждого раунда.
     */
    @Column(name = "unique_partners", nullable = false)
    @Builder.Default
    private Integer uniquePartners = 0;

    /**
     * Число уникальных соперников, против которых играл игрок.
     * Пересчитывается после каждого раунда.
     */
    @Column(name = "unique_opponents", nullable = false)
    @Builder.Default
    private Integer uniqueOpponents = 0;

    // ── Выбывание ────────────────────────────────────────────────────────────

    @Column(name = "dropped_out_at")
    private LocalDateTime droppedOutAt;

    @Column(name = "dropped_out_reason")
    private String droppedOutReason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ── Вспомогательные методы ───────────────────────────────────────────────

    /**
     * Добавляет результат матча к статистике игрока.
     * Ничья: pointsScored == pointsConceded.
     */
    public void addMatchResult(int pointsScored, int pointsConceded) {
        this.pointsScored    += pointsScored;
        this.pointsConceded  += pointsConceded;
        this.totalScore      += pointsScored;
        this.matchesPlayed++;

        if (pointsScored > pointsConceded) {
            this.matchesWon++;
        } else if (pointsScored < pointsConceded) {
            this.matchesLost++;
        } else {
            this.matchesDrawn++;
        }
    }

    /**
     * Откатывает ранее записанный результат матча.
     * Используется при редактировании уже сохранённого результата.
     */
    public void revertMatchResult(int pointsScored, int pointsConceded) {
        this.pointsScored   -= pointsScored;
        this.pointsConceded -= pointsConceded;
        this.totalScore     -= pointsScored;
        this.matchesPlayed--;

        if (pointsScored > pointsConceded) {
            this.matchesWon--;
        } else if (pointsScored < pointsConceded) {
            this.matchesLost--;
        } else {
            this.matchesDrawn--;
        }
    }

    /** Разница очков: набранные − пропущенные */
    public Integer getPointDifference() {
        return pointsScored - pointsConceded;
    }
}