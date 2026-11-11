package com.padle.core.padelcoreservice.security;

import com.padle.core.padelcoreservice.exception.PublicLiveException;
import com.padle.core.padelcoreservice.service.PublicLiveAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PublicLiveChannelInterceptor implements ChannelInterceptor {
    private static final String PREFIX = "/topic/public/live/";
    private final PublicLiveAccess access;
    @Override public Message<?> preSend(Message<?> message, MessageChannel channel) {
        var headers = StompHeaderAccessor.wrap(message);
        var destination = headers.getDestination();
        if(destination == null) return message;
        if(headers.getCommand() == StompCommand.SUBSCRIBE && (destination.contains("*") || destination.contains("?") || destination.contains("{")))
            throw new AccessDeniedException("Для публичной подписки требуется точный адрес");
        if(!destination.startsWith(PREFIX)) return message;
        if(headers.getCommand() == StompCommand.SEND) throw new AccessDeniedException("Публичный live доступен только для чтения");
        if(headers.getCommand() == StompCommand.SUBSCRIBE) {
            try { access.readable(Long.parseLong(destination.substring(PREFIX.length()))); }
            catch(NumberFormatException | PublicLiveException | DataAccessException exception) { throw new AccessDeniedException("Публичный live недоступен"); }
        }
        return message;
    }
}
