package com.eventos.application.services;

import com.eventos.application.ports.input.EventUseCase;
import com.eventos.application.ports.output.EventRepository;
import com.eventos.domain.Event;
import com.eventos.domain.Activity;
import java.util.List;

public class EventServiceImpl implements EventUseCase {
    private final EventRepository eventRepository;

    public EventServiceImpl(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Override
    public Event createEvent(Event event) {
        return eventRepository.save(event);
    }

    @Override
    public Event getEvent(Long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Evento não encontrado com ID: " + id));
    }

    @Override
    public List<Event> listEvents() {
        return eventRepository.findAll();
    }

    @Override
    public Event updateEvent(Event event) {
        getEvent(event.getId()); // verify exists
        return eventRepository.save(event);
    }

    @Override
    public void deleteEvent(Long id) {
        eventRepository.deleteById(id);
    }

    @Override
    public Event addActivityToEvent(Long eventId, Activity activity) {
        Event event = getEvent(eventId);
        event.addActivity(activity);
        return eventRepository.save(event);
    }
}
