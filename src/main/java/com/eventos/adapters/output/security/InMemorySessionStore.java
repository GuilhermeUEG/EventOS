package com.eventos.adapters.output.security;

import com.eventos.application.auth.AuthenticatedSession;
import com.eventos.application.ports.output.SessionStore;
import com.eventos.domain.model.User;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Armazena sessões apenas em memória. Reiniciar o servidor revoga todos os tokens.
 */
public class InMemorySessionStore implements SessionStore {
    private static final Duration SESSION_DURATION = Duration.ofHours(8);
    private final SecureRandom secureRandom = new SecureRandom();
    private final ConcurrentMap<String, AuthenticatedSession> sessions = new ConcurrentHashMap<>();

    @Override
    public AuthenticatedSession create(User user) {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        AuthenticatedSession session = new AuthenticatedSession(
                token, user.getId(), user.getRole(), Instant.now().plus(SESSION_DURATION));
        sessions.put(token, session);
        return session;
    }

    @Override
    public Optional<AuthenticatedSession> findValid(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        AuthenticatedSession session = sessions.get(token);
        if (session == null) return Optional.empty();
        if (session.isExpired()) {
            sessions.remove(token);
            return Optional.empty();
        }
        return Optional.of(session);
    }

    @Override
    public void revoke(String token) {
        if (token != null) sessions.remove(token);
    }
}
