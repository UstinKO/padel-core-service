package com.padle.core.padelcoreservice.repository;

import com.padle.core.padelcoreservice.model.ParticipationRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParticipationRequestRepository extends JpaRepository<ParticipationRequest, UUID> {
    Optional<ParticipationRequest> findByRegistrationIdAndSupersededAtIsNull(Long registrationId);

    List<ParticipationRequest> findByRegistrationIdInAndSupersededAtIsNull(Collection<Long> registrationIds);

    @Query("""
            select r from ParticipationRequest r join fetch r.registration a join fetch a.player
            where r.supersededAt is null and (a.player.id = :actorId or r.delegatePlayerId = :actorId)
            order by r.requestedAt desc
            """)
    List<ParticipationRequest> findCurrentForActor(Long actorId);

    @Query("select r.registration.tournament.id from ParticipationRequest r where r.id = :id")
    Optional<Long> findTournamentId(UUID id);

}
