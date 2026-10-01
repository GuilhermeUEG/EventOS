package com.eventos.application.ports.output;

import com.eventos.application.auth.AuthenticatedSession;
import com.eventos.domain.model.User;
import java.util.Optional;

public interface SessionStore {
    AuthenticatedSession create(User user);
    Optional<AuthenticatedSession> findValid(String token);
    void revoke(String token);
}
