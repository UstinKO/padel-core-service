package com.padle.core.padelcoreservice.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tournament_king_of_court_db")
@Data
@EqualsAndHashCode(exclude = {"rounds", "playerStats"})
@ToString(exclude = {"rounds", "playerStats"})
public class TournamentKingOfCourt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "tournament_id", nullable = false)
    private Tournament tournament;

    @Column(name = "max_courts", nullable = false)
    private Integer maxCourts = 5;

    @Column(name = "calibration_rounds", nullable = false)
    private Integer calibrationRounds = 3;

    @Column(name = "current_round", nullable = false)
    private Integer currentRound = 0;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = false;

    @Column(name = "is_finished", nullable = false)
    private Boolean isFinished = false;

    @Column(name = "youtube_link", length = 500)
    private String youtubeLink;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "tournamentKing", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<KingOfCourtRound> rounds = new ArrayList<>();

    @OneToMany(mappedBy = "tournamentKing", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<KingOfCourtPlayerStats> playerStats = new ArrayList<>();
}