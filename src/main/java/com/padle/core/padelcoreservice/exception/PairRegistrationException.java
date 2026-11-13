package com.padle.core.padelcoreservice.exception;

public class PairRegistrationException extends TournamentRegistrationException {

    public enum Reason {
        TOURNAMENT_NOT_FOUND,
        NOT_DOUBLES,
        REGISTRATION_CLOSED,
        PLAYER_NOT_FOUND,
        PARTNER_NOT_FOUND,
        SELF_PARTNER,
        ALREADY_REGISTERED,
        PARTNER_ALREADY_REGISTERED,
        PARTNER_LEVEL_REQUIRED,
        PARTNER_LEVEL_MISMATCH,
        NO_PENDING_PARTNER,
        PLAYER_LEVEL_MISMATCH,
        INVALID_PARTNER,
        SIGN_IN_REQUIRED,
        REGISTRATION_CONFLICT,
        REGISTRATION_FAILED,
        REGISTRATION_REJECTED
    }

    private final Reason reason;

    public PairRegistrationException(Reason reason) {
        this(reason, reason.name());
    }

    public PairRegistrationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
