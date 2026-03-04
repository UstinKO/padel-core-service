package com.padle.core.padelcoreservice.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Entity
@Table(name = "court_teams_db")
@Data
@EqualsAndHashCode(exclude = {"court", "player1", "player2"})
@ToString(exclude = {"court", "player1", "player2"})
public class CourtTeam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "court_id", nullable = false)
    private KingOfCourtCourt court;

    @Column(name = "team_number", nullable = false)
    private Integer teamNumber;

    @ManyToOne
    @JoinColumn(name = "player1_id")
    private PlayerPadel player1;

    @ManyToOne
    @JoinColumn(name = "player2_id")
    private PlayerPadel player2;
}