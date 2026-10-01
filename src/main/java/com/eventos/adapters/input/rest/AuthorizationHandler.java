package com.eventos.adapters.input.rest;

import com.eventos.application.auth.AuthenticatedSession;
import com.eventos.application.ports.output.SessionStore;
import com.eventos.domain.exceptions.UnauthorizedException;
import com.eventos.domain.model.UserRole;
import io.javalin.http.Context;
import java.util.Optional;

/**
 * Autoriza operações no servidor; ocultar botões no cliente nunca é considerado autorização.
 */
public class AuthorizationHandler {
    private final SessionStore sessionStore;

    public AuthorizationHandler(SessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    public AuthenticatedSession requireAuthenticated(Context ctx) {
        return findSession(ctx)
                .orElseThrow(() -> new UnauthorizedException("Autenticação obrigatória ou sessão expirada."));
    }

    public Optional<AuthenticatedSession> findSession(Context ctx) {
        return sessionStore.findValid(extractBearerToken(ctx));
    }

    public AuthenticatedSession requireRole(Context ctx, UserRole... roles) {
        AuthenticatedSession session = requireAuthenticated(ctx);
        if (!session.hasAnyRole(roles)) {
            throw new UnauthorizedException("Seu perfil não possui permissão para esta operação.");
        }
        return session;
    }

    public AuthenticatedSession requireSelfOrStaff(Context ctx, Long requestedUserId) {
        AuthenticatedSession session = requireAuthenticated(ctx);
        if (!session.userId().equals(requestedUserId)
                && !session.hasAnyRole(UserRole.ADMIN, UserRole.ORGANIZER)) {
            throw new UnauthorizedException("Não é permitido acessar ou alterar dados de outro participante.");
        }
        return session;
    }

    public String extractBearerToken(Context ctx) {
        String header = ctx.header("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return null;
        return header.substring("Bearer ".length()).trim();
    }
}
