package com.eventos.application.ports.input;

import com.eventos.application.commands.EventUpdate;
import com.eventos.domain.model.Activity;
import com.eventos.domain.model.ActivityFilter;
import com.eventos.domain.model.ActivityType;
import com.eventos.domain.model.Event;
import com.eventos.domain.model.EventStatus;
import com.eventos.domain.policies.CertificateEligibilityPolicy;
import java.time.LocalDateTime;
import java.util.List;

public interface EventUseCase {
    Event createEvent(Event event);

    Event updateEvent(Long eventId, EventUpdate update);

    Event changeCertificatePolicy(Long eventId, CertificateEligibilityPolicy policy);

    Event publishEvent(Long eventId);

    Event cancelEvent(Long eventId);

    Event finishEvent(Long eventId);

    /** Evento com sua programação carregada. */
    Event getEvent(Long id);

    List<Event> listEvents();

    /**
     * Consulta eventos. Quando {@code staffView} é falso, apenas eventos publicamente visíveis
     * (RN-04) são devolvidos, independentemente do status solicitado.
     */
    List<Event> filterEvents(String search, String track, ActivityType type, EventStatus status, boolean staffView);

    /**
     * Eventos publicamente visíveis para a página inicial, já com a programação: primeiro os que ainda não
     * terminaram (do mais próximo ao mais distante) e depois os que já passaram (do mais recente).
     */
    List<Event> featuredEvents(int limit, LocalDateTime now);

    Activity addActivityToEvent(Long eventId, Activity activity);

    Activity getActivity(Long activityId);

    List<Activity> getEventActivities(Long eventId);

    List<Activity> searchActivities(Long eventId, ActivityFilter filter);
}
