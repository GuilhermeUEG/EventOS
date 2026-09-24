package com.eventos.domain;

import com.eventos.domain.exceptions.BusinessRuleException;
import com.eventos.domain.exceptions.ConflictException;
import com.eventos.domain.model.Activity;
import com.eventos.domain.model.ActivityLocation;
import com.eventos.domain.model.ActivityType;
import com.eventos.domain.model.Event;
import com.eventos.domain.model.EventStatus;
import com.eventos.domain.model.Period;
import com.eventos.domain.policies.MinimumAttendancePercentagePolicy;
import com.eventos.domain.policies.SingleCheckInPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes de Domínio: Agregado Event")
public class EventTest {

    @Test
    @DisplayName("Não deve permitir publicar evento sem atividades cadastradas")
    void shouldNotPublishWithoutActivities() {
        Period period = new Period(LocalDateTime.of(2026, 9, 25, 8, 0), LocalDateTime.of(2026, 9, 27, 18, 0));
        Event event = new Event(1L, "Congresso", "Desc", period, EventStatus.DRAFT, 1L, null, 200, new MinimumAttendancePercentagePolicy(75.0));

        assertThrows(BusinessRuleException.class, event::publish);
        assertEquals(EventStatus.DRAFT, event.getStatus());
    }

    @Test
    @DisplayName("Deve publicar evento quando contiver ao menos uma atividade")
    void shouldPublishWithActivities() {
        Period eventPeriod = new Period(LocalDateTime.of(2026, 9, 25, 8, 0), LocalDateTime.of(2026, 9, 27, 18, 0));
        Event event = new Event(1L, "Congresso", "Desc", eventPeriod, EventStatus.DRAFT, 1L, null, 200, new MinimumAttendancePercentagePolicy(75.0));

        Period actPeriod = new Period(LocalDateTime.of(2026, 9, 25, 9, 0), LocalDateTime.of(2026, 9, 25, 11, 0));
        Activity activity = new Activity(1L, 1L, "Abertura", "Desc", actPeriod,
                new ActivityLocation("Auditório", "Geral", 100), ActivityType.LECTURE, 100, 0, null, new SingleCheckInPolicy(), false);

        event.addActivity(activity);
        event.publish();

        assertEquals(EventStatus.PUBLISHED, event.getStatus());
        assertTrue(event.isEnrollmentOpen());
    }

    @Test
    @DisplayName("Deve rejeitar atividade fora do período do evento")
    void shouldRejectActivityOutsideEventPeriod() {
        Period eventPeriod = new Period(LocalDateTime.of(2026, 9, 25, 8, 0), LocalDateTime.of(2026, 9, 27, 18, 0));
        Event event = new Event(1L, "Congresso", "Desc", eventPeriod, EventStatus.DRAFT, 1L, null, 200, null);

        // Atividade para a semana seguinte
        Period actPeriod = new Period(LocalDateTime.of(2026, 10, 5, 9, 0), LocalDateTime.of(2026, 10, 5, 11, 0));
        Activity activity = new Activity(1L, 1L, "Extra", "Desc", actPeriod,
                new ActivityLocation("Auditório", "Geral", 100), ActivityType.LECTURE, 100, 0, null, new SingleCheckInPolicy(), false);

        assertThrows(BusinessRuleException.class, () -> event.addActivity(activity));
    }

    @Test
    @DisplayName("Deve rejeitar atividades com conflito de mesma sala e horário sobreposto")
    void shouldRejectConflictingRoomActivities() {
        Period eventPeriod = new Period(LocalDateTime.of(2026, 9, 25, 8, 0), LocalDateTime.of(2026, 9, 27, 18, 0));
        Event event = new Event(1L, "Congresso", "Desc", eventPeriod, EventStatus.DRAFT, 1L, null, 200, null);

        Period p1 = new Period(LocalDateTime.of(2026, 9, 25, 9, 0), LocalDateTime.of(2026, 9, 25, 11, 0));
        Activity a1 = new Activity(1L, 1L, "Palestra 1", "Desc", p1,
                new ActivityLocation("Sala 101", "Geral", 50), ActivityType.LECTURE, 50, 0, null, new SingleCheckInPolicy(), false);
        event.addActivity(a1);

        Period p2 = new Period(LocalDateTime.of(2026, 9, 25, 10, 0), LocalDateTime.of(2026, 9, 25, 12, 0));
        Activity a2 = new Activity(2L, 1L, "Palestra 2", "Desc", p2,
                new ActivityLocation("Sala 101", "Geral", 50), ActivityType.LECTURE, 50, 0, null, new SingleCheckInPolicy(), false);

        assertThrows(ConflictException.class, () -> event.addActivity(a2));
    }
}
