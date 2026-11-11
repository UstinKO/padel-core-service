package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.dto.CancellationHistory.ActorKind;
import com.padle.core.padelcoreservice.dto.CancellationHistory.Scope;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.enums.OwnerRole;
import com.padle.core.padelcoreservice.service.cancellation.CancellationContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CancellationContextTest {
    @Test
    void organizerCommentIsPlainTextAndCannotCarryMarkupOrUnboundedText() {
        var owner=Owner.builder().id(7L).role(OwnerRole.SUPER_ADMIN).build();
        var context=CancellationContext.organizer(owner,Scope.PAIR,"  Перенос мероприятия  ");
        assertThat(context.initiator()).isEqualTo(ActorKind.OWNER);
        assertThat(context.actorId()).isEqualTo(7);
        assertThat(context.organizerComment()).isEqualTo("Перенос мероприятия");
        for(var invalid:new String[]{"<script>alert(1)</script>","a".repeat(256),"text"+(char)0}) {
            assertThatThrownBy(()->CancellationContext.organizer(owner,Scope.PAIR,invalid)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
