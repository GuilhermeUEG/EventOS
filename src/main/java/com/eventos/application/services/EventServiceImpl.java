package com.eventos.application.services;

import com.eventos.application.ports.input.EventUseCase;
import com.eventos.application.ports.output.ActivityRepository;
import com.eventos.application.ports.output.EventRepository;
import com.eventos.domain.exceptions.EntityNotFoundException;
import com.eventos.domain.model.Activity;
import com.eventos.domain.model.ActivityType;
import com.eventos.domain.model.Event;
import com.eventos.domain.model.EventStatus;
import com.eventos.domain.services.ConflictValidator;
import java.util.List;

public class EventServiceImpl implements EventUseCase {
    private final EventRepository eventRepository;
    private final ActivityRepository activityRepository;

    public EventServiceImpl(EventRepository eventRepository, ActivityRepository activityRepository) {
        this.eventRepository = eventRepository;
        this.activityRepository = activityRepository;
    }

    @Override
    public Event createEvent(Event event) {
        return eventRepository.save(event);
    }

    @Override
    public Event updateEvent(Event event) {
        // Valida se evento existe
        getEvent(event.getId());
        return eventRepository.save(event);
    }

    @Override
    public Event publishEvent(Long eventId) {
        Event event = getEvent(eventId);
        event.publish();
        return eventRepository.save(event);
    }

    @Override
    public Event cancelEvent(Long eventId) {
        Event event = getEvent(eventId);
        event.cancel();
        return eventRepository.save(event);
    }

    @Override
    public Event getEvent(Long id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Evento com ID " + id + " não encontrado."));
        List<Activity> activities = activityRepository.findByEventId(id);
        // Retorna evento carregado com atividades
        return new Event(event.getId(), event.getTitle(), event.getDescription(),
                event.getPeriod(), event.getStatus(), event.getOrganizerId(),
                activities, event.getMaxCapacity(), event.getCertificateEligibilityPolicy());
    }

    @Override
    public List<Event> listEvents() {
        return eventRepository.findAll();
    }

    @Override
    public List<Event> filterEvents(String search, String track, ActivityType type, EventStatus status) {
        return eventRepository.findByFilter(search, track, type, status);
    }

    @Override
    public Activity addActivityToEvent(Long eventId, Activity activity) {
        Event event = getEvent(eventId);
        List<Activity> existingActivities = activityRepository.findByEventId(eventId);
        
        // Valida conflito de horários e salas
        ConflictValidator.validateActivityRoomConflict(activity, existingActivities);
        
        // Adiciona à entidade Event (invariantes de período e escopo)
        event.addActivity(activity);
        activity.setEventId(eventId);
        
        return activityRepository.save(activity);
    }

    @Override
    public Activity getActivity(Long activityId) {
        return activityRepository.findById(activityId)
                .orElseThrow(() -> new EntityNotFoundException("Atividade com ID " + activityId + " não encontrada."));
    }

    @Override
    public List<Activity> getEventActivities(Long eventId) {
        return activityRepository.findByEventId(eventId);
    }
}
