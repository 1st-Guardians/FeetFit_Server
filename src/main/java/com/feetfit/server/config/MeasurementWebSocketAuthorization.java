package com.feetfit.server.config;

import com.feetfit.server.jwt.TokenProvider;
import com.feetfit.server.repository.MeasurementSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class MeasurementWebSocketAuthorization implements ChannelInterceptor {
    private static final Pattern USER_TOPIC = Pattern.compile("^/topic/users/(\\d+)/measurements$");
    private static final Pattern SESSION_TOPIC = Pattern.compile("^/topic/measurements/(\\d+)$");
    private final TokenProvider tokens;
    private final MeasurementSessionRepository measurements;
    private final MeasurementWebSocketSessions sessions;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        var headers = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (headers == null || headers.getCommand() == null) return message;
        if (headers.getCommand() == StompCommand.DISCONNECT || headers.getCommand() == StompCommand.UNSUBSCRIBE) return message;
        try {
            if (headers.getCommand() == StompCommand.CONNECT) {
                String authorization = headers.getFirstNativeHeader("Authorization");
                String token = authorization != null && authorization.startsWith("Bearer ")
                        ? authorization.substring(7) : existingToken(headers);
                Authentication authentication = authenticate(token);
                headers.setUser(authentication);
                sessions.authenticate(headers.getSessionId(), Long.parseLong(authentication.getName()));
                // Binding before rechecking closes the race with account deletion's after-commit disconnect.
                authenticate(token);
            } else {
                Authentication authentication = authenticate(existingToken(headers));
                if (headers.getCommand() == StompCommand.SUBSCRIBE) {
                    requireOwnedTopic(authentication, headers.getDestination());
                } else if (headers.getCommand() == StompCommand.SEND) {
                    throw new IllegalArgumentException("Client message sending is not supported.");
                }
            }
            return message;
        } catch (RuntimeException exception) {
            throw new MessageDeliveryException("Measurement socket authorization failed.");
        }
    }

    private Authentication authenticate(String token) {
        if (token == null || !tokens.validateToken(token)) throw new IllegalArgumentException("Missing valid socket token.");
        return tokens.getAuthentication(token);
    }

    private String existingToken(StompHeaderAccessor headers) {
        return headers.getUser() instanceof Authentication authentication
                && authentication.getCredentials() instanceof String token ? token : null;
    }

    private void requireOwnedTopic(Authentication user, String destination) {
        if (destination == null) throw new IllegalArgumentException();
        Long userId = Long.valueOf(user.getName());
        var userTopic = USER_TOPIC.matcher(destination);
        if (userTopic.matches() && userId.equals(Long.valueOf(userTopic.group(1)))) return;
        var measurementTopic = SESSION_TOPIC.matcher(destination);
        if (measurementTopic.matches() && measurements.existsByIdAndUserId(Long.valueOf(measurementTopic.group(1)), userId)) return;
        throw new IllegalArgumentException("Topic is not owned by the authenticated user.");
    }
}
