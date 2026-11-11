package com.padle.core.padelcoreservice.security;

import com.padle.core.padelcoreservice.exception.PublicLiveException;
import com.padle.core.padelcoreservice.service.PublicLiveAccess;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublicLiveChannelInterceptorTest {
    private final PublicLiveAccess access=mock(PublicLiveAccess.class);
    private final PublicLiveChannelInterceptor interceptor=new PublicLiveChannelInterceptor(access);

    @Test void verifiesExactPublicSubscriptionAndPreservesLegacyDestinations() {
        assertThat(interceptor.preSend(message(StompCommand.SUBSCRIBE,"/topic/public/live/42"),null)).isNotNull();
        verify(access).readable(42);
        assertThat(interceptor.preSend(message(StompCommand.SUBSCRIBE,"/topic/tournament/7"),null)).isNotNull();
        verifyNoMoreInteractions(access);
    }
    @Test void rejectsHiddenMalformedWildcardAndClientPublicWrites() {
        when(access.readable(42)).thenThrow(new PublicLiveException("LIVE_EVENT_NOT_FOUND"));
        for(String destination : new String[]{"/topic/public/live/42","/topic/public/live/nope","/topic/**","/topic/public/live/{id}","/topic/public/live/42?"}) {
            assertThatThrownBy(() -> interceptor.preSend(message(StompCommand.SUBSCRIBE,destination),null)).isInstanceOf(AccessDeniedException.class);
        }
        assertThatThrownBy(() -> interceptor.preSend(message(StompCommand.SEND,"/topic/public/live/42"),null)).isInstanceOf(AccessDeniedException.class);
    }
    private Message<byte[]> message(StompCommand command,String destination) {
        var headers=StompHeaderAccessor.create(command); headers.setDestination(destination);
        return MessageBuilder.createMessage(new byte[0],headers.getMessageHeaders());
    }
}
