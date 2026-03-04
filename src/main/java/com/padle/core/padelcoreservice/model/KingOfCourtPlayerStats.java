package com.padle.core.padelcoreservice.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Entity
@Table(name = "king_of_court_player_stats_db")
@Data
@EqualsAndHashCode(exclude = {"tournamentKing", "player"})
@ToString(exclude = {"tournamentKing", "player"})
public class KingOfCourtPlayerStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "tournament_king_id", nullable = false)
    private TournamentKingOfCourt tournamentKing;

    @ManyToOne
    @JoinColumn(name = "player_id", nullable = false)
    private PlayerPadel player;

    @Column(name = "total_points", nullable = false)
    private Integer totalPoints = 0;

    @Column(name = "bonus_points", nullable = false)
    private Integer bonusPoints = 0;

    @Column(name = "games_played", nullable = false)
    private Integer gamesPlayed = 0;

    @Column(name = "wins", nullable = false)
    private Integer wins = 0;

    @Column(name = "losses", nullable = false)
    private Integer losses = 0;
}