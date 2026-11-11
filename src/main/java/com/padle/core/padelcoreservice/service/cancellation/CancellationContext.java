package com.padle.core.padelcoreservice.service.cancellation;

import com.padle.core.padelcoreservice.dto.CancellationHistory.ActorKind;
import com.padle.core.padelcoreservice.dto.CancellationHistory.Channel;
import com.padle.core.padelcoreservice.dto.CancellationHistory.Reason;
import com.padle.core.padelcoreservice.dto.CancellationHistory.Scope;
import com.padle.core.padelcoreservice.model.Owner;

public record CancellationContext(ActorKind initiator, Long actorId, Scope scope, Reason reason,
                                  Channel channel, String organizerComment) {
    public static CancellationContext participant(long id, Scope scope, Reason reason) {
        return new CancellationContext(ActorKind.PLAYER, id, scope, reason, Channel.ACCOUNT, null);
    }

    public static CancellationContext organizer(Owner owner, Scope scope, String comment) {
        return new CancellationContext(owner == null ? ActorKind.UNKNOWN : ActorKind.OWNER,
                owner == null ? null : owner.getId(), scope, Reason.ORGANIZER_REMOVAL,
                Channel.ORGANIZER, plain(comment));
    }

    public static CancellationContext reserveLink() {
        return new CancellationContext(ActorKind.UNKNOWN, null, Scope.PARTICIPANT,
                Reason.PARTICIPANT_WITHDRAWAL, Channel.RESERVE_LINK, null);
    }

    public static CancellationContext replacement(long actor) {
        return new CancellationContext(ActorKind.PLAYER, actor, Scope.PARTICIPANT,
                Reason.PARTICIPANT_REPLACED, Channel.REPLACEMENT, null);
    }

    public static CancellationContext deactivation() {
        return new CancellationContext(ActorKind.SYSTEM, null, Scope.PARTICIPANT,
                Reason.PLAYER_DEACTIVATED, Channel.SYSTEM, null);
    }

    private static String plain(String value) {
        if (value == null || value.isBlank()) return "";
        if (value.length() > 255 || value.indexOf('<') >= 0 || value.indexOf('>') >= 0
                || value.codePoints().anyMatch(code -> Character.isISOControl(code) && code != '\n' && code != '\t')) {
            throw new IllegalArgumentException("CANCELLATION_COMMENT_INVALID");
        }
        return value.strip();
    }
}
