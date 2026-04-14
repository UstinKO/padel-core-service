package com.padle.core.padelcoreservice.model.americano;

import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.enums.AmericanoPlayerStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Представляет фиксированную пару игроков в Team Americano турнире.
 * Каждая пара — это единица турнира: играет вместе весь турнир,
 * набирает очки как команда и участвует в Round Robin сетке.
 */
@Entity
@Table(name = "americano_teams_db",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"tournament_id", "player1_id"},
                name = "uq_americano_team_tournament_player1"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AmericanoTeam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_id", nullable = false)
    private Tournament tournament;

    /** Главный игрок пары (тот кто создал регистрацию, mainPlayerId == player.id) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player1_id", nullable = false)
    private PlayerPadel player1;

    /** Партнёр (зарегистрирован на сайте) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player2_id")
    private PlayerPadel player2;

    /** Имя партнёра если он НЕ зарегистрирован на сайте */
    @Column(name = "player2_name", length = 200)
    private String player2Name;

    /** Телефон партнёра если он НЕ зарегистрирован на сайте */
    @Column(name = "player2_phone", length = 30)
    private String player2Phone;

    /** Порядковый номер команды в турнире (для отображения) */
    @Column(name = "team_number", nullable = false)
    private Integer teamNumber;

    @Column(name = "current_position")
    private Integer currentPosition;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private AmericanoPlayerStatus status = AmericanoPlayerStatus.ACTIVE;

    // ── Статистика ──────────────────────────────────────────────────────────

    /** Сумма набранных очков командой */
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

    @Column(name = "matches_drawn", nullable = false)
    @Builder.Default
    private Integer matchesDrawn = 0;

    @Column(name = "points_scored", nullable = false)
    @Builder.Default
    private Integer pointsScored = 0;

    @Column(name = "points_conceded", nullable = false)
    @Builder.Default
    private Integer pointsConceded = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ── Вспомогательные методы ──────────────────────────────────────────────

    public void addMatchResult(int scored, int conceded) {
        this.pointsScored   += scored;
        this.pointsConceded += conceded;
        this.totalScore     += scored;
        this.matchesPlayed++;

        if (scored > conceded)      this.matchesWon++;
        else if (scored < conceded) this.matchesLost++;
        else                        this.matchesDrawn++;
    }

    public void revertMatchResult(int scored, int conceded) {
        this.pointsScored   -= scored;
        this.pointsConceded -= conceded;
        this.totalScore     -= scored;
        this.matchesPlayed--;

        if (scored > conceded)      this.matchesWon--;
        else if (scored < conceded) this.matchesLost--;
        else                        this.matchesDrawn--;
    }

    public Integer getPointDifference() {
        return pointsScored - pointsConceded;
    }

    /** Отображаемое имя команды */
    public String getDisplayName() {
        String p1 = player1 != null
                ? player1.getNombre() + " " + player1.getApellido()
                : "?";
        String p2 = player2 != null
                ? player2.getNombre() + " " + player2.getApellido()
                : (player2Name != null ? player2Name : "?");
        return p1 + " / " + p2;
    }
}