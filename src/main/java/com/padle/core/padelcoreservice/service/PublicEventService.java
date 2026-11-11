package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.annotation.Timed;
import com.padle.core.padelcoreservice.dto.PublicEventResponse;
import com.padle.core.padelcoreservice.dto.PublicEventResponse.Action;
import com.padle.core.padelcoreservice.dto.PublicEventResponse.ActionType;
import com.padle.core.padelcoreservice.dto.PublicEventResponse.Phase;
import com.padle.core.padelcoreservice.dto.PublicTournamentDto;
import com.padle.core.padelcoreservice.exception.PublicDiscoveryException;
import com.padle.core.padelcoreservice.mapper.PublicEventMapper;
import com.padle.core.padelcoreservice.mapper.PublicParticipantsMapper;
import com.padle.core.padelcoreservice.mapper.PublicTournamentMapper;
import lombok.RequiredArgsConstructor;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicEventService {
    private final TournamentService tournaments;
    private final PublicTournamentMapper tournamentMapper;
    private final PublicParticipantsMapper participantsMapper;
    private final PublicEventMapper eventMapper;
    private final PublicEventStateService states;
    @Value("${app.base-url:http://localhost:8080}") private String baseUrl;

    @Timed(name = "public.event.time", tags = {"service=public-event", "operation=details"})
    public PublicEventResponse event(Long id) {
        if (id < 1) { throw new PublicDiscoveryException("INVALID_EVENT_ID"); }
        var source = tournaments.getActiveTournamentById(id)
                .orElseThrow(() -> new PublicDiscoveryException("EVENT_NOT_FOUND"));
        if (source.getEstado() == TournamentStatus.BORRADOR) {
            throw new PublicDiscoveryException("EVENT_NOT_FOUND");
        }
        var event = PublicTournamentDto.withParticipants(tournamentMapper.toPublicDto(source, baseUrl),
                participantsMapper.map(source, tournaments.getActiveRegistrationsByTournament(id)));
        var state = states.state(source);
        var actions = new ArrayList<Action>();
        if (event.registrationOpen() && state.phase() == Phase.NOT_STARTED) {
            actions.add(new Action(ActionType.REGISTRATION_INFO, event.websiteUrl()));
        }
        if (!state.livePath().isBlank() && state.phase() != Phase.NOT_STARTED && state.phase() != Phase.CANCELLED) {
            actions.add(new Action(ActionType.LIVE, state.livePath()));
        }
        var calendar = "/api/public/tournaments/" + id + "/calendar/event.ics?download=true";
        actions.add(new Action(ActionType.CALENDAR, calendar));
        return eventMapper.toResponse(event, eventMapper.toOrganizer(source.getContactoOrganizador()), state, actions, calendar);
    }
}
