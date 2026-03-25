package com.padle.core.padelcoreservice.model.americano;

import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.enums.AmericanoRoundStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "americano_rounds_db")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AmericanoRound {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "tournament_id", nullable = false)
    private Tournament tournament;

    @Column(name = "round_number", nullable = false)
    private Integer roundNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private AmericanoRoundStatus status = AmericanoRoundStatus.PENDING;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "points_per_match", nullable = false)
    private Integer pointsPerMatch; // Например, 32 очка за матч

    @Column(name = "is_doubles", nullable = false)
    private Boolean isDoubles; // true - парный, false - индивидуальный

    @Column(name = "courts")
    private Integer courts; // Количество кортов в этом раунде

    @Column(name = "note")
    private String note;

    @OneToMany(mappedBy = "round", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<AmericanoMatch> matches = new ArrayList<>();

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

    public void complete() {
        this.status = AmericanoRoundStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public void cancel() {
        this.status = AmericanoRoundStatus.CANCELLED;
        this.completedAt = LocalDateTime.now();
    }

    public boolean isCompleted() {
        return status == AmericanoRoundStatus.COMPLETED;
    }

    public boolean isInProgress() {
        return status == AmericanoRoundStatus.IN_PROGRESS;
    }

    public int getTotalMatches() {
        return matches != null ? matches.size() : 0;
    }

    public int getCompletedMatches() {
        return matches != null ? (int) matches.stream().filter(AmericanoMatch::isCompleted).count() : 0;
    }
}