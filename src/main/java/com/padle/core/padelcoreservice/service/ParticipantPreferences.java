package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.dto.PlayingSideDtos;
import com.padle.core.padelcoreservice.mapper.PlayingSideMapper;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.persistence.tournament.TournamentRegistration;
import com.padle.core.persistence.tournament.TournamentRegistrationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ParticipantPreferences {
    private final TournamentAccessService access;
    private final TournamentRegistrationRepository registrations;
    private final PlayingSideMapper mapper;

    @Transactional(readOnly = true)
    public List<PlayingSideDtos.Participant> roster(Owner owner, long tournamentId) {
        access.assertCanManageTournament(owner, tournamentId);
        return registrations.findByTournamentId(tournamentId).stream()
                .filter(row -> Boolean.TRUE.equals(row.getIsActive()))
                .map(TournamentRegistration::getPlayer).distinct().map(mapper::participant).toList();
    }
}
