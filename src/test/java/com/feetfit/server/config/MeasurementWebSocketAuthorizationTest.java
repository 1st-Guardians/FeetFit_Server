package com.feetfit.server.config;

import com.feetfit.server.domain.enums.UserStatus;
import com.feetfit.server.event.UserWithdrawnEvent;
import com.feetfit.server.jwt.TokenProvider;
import com.feetfit.server.repository.MeasurementSessionRepository;
import com.feetfit.server.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MeasurementWebSocketAuthorizationTest {
    private final UserRepository users = mock(UserRepository.class);
    private final MeasurementSessionRepository measurements = mock(MeasurementSessionRepository.class);
    private final TokenProvider tokens = new TokenProvider("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=", users);
    private final MeasurementWebSocketSessions sessions = new MeasurementWebSocketSessions();
    private final MeasurementWebSocketAuthorization authorization = new MeasurementWebSocketAuthorization(tokens, measurements, sessions);
    private Authentication user;

    @BeforeEach
    void setup() {
        when(users.existsByIdAndStatus(7L, UserStatus.ACTIVE)).thenReturn(true);
        user = tokens.getAuthentication(tokens.createAccessToken(7L));
    }

    @Test
    void connectRequiresValidActiveUserToken() {
        var connect = headers(StompCommand.CONNECT, null);
        assertThatThrownBy(() -> send(connect)).isInstanceOf(MessageDeliveryException.class);
        connect.setNativeHeader("Authorization", "Bearer " + user.getCredentials());
        assertThatCode(() -> send(connect)).doesNotThrowAnyException();
        assertThat(connect.getUser().getName()).isEqualTo("7");
        when(users.existsByIdAndStatus(7L, UserStatus.ACTIVE)).thenReturn(false);
        assertThatThrownBy(() -> send(connect)).isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void subscriptionsAreRestrictedToOwnedUserAndMeasurementTopics() {
        when(measurements.existsByIdAndUserId(45L, 7L)).thenReturn(true);
        assertThatCode(() -> send(headers(StompCommand.SUBSCRIBE, "/topic/users/7/measurements"))).doesNotThrowAnyException();
        assertThatCode(() -> send(headers(StompCommand.SUBSCRIBE, "/topic/measurements/45"))).doesNotThrowAnyException();
        for (String topic : new String[]{"/topic/users/8/measurements", "/topic/measurements/46", "/topic/**", "/topic/measurements/*"}) {
            assertThatThrownBy(() -> send(headers(StompCommand.SUBSCRIBE, topic))).isInstanceOf(MessageDeliveryException.class);
        }
        assertThatThrownBy(() -> send(headers(StompCommand.SEND, "/app/measurements"))).isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void deletionInvalidatesSubscriptionAndClosesOnlyThatUsersActualSockets() throws Exception {
        var own = socket("own", 7L);
        var other = socket("other", 8L);
        when(users.existsByIdAndStatus(7L, UserStatus.ACTIVE)).thenReturn(false);
        assertThatThrownBy(() -> send(headers(StompCommand.SUBSCRIBE, "/topic/users/7/measurements")))
                .isInstanceOf(MessageDeliveryException.class);
        sessions.onWithdrawn(new UserWithdrawnEvent(7L));
        verify(own).close(CloseStatus.POLICY_VIOLATION);
        verify(other, never()).close(any());
    }

    private StompHeaderAccessor headers(StompCommand command, String destination) {
        var headers = StompHeaderAccessor.create(command);
        headers.setSessionId("test-session");
        if (command != StompCommand.CONNECT) headers.setUser(user);
        headers.setDestination(destination);
        headers.setLeaveMutable(true);
        return headers;
    }

    private void send(StompHeaderAccessor headers) {
        authorization.preSend(MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()), null);
    }

    private WebSocketSession socket(String id, Long userId) {
        var socket = mock(WebSocketSession.class);
        when(socket.getId()).thenReturn(id);
        when(socket.isOpen()).thenReturn(true);
        sessions.connected(socket);
        sessions.authenticate(id, userId);
        return socket;
    }
}
