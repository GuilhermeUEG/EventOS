package com.eventos.application.ports.input;

import com.eventos.domain.Event;
import com.eventos.domain.Activity;
import java.util.List;

public interface EventUseCase {
    Event createEvent(Event event);
    Event getEvent(Long id);
    List<Event> listEvents();
    Event updateEvent(Event event);
    void deleteEvent(Long id);
    Event addActivityToEvent(Long eventId, Activity activity);
}
