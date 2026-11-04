package com.eventos.adapters.input.rest;

import com.eventos.application.auth.AuthenticatedSession;
import com.eventos.application.commands.EventUpdate;
import com.eventos.application.ports.input.EventUseCase;
import com.eventos.domain.exceptions.ValidationException;
import com.eventos.domain.model.Activity;
import com.eventos.domain.model.ActivityFilter;
import com.eventos.domain.model.ActivityLocation;
import com.eventos.domain.model.ActivityType;
import com.eventos.domain.model.Event;
import com.eventos.domain.model.EventStatus;
import com.eventos.domain.model.Period;
import com.eventos.domain.model.Speaker;
import com.eventos.domain.model.UserRole;
import com.eventos.domain.policies.AttendancePolicyFactory;
import com.eventos.domain.policies.CertificateEligibilityPolicy;
import com.eventos.domain.policies.CertificatePolicyFactory;
import com.eventos.domain.policies.CertificatePolicySpec;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Eventos, programação e atividades (RF-04 a RF-09, RF-10, RF-11). A visibilidade pública depende
 * do estado do evento (RN-04) e a alteração é restrita ao organizador responsável ou ao administrador (RN-17).
 */
class EventController {
    /** Teto de eventos por página quando `limit` é informado; sem `limit` a lista vem inteira (é o que o desktop usa). */
    private static final int MAX_PAGE_SIZE = 500;

    private final EventUseCase eventUseCase;
    private final ApiSupport support;

    EventController(EventUseCase eventUseCase, ApiSupport support) {
        this.eventUseCase = eventUseCase;
        this.support = support;
    }

    void register(Javalin app) {
        app.get("/api/events", this::listEvents);
        app.get("/api/events/{id}", this::getEvent);
        app.post("/api/events", this::createEvent);
        app.put("/api/events/{id}", this::updateEvent);
        app.put("/api/events/{id}/certificate-policy", this::changeCertificatePolicy);
        app.post("/api/events/{id}/publish", this::publishEvent);
        app.post("/api/events/{id}/cancel", this::cancelEvent);
        app.post("/api/events/{id}/finish", this::finishEvent);
        app.get("/api/events/{id}/activities", this::listActivities);
        app.post("/api/events/{id}/activities", this::addActivity);
        app.get("/api/activities/{id}", this::getActivity);
    }

    private void listEvents(Context ctx) {
        ActivityType type = support.parseEnum(ActivityType.class, ctx.queryParam("type"), "type");
        EventStatus status = support.parseEnum(EventStatus.class, ctx.queryParam("status"), "status");
        AuthenticatedSession session = support.authorization().findSession(ctx).orElse(null);
        boolean staff = session != null && session.hasAnyRole(UserRole.ADMIN, UserRole.ORGANIZER);

        List<Event> events = eventUseCase.filterEvents(ctx.queryParam("search"), ctx.queryParam("track"), type, status, staff);
        if (session != null && session.role() == UserRole.ORGANIZER) {
            // Rascunhos e eventos encerrados de outros organizadores não são exibidos.
            events = events.stream()
                    .filter(e -> e.isPubliclyVisible() || session.userId().equals(e.getOrganizerId()))
                    .toList();
        }
        if (staff && "true".equalsIgnoreCase(ctx.queryParam("mine"))) {
            events = events.stream().filter(e -> session.userId().equals(e.getOrganizerId())).toList();
        }
        // Paginação opcional: o total vai no cabeçalho para a tela dizer "mostrando X de N".
        ctx.header("X-Total-Count", String.valueOf(events.size()));
        int offset = nonNegativeInt(ctx.queryParam("offset"), "offset", 0);
        int limit = ctx.queryParam("limit") == null
                ? Integer.MAX_VALUE
                : Math.min(nonNegativeInt(ctx.queryParam("limit"), "limit", MAX_PAGE_SIZE), MAX_PAGE_SIZE);
        ctx.json(ApiMapper.events(events.stream().skip(offset).limit(limit).toList()));
    }

    private static int nonNegativeInt(String text, String name, int fallback) {
        if (text == null || text.isBlank()) return fallback;
        try {
            int value = Integer.parseInt(text.trim());
            if (value >= 0) return value;
        } catch (NumberFormatException ignored) {
            // cai na mensagem abaixo
        }
        throw new ValidationException("O parâmetro '" + name + "' deve ser um número inteiro maior ou igual a zero.");
    }

    private void getEvent(Context ctx) {
        Event event = eventUseCase.getEvent(support.pathId(ctx, "id"));
        support.requireVisibleOrManager(ctx, event);
        ctx.json(ApiMapper.event(event));
    }

    private void createEvent(Context ctx) {
        AuthenticatedSession session = support.authorization().requireRole(ctx, UserRole.ADMIN, UserRole.ORGANIZER);
        ApiRequests.CreateEvent body = support.body(ctx, ApiRequests.CreateEvent.class);
        LocalDateTime start = support.parseDateTime(body.start());
        LocalDateTime end = support.parseDateTime(body.end());

        Event event = new Event(null, body.title(), body.description(), new Period(start, end), EventStatus.DRAFT,
                session.userId(), new ArrayList<>(), body.maxCapacity() != null ? body.maxCapacity() : 500,
                body.certificatePolicy() != null ? toPolicy(body.certificatePolicy()) : CertificatePolicyFactory.defaultPolicy());
        event.configureRegistration(
                body.activitySelectionEnabled() == null || body.activitySelectionEnabled(),
                body.activitySelectionRequired() != null && body.activitySelectionRequired(),
                body.registrationDeadline() != null && !body.registrationDeadline().isBlank()
                        ? support.parseDateTime(body.registrationDeadline()) : start,
                body.timeZone() != null && !body.timeZone().isBlank() ? body.timeZone() : Event.DEFAULT_TIME_ZONE);
        ctx.status(HttpStatus.CREATED).json(ApiMapper.event(eventUseCase.createEvent(event)));
    }

    private void updateEvent(Context ctx) {
        Long id = support.pathId(ctx, "id");
        support.requireEventManager(ctx, id);
        ApiRequests.UpdateEvent body = support.body(ctx, ApiRequests.UpdateEvent.class);

        Period period = null;
        if (body.start() != null || body.end() != null) {
            Period current = eventUseCase.getEvent(id).getPeriod();
            period = new Period(
                    body.start() != null ? support.parseDateTime(body.start()) : current.getStart(),
                    body.end() != null ? support.parseDateTime(body.end()) : current.getEnd());
        }
        EventUpdate update = new EventUpdate(body.title(), body.description(), period, body.maxCapacity(),
                body.activitySelectionEnabled(), body.activitySelectionRequired(),
                body.registrationDeadline() != null && !body.registrationDeadline().isBlank()
                        ? support.parseDateTime(body.registrationDeadline()) : null,
                body.timeZone() != null && !body.timeZone().isBlank() ? body.timeZone() : null);
        ctx.json(ApiMapper.event(eventUseCase.updateEvent(id, update)));
    }

    private void changeCertificatePolicy(Context ctx) {
        Long id = support.pathId(ctx, "id");
        support.requireEventManager(ctx, id);
        ApiRequests.CertificatePolicy body = support.body(ctx, ApiRequests.CertificatePolicy.class);
        ctx.json(ApiMapper.event(eventUseCase.changeCertificatePolicy(id, toPolicy(body))));
    }

    private void publishEvent(Context ctx) {
        Long id = support.pathId(ctx, "id");
        support.requireEventManager(ctx, id);
        ctx.json(ApiMapper.event(eventUseCase.publishEvent(id)));
    }

    private void cancelEvent(Context ctx) {
        Long id = support.pathId(ctx, "id");
        support.requireEventManager(ctx, id);
        ctx.json(ApiMapper.event(eventUseCase.cancelEvent(id)));
    }

    private void finishEvent(Context ctx) {
        Long id = support.pathId(ctx, "id");
        support.requireEventManager(ctx, id);
        ctx.json(ApiMapper.event(eventUseCase.finishEvent(id)));
    }

    /** Programação do evento com filtros combináveis: date, from, to, track, type, room e search (RF-09). */
    private void listActivities(Context ctx) {
        Long eventId = support.pathId(ctx, "id");
        support.requireVisibleOrManager(ctx, eventUseCase.getEvent(eventId));
        ActivityFilter filter = new ActivityFilter(
                support.parseDate(ctx.queryParam("date")),
                support.parseTime(ctx.queryParam("from")),
                support.parseTime(ctx.queryParam("to")),
                ctx.queryParam("track"),
                support.parseEnum(ActivityType.class, ctx.queryParam("type"), "type"),
                ctx.queryParam("room"),
                ctx.queryParam("search"));
        ctx.json(ApiMapper.activities(eventUseCase.searchActivities(eventId, filter)));
    }

    private void addActivity(Context ctx) {
        Long eventId = support.pathId(ctx, "id");
        support.requireEventManager(ctx, eventId);
        ApiRequests.CreateActivity body = support.body(ctx, ApiRequests.CreateActivity.class);

        int capacity = body.capacity() != null ? body.capacity() : 50;
        List<Speaker> speakers = new ArrayList<>();
        if (body.speakers() != null) {
            for (ApiRequests.Speaker s : body.speakers()) {
                speakers.add(new Speaker(null, s.name(), s.role(), s.bio(), s.photoUrl()));
            }
        }
        ActivityType type = support.parseEnum(ActivityType.class, body.type(), "type");
        Activity activity = new Activity(null, eventId, body.title(), body.description(),
                new Period(support.parseDateTime(body.start()), support.parseDateTime(body.end())),
                new ActivityLocation(body.room(), body.track(), capacity),
                type != null ? type : ActivityType.LECTURE, capacity, 0, speakers,
                AttendancePolicyFactory.create(body.attendancePolicy()),
                body.requiresRegistration() == null || body.requiresRegistration());
        ctx.status(HttpStatus.CREATED).json(ApiMapper.activity(eventUseCase.addActivityToEvent(eventId, activity)));
    }

    private void getActivity(Context ctx) {
        Activity activity = eventUseCase.getActivity(support.pathId(ctx, "id"));
        support.requireVisibleOrManager(ctx, eventUseCase.getEvent(activity.getEventId()));
        ctx.json(ApiMapper.activity(activity));
    }

    private CertificateEligibilityPolicy toPolicy(ApiRequests.CertificatePolicy request) {
        return CertificatePolicyFactory.create(new CertificatePolicySpec(request.type(), request.percentage(),
                request.minActivities(), request.activityIds() == null ? null : new HashSet<>(request.activityIds())));
    }
}
