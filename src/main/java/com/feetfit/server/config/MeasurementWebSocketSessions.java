package com.feetfit.server.config;

import com.feetfit.server.event.UserWithdrawnEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class MeasurementWebSocketSessions {
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, Long> users = new ConcurrentHashMap<>();

    public void connected(WebSocketSession session) { sessions.put(session.getId(), session); }
    public void authenticate(String sessionId, Long userId) { users.put(sessionId, userId); }
    public void disconnected(String sessionId) { users.remove(sessionId); sessions.remove(sessionId); }

    @TransactionalEventListener
    public void onWithdrawn(UserWithdrawnEvent event) {
        users.forEach((sessionId, userId) -> {
            if (!event.userId().equals(userId)) return;
            WebSocketSession session = sessions.get(sessionId);
            try {
                if (session != null && session.isOpen()) session.close(CloseStatus.POLICY_VIOLATION);
            } catch (IOException exception) {
                log.warn("Could not close withdrawn user's socket. sessionId={}", sessionId);
            } finally {
                disconnected(sessionId);
            }
        });
    }
}
