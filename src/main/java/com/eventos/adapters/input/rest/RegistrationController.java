package com.eventos.adapters.input.rest;

import com.eventos.application.ports.input.RegistrationUseCase;
import com.eventos.domain.model.Registration;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Inscrições, agenda pessoal e cancelamento (RF-11 a RF-18). O participante da operação é sempre o
 * usuário da sessão, nunca um identificador enviado pelo cliente.
 */
class RegistrationController {
    private final RegistrationUseCase registrationUseCase;
    private final ApiSupport support;

    RegistrationController(RegistrationUseCase registrationUseCase, ApiSupport support) {
        this.registrationUseCase = registrationUseCase;
        this.support = support;
    }

    void register(Javalin app) {
        app.post("/api/registrations", this::registerForEvent);
        app.post("/api/registrations/{eventId}/activities/{activityId}", this::addActivity);
        app.delete("/api/registrations/{eventId}/activities/{activityId}", this::removeActivity);
        app.post("/api/registrations/{eventId}/cancel", this::cancelRegistration);
        app.get("/api/registrations/event/{eventId}", this::getEventRegistrations);
        app.get("/api/registrations/user/{userId}", this::getUserRegistrations);
        app.get("/api/registrations/agenda/{userId}/{eventId}", this::getAgenda);
    }

    private void registerForEvent(Context ctx) {
        Long userId = support.authorization().requireAuthenticated(ctx).userId();
        ApiRequests.Registration body = support.body(ctx, ApiRequests.Registration.class);
        Set<Long> activityIds = body.activityIds() == null ? Set.of() : new HashSet<>(body.activityIds());
        Registration reg = registrationUseCase.registerForEvent(support.required(body.eventId(), "eventId"), userId, activityIds);
        ctx.status(HttpStatus.CREATED).json(ApiMapper.registration(reg));
    }

    private void addActivity(Context ctx) {
        Long userId = support.authorization().requireAuthenticated(ctx).userId();
        Registration reg = registrationUseCase.addActivityToRegistration(
                support.pathId(ctx, "eventId"), userId, support.pathId(ctx, "activityId"));
        ctx.json(ApiMapper.registration(reg));
    }

    private void removeActivity(Context ctx) {
        Long userId = support.authorization().requireAuthenticated(ctx).userId();
        Registration reg = registrationUseCase.removeActivityFromRegistration(
                support.pathId(ctx, "eventId"), userId, support.pathId(ctx, "activityId"));
        ctx.json(ApiMapper.registration(reg));
    }

    private void cancelRegistration(Context ctx) {
        Long userId = support.authorization().requireAuthenticated(ctx).userId();
        registrationUseCase.cancelRegistration(support.pathId(ctx, "eventId"), userId);
        ctx.json(Map.of("message", "Inscrição cancelada com sucesso."));
    }

    private void getEventRegistrations(Context ctx) {
        Long eventId = support.pathId(ctx, "eventId");
        support.requireEventManager(ctx, eventId);
        ctx.json(registrationUseCase.getEventRegistrations(eventId).stream().map(ApiMapper::registration).toList());
    }

    private void getUserRegistrations(Context ctx) {
        Long userId = support.pathId(ctx, "userId");
        support.authorization().requireSelfOrStaff(ctx, userId);
        ctx.json(registrationUseCase.getUserRegistrations(userId).stream().map(ApiMapper::registration).toList());
    }

    private void getAgenda(Context ctx) {
        Long userId = support.pathId(ctx, "userId");
        support.authorization().requireSelfOrStaff(ctx, userId);
        ctx.json(ApiMapper.activities(registrationUseCase.getParticipantAgenda(userId, support.pathId(ctx, "eventId"))));
    }
}
