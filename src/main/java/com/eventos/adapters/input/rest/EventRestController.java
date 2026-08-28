package com.eventos.adapters.input.rest;

import com.eventos.application.ports.input.EventUseCase;
import com.eventos.domain.Event;
import com.eventos.domain.Activity;
import io.javalin.Javalin;
import io.javalin.http.Context;
import java.util.List;

public class EventRestController {
    private final EventUseCase eventUseCase;

    public EventRestController(EventUseCase eventUseCase, Javalin app) {
        this.eventUseCase = eventUseCase;
        setupRoutes(app);
    }

    private void setupRoutes(Javalin app) {
        app.post("/api/events", this::createEvent);
        app.get("/api/events/{id}", this::getEvent);
        app.get("/api/events", this::listEvents);
        app.post("/api/events/{eventId}/activities", this::addActivity);
    }

    private void createEvent(Context ctx) {
        try {
            Event event = ctx.bodyAsClass(Event.class);
            Event saved = eventUseCase.createEvent(event);
            ctx.status(201).json(saved);
        } catch (Exception e) {
            ctx.status(400).result("Erro ao criar evento: " + e.getMessage());
        }
    }

    private void getEvent(Context ctx) {
        try {
            Long id = Long.parseLong(ctx.pathParam("id"));
            Event event = eventUseCase.getEvent(id);
            ctx.json(event);
        } catch (IllegalArgumentException e) {
            ctx.status(404).result(e.getMessage());
        } catch (Exception e) {
            ctx.status(500).result("Erro interno: " + e.getMessage());
        }
    }

    private void listEvents(Context ctx) {
        try {
            List<Event> events = eventUseCase.listEvents();
            ctx.json(events);
        } catch (Exception e) {
            ctx.status(500).result("Erro ao listar eventos: " + e.getMessage());
        }
    }

    private void addActivity(Context ctx) {
        try {
            Long eventId = Long.parseLong(ctx.pathParam("eventId"));
            Activity activity = ctx.bodyAsClass(Activity.class);
            Event updated = eventUseCase.addActivityToEvent(eventId, activity);
            ctx.json(updated);
        } catch (Exception e) {
            ctx.status(400).result("Erro ao adicionar atividade: " + e.getMessage());
        }
    }
}
