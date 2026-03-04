package com.padle.core.padelcoreservice.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "king_of_court_court_db")
@Data
@EqualsAndHashCode(exclude = {"round", "players", "teams", "result"})
@ToString(exclude = {"round", "players", "teams", "result"})
public class KingOfCourtCourt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "round_id", nullable = false)
    private KingOfCourtRound round;

    @Column(name = "court_number", nullable = false)
    private Integer courtNumber;

    @ManyToMany
    @JoinTable(
            name = "court_players_db",
            joinColumns = @JoinColumn(name = "court_id"),
            inverseJoinColumns = @JoinColumn(name = "player_id")
    )
    private List<PlayerPadel> players = new ArrayList<>();

    @OneToMany(mappedBy = "court", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<CourtTeam> teams = new ArrayList<>();

    @OneToOne(mappedBy = "court", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private KingOfCourtMatchResult result;
}