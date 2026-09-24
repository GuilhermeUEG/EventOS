package com.eventos.application.ports.output;

import com.eventos.domain.model.ActivityType;
import com.eventos.domain.model.Event;
import com.eventos.domain.model.EventStatus;
import java.util.List;
import java.util.Optional;

public interface EventRepository {
    Event save(Event event);
    Optional<Event> findById(Long id);
    List<Event> findAll();
    List<Event> findByFilter(String search, String track, ActivityType type, EventStatus status);
    void delete(Long id);
}
