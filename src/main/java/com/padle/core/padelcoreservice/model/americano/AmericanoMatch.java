package com.padle.core.padelcoreservice.model.americano;

import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.enums.AmericanoRoundStatus;
import com.padle.core.padelcoreservice.model.enums.PlayoffStage;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "americano_matches_db")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AmericanoMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "round_id", nullable = false)
    private AmericanoRound round;

    @ManyToOne
    @JoinColumn(name = "tournament_id", nullable = false)
    private com.padle.core.padelcoreservice.model.Tournament tournament;

    @Column(name = "match_number", nullable = false)
    private Integer matchNumber; // Номер матча в раунде

    @ManyToOne
    @JoinColumn(name = "team1_player1_id")
    private PlayerPadel team1Player1;

    @ManyToOne
    @JoinColumn(name = "team1_player2_id")
    private PlayerPadel team1Player2; // null для индивидуального

    @ManyToOne
    @JoinColumn(name = "team2_player1_id")
    private PlayerPadel team2Player1;

    @ManyToOne
    @JoinColumn(name = "team2_player2_id")
    private PlayerPadel team2Player2; // null для индивидуального

    @Column(name = "team1_score")
    private Integer team1Score;

    @Column(name = "team2_score")
    private Integer team2Score;

    @Column(name = "is_doubles", nullable = false)
    private Boolean isDoubles;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private AmericanoRoundStatus status = AmericanoRoundStatus.PENDING;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "court_number")
    private Integer courtNumber; // Номер корта

    @Column(name = "note")
    private String note;

    /** ID команды (AmericanoTeam) — используется в AMERICANO_TEAMS */
    @Column(name = "team1_id")
    private Long team1Id;

    @Column(name = "team2_id")
    private Long team2Id;

    @Enumerated(EnumType.STRING)
    @Column(name = "playoff_stage", length = 20)
    private PlayoffStage playoffStage;

    /** Счёт в геймах (для AMERICANO_TEAMS с сетовым форматом) */
    @Column(name = "team1_games")
    private Integer team1Games;

    @Column(name = "team2_games")
    private Integer team2Games;

    /** Матч 1/8 финала (play-in, T13) желательно запустить как можно раньше — ТЗ §32. */
    @Column(name = "priority", nullable = false)
    @Builder.Default
    private Boolean priority = false;

    /**
     * Play-in-матч (T13): id и слот (1|2) четвертьфинального матча, куда сразу после этого матча
     * должен попасть победитель — в отличие от QF→SF/SF→Final, слот фиксирован заранее (ТЗ §33/§34),
     * а не пересчитывается движком подбора соперников после завершения всей стадии.
     */
    @Column(name = "next_match_id")
    private Long nextMatchId;

    @Column(name = "next_match_slot")
    private Integer nextMatchSlot;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Вспомогательные методы
    public void start() {
        this.status = AmericanoRoundStatus.IN_PROGRESS;
        this.startedAt = LocalDateTime.now();
    }

    public void complete(int team1Score, int team2Score) {
        this.team1Score = team1Score;
        this.team2Score = team2Score;
        this.status = AmericanoRoundStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public boolean isCompleted() {
        return status == AmericanoRoundStatus.COMPLETED;
    }

    public boolean isInProgress() {
        return status == AmericanoRoundStatus.IN_PROGRESS;
    }

    public String getTeam1DisplayName() {
        if (isDoubles) {
            return (team1Player1 != null ? team1Player1.getNombre() + " " + team1Player1.getApellido() : "?") +
                    " / " +
                    (team1Player2 != null ? team1Player2.getNombre() + " " + team1Player2.getApellido() : "?");
        } else {
            return team1Player1 != null ? team1Player1.getNombre() + " " + team1Player1.getApellido() : "?";
        }
    }

    public String getTeam2DisplayName() {
        if (isDoubles) {
            return (team2Player1 != null ? team2Player1.getNombre() + " " + team2Player1.getApellido() : "?") +
                    " / " +
                    (team2Player2 != null ? team2Player2.getNombre() + " " + team2Player2.getApellido() : "?");
        } else {
            return team2Player1 != null ? team2Player1.getNombre() + " " + team2Player1.getApellido() : "?";
        }
    }

    public boolean isTeam1Winner() {
        return team1Score != null && team2Score != null && team1Score > team2Score;
    }
}