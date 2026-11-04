package com.eventos.adapters.input.rest;

import com.eventos.adapters.input.rest.ApiDtos.AuthResponse;
import com.eventos.adapters.input.rest.ApiDtos.UserResponse;
import com.eventos.application.auth.AuthenticatedSession;
import com.eventos.application.ports.input.AuthUseCase;
import com.eventos.application.ports.output.SessionStore;
import com.eventos.domain.model.User;
import com.eventos.domain.model.UserRole;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import java.util.Map;

/**
 * Autenticação e gestão de usuários (RF-01 a RF-03). O cadastro público sempre cria participantes;
 * somente administradores criam organizadores e alteram perfis.
 */
class AuthController {
    private final AuthUseCase authUseCase;
    private final SessionStore sessionStore;
    private final ApiSupport support;

    AuthController(AuthUseCase authUseCase, SessionStore sessionStore, ApiSupport support) {
        this.authUseCase = authUseCase;
        this.sessionStore = sessionStore;
        this.support = support;
    }

    void register(Javalin app) {
        app.post("/api/auth/register", this::registerAccount);
        app.post("/api/auth/login", this::login);
        app.post("/api/auth/logout", this::logout);
        app.get("/api/auth/me", this::me);
        app.get("/api/users", this::listUsers);
        app.post("/api/users", this::createUser);
        app.get("/api/users/{id}", this::getUser);
        app.put("/api/users/{id}", this::updateUser);
        app.put("/api/users/{id}/password", this::changePassword);
        app.put("/api/users/{id}/role", this::changeRole);
    }

    private void registerAccount(Context ctx) {
        ApiRequests.Register body = support.body(ctx, ApiRequests.Register.class);
        User user = authUseCase.register(body.name(), body.email(), body.password());
        ctx.status(HttpStatus.CREATED).json(authResponse(user, sessionStore.create(user)));
    }

    private void login(Context ctx) {
        ApiRequests.Login body = support.body(ctx, ApiRequests.Login.class);
        User user = authUseCase.login(body.email(), body.password());
        ctx.json(authResponse(user, sessionStore.create(user)));
    }

    private void logout(Context ctx) {
        support.authorization().requireAuthenticated(ctx);
        sessionStore.revoke(support.authorization().extractBearerToken(ctx));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void me(Context ctx) {
        AuthenticatedSession session = support.authorization().requireAuthenticated(ctx);
        ctx.json(ApiMapper.user(authUseCase.getUserById(session.userId())));
    }

    private void listUsers(Context ctx) {
        // Dados pessoais de todos os usuários: somente o administrador (RNF-11, coleta mínima).
        support.authorization().requireRole(ctx, UserRole.ADMIN);
        ctx.json(authUseCase.listUsers().stream().map(ApiMapper::user).toList());
    }

    private void createUser(Context ctx) {
        AuthenticatedSession session = support.authorization().requireRole(ctx, UserRole.ADMIN);
        ApiRequests.CreateUser body = support.body(ctx, ApiRequests.CreateUser.class);
        UserRole role = support.parseEnum(UserRole.class, support.required(body.role(), "role"), "role");
        User created = authUseCase.createUser(session.userId(), body.name(), body.email(), body.password(), role);
        ctx.status(HttpStatus.CREATED).json(ApiMapper.user(created));
    }

    private void getUser(Context ctx) {
        Long id = support.pathId(ctx, "id");
        support.authorization().requireSelfOrStaff(ctx, id);
        ctx.json(ApiMapper.user(authUseCase.getUserById(id)));
    }

    private void updateUser(Context ctx) {
        Long id = support.pathId(ctx, "id");
        support.authorization().requireSelfOrStaff(ctx, id);
        ApiRequests.UpdateProfile body = support.body(ctx, ApiRequests.UpdateProfile.class);
        ctx.json(ApiMapper.user(authUseCase.updateProfile(id, body.name())));
    }

    /** Somente o próprio usuário troca a senha, informando a senha atual. */
    private void changePassword(Context ctx) {
        Long id = support.pathId(ctx, "id");
        AuthenticatedSession session = support.authorization().requireAuthenticated(ctx);
        if (!session.userId().equals(id)) {
            throw new com.eventos.domain.exceptions.ForbiddenException("Somente o próprio usuário pode alterar a sua senha.");
        }
        ApiRequests.ChangePassword body = support.body(ctx, ApiRequests.ChangePassword.class);
        authUseCase.changePassword(id, body.currentPassword(), body.newPassword());
        ctx.json(Map.of("message", "Senha alterada com sucesso."));
    }

    private void changeRole(Context ctx) {
        AuthenticatedSession session = support.authorization().requireRole(ctx, UserRole.ADMIN);
        Long id = support.pathId(ctx, "id");
        ApiRequests.ChangeRole body = support.body(ctx, ApiRequests.ChangeRole.class);
        UserRole role = support.parseEnum(UserRole.class, support.required(body.role(), "role"), "role");
        UserResponse updated = ApiMapper.user(authUseCase.changeRole(session.userId(), id, role));
        ctx.json(updated);
    }

    private AuthResponse authResponse(User user, AuthenticatedSession session) {
        return new AuthResponse(session.token(), session.expiresAt(), ApiMapper.user(user));
    }
}
