package com.eventos.application.ports.input;

import com.eventos.domain.model.Activity;
import com.eventos.domain.model.ActivityType;
import com.eventos.domain.model.Event;
import com.eventos.domain.model.EventStatus;
import java.util.List;

public interface EventUseCase {
    Event createEvent(Event event);
    Event updateEvent(Event event);
    Event publishEvent(Long eventId);
    Event cancelEvent(Long eventId);
    Event finishEvent(Long eventId);
    Event getEvent(Long id);
    List<Event> listEvents();
    List<Event> filterEvents(String search, String track, ActivityType type, EventStatus status);
    Activity addActivityToEvent(Long eventId, Activity activity);
    Activity getActivity(Long activityId);
    List<Activity> getEventActivities(Long eventId);
}
