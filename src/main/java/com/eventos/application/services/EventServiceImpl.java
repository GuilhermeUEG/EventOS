package com.eventos.application.services;

import com.eventos.application.commands.EventUpdate;
import com.eventos.application.ports.input.EventUseCase;
import com.eventos.application.ports.output.ActivityRepository;
import com.eventos.application.ports.output.EventRepository;
import com.eventos.domain.exceptions.EntityNotFoundException;
import com.eventos.domain.exceptions.ValidationException;
import com.eventos.domain.model.Activity;
import com.eventos.domain.model.ActivityFilter;
import com.eventos.domain.model.ActivityType;
import com.eventos.domain.model.Event;
import com.eventos.domain.model.EventStatus;
import com.eventos.domain.policies.CertificateEligibilityPolicy;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

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
    public Event updateEvent(Long eventId, EventUpdate update) {
        Event event = getEvent(eventId);
        event.updateDetails(update.title(), update.description(), update.period(),
                update.maxCapacity() != null ? update.maxCapacity() : 0);
        boolean registrationChanged = update.activitySelectionEnabled() != null
                || update.activitySelectionRequired() != null
                || update.registrationDeadline() != null
                || update.timeZone() != null;
        if (registrationChanged) {
            event.configureRegistration(
                    update.activitySelectionEnabled() != null ? update.activitySelectionEnabled() : event.isActivitySelectionEnabled(),
                    update.activitySelectionRequired() != null ? update.activitySelectionRequired() : event.isActivitySelectionRequired(),
                    update.registrationDeadline() != null ? update.registrationDeadline() : event.getRegistrationDeadline(),
                    update.timeZone() != null ? update.timeZone() : event.getTimeZone());
        }
        return eventRepository.save(event);
    }

    @Override
    public Event changeCertificatePolicy(Long eventId, CertificateEligibilityPolicy policy) {
        Event event = getEvent(eventId);
        Set<Long> eventActivityIds = new HashSet<>();
        event.getActivities().forEach(a -> eventActivityIds.add(a.getId()));
        for (Long required : policy.spec().activityIds()) {
            if (!eventActivityIds.contains(required)) {
                throw new ValidationException("A atividade " + required + " não pertence a este evento.");
            }
        }
        event.changeCertificatePolicy(policy);
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
    public Event finishEvent(Long eventId) {
        Event event = getEvent(eventId);
        event.finish();
        return eventRepository.save(event);
    }

    @Override
    public Event getEvent(Long id) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Evento com ID " + id + " não encontrado."));
        List<Activity> activities = activityRepository.findByEventId(id);
        Event loaded = new Event(event.getId(), event.getTitle(), event.getDescription(),
                event.getPeriod(), event.getStatus(), event.getOrganizerId(),
                activities, event.getMaxCapacity(), event.getCertificateEligibilityPolicy());
        loaded.configureRegistration(event.isActivitySelectionEnabled(), event.isActivitySelectionRequired(),
                event.getRegistrationDeadline(), event.getTimeZone());
        return loaded;
    }

    @Override
    public List<Event> listEvents() {
        return eventRepository.findAll();
    }

    @Override
    public List<Event> filterEvents(String search, String track, ActivityType type, EventStatus status, boolean staffView) {
        List<Event> events = eventRepository.findByFilter(search, track, type, status);
        if (status == null) {
            // Eventos cancelados não entram na listagem comum; só aparecem a quem pede a situação explicitamente.
            events = events.stream().filter(e -> e.getStatus() != EventStatus.CANCELLED).toList();
        }
        return staffView ? events : events.stream().filter(Event::isPubliclyVisible).toList();
    }

    @Override
    public List<Event> featuredEvents(int limit, LocalDateTime now) {
        List<Event> visible = filterEvents(null, null, null, null, false);
        Comparator<Event> byStart = Comparator.comparing((Event e) -> e.getPeriod().getStart());
        List<Event> upcoming = visible.stream().filter(e -> !e.getPeriod().getEnd().isBefore(now)).sorted(byStart).toList();
        List<Event> past = visible.stream().filter(e -> e.getPeriod().getEnd().isBefore(now)).sorted(byStart.reversed()).toList();
        return Stream.concat(upcoming.stream(), past.stream())
                .limit(Math.max(limit, 0))
                .map(e -> getEvent(e.getId()))
                .toList();
    }

    @Override
    public Activity addActivityToEvent(Long eventId, Activity activity) {
        Event event = getEvent(eventId);
        // O agregado valida período, conflito de sala e estado do evento, e vincula a atividade a ele.
        event.addActivity(activity);
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

    @Override
    public List<Activity> searchActivities(Long eventId, ActivityFilter filter) {
        eventRepository.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Evento com ID " + eventId + " não encontrado."));
        return (filter != null ? filter : ActivityFilter.none()).apply(activityRepository.findByEventId(eventId));
    }
}
