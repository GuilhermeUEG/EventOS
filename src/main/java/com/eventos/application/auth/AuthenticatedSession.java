package com.eventos.application.auth;

import com.eventos.domain.model.UserRole;
import java.time.Instant;

/**
 * Sessão autenticada mantida no servidor. O token é opaco para o cliente.
 */
public record AuthenticatedSession(
        String token,
        Long userId,
        UserRole role,
        Instant expiresAt
) {
    public boolean isExpired() {
        return expiresAt == null || Instant.now().isAfter(expiresAt);
    }

    public boolean hasAnyRole(UserRole... allowedRoles) {
        if (allowedRoles == null) return false;
        for (UserRole allowed : allowedRoles) {
            if (role == allowed) return true;
        }
        return false;
    }
}
