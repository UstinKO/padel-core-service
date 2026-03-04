package com.padle.core.padelcoreservice.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "king_of_court_round_db")
@Data
@EqualsAndHashCode(exclude = {"tournamentKing", "courts"})
@ToString(exclude = {"tournamentKing", "courts"})
public class KingOfCourtRound {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "tournament_king_id", nullable = false)
    private TournamentKingOfCourt tournamentKing;

    @Column(name = "round_number", nullable = false)
    private Integer roundNumber;

    @Column(name = "is_completed", nullable = false)
    private Boolean isCompleted = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @OneToMany(mappedBy = "round", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<KingOfCourtCourt> courts = new ArrayList<>();
}