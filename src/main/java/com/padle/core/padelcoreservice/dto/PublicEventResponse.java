package com.padle.core.padelcoreservice.dto;

import java.util.List;

public record PublicEventResponse(PublicTournamentDto event, Organizer organizer, Format format,
        Phase phase, List<Action> actions, String canonicalUrl, String calendarUrl, String pngUrl) {
    public PublicEventResponse { actions = List.copyOf(actions); }
    public record Organizer(String contact, String whatsappUrl) {}
    public record Action(ActionType type, String url) {}
    public record State(Format format, Phase phase, String livePath) {}
    public enum Format { KING_OF_COURT, AMERICANO, TEAM_PAIRS, TEAM_AMERICANO, TEAM_PLAYOFF, CANCHA_ABIERTA, PADEL_CLINIC }
    public enum Phase { NOT_STARTED, LIVE, QUALIFICATION, PLAYOFF, FINISHED, CANCELLED }
    public enum ActionType { REGISTRATION_INFO, LIVE, CALENDAR }
}
