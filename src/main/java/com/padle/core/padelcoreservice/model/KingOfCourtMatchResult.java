package com.padle.core.padelcoreservice.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "king_of_court_match_result_db")
@Data
@EqualsAndHashCode(exclude = {"court", "winner1", "winner2", "loser1", "loser2"})
@ToString(exclude = {"court", "winner1", "winner2", "loser1", "loser2"})
public class KingOfCourtMatchResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "court_id", nullable = false, unique = true)
    private KingOfCourtCourt court;

    @ManyToOne
    @JoinColumn(name = "winner1_id")
    private PlayerPadel winner1;

    @ManyToOne
    @JoinColumn(name = "winner2_id")
    private PlayerPadel winner2;

    @ManyToOne
    @JoinColumn(name = "loser1_id")
    private PlayerPadel loser1;

    @ManyToOne
    @JoinColumn(name = "loser2_id")
    private PlayerPadel loser2;

    @Column(name = "winners_score", nullable = false)
    private Integer winnersScore;

    @Column(name = "losers_score", nullable = false)
    private Integer losersScore;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Transient
    public List<PlayerPadel> getWinners() {
        return List.of(winner1, winner2);
    }

    @Transient
    public List<PlayerPadel> getLosers() {
        return List.of(loser1, loser2);
    }
}