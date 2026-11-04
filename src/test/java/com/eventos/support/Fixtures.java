package com.eventos.support;

import com.eventos.domain.model.Activity;
import com.eventos.domain.model.ActivityLocation;
import com.eventos.domain.model.ActivityType;
import com.eventos.domain.model.Event;
import com.eventos.domain.model.EventStatus;
import com.eventos.domain.model.Period;
import com.eventos.domain.model.User;
import com.eventos.domain.policies.AttendancePolicy;
import com.eventos.domain.policies.CertificateEligibilityPolicy;
import com.eventos.domain.policies.MinimumAttendancePercentagePolicy;
import com.eventos.domain.policies.SingleCheckInPolicy;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Massa de dados dos testes: evento que começa amanhã às 08:00 e dura até o fim do dia seguinte.
 */
public final class Fixtures {
    private Fixtures() {
    }

    public static LocalDateTime firstDay() {
        return LocalDate.now().plusDays(1).atTime(8, 0);
    }

    public static Event newEvent(TestApp app, User organizer, int capacity) {
        return newEvent(app, organizer, capacity, new MinimumAttendancePercentagePolicy(50));
    }

    public static Event newEvent(TestApp app, User organizer, int capacity, CertificateEligibilityPolicy policy) {
        LocalDateTime start = firstDay();
        return app.eventUseCase.createEvent(new Event(null, "Evento " + UUID.randomUUID().toString().substring(0, 6),
                "Descrição", new Period(start, start.plusDays(1).withHour(18)), EventStatus.DRAFT,
                organizer.getId(), null, capacity, policy));
    }

    public static Activity addActivity(TestApp app, Event event, String title, String room, int startHour,
                                       int hours, int capacity, AttendancePolicy policy, boolean requiresRegistration) {
        LocalDateTime start = firstDay().withHour(startHour);
        return app.eventUseCase.addActivityToEvent(event.getId(), new Activity(null, event.getId(), title, "Descrição",
                new Period(start, start.plusHours(hours)), new ActivityLocation(room, "Trilha A", capacity),
                ActivityType.LECTURE, capacity, 0, List.of(), policy, requiresRegistration));
    }

    public static Activity addActivity(TestApp app, Event event, String title, String room, int startHour, int hours) {
        return addActivity(app, event, title, room, startHour, hours, 50, new SingleCheckInPolicy(), true);
    }

    public static Event publish(TestApp app, Event event) {
        return app.eventUseCase.publishEvent(event.getId());
    }

    public static User participant(TestApp app, String name) {
        return app.auth.register(name, TestApp.uniqueEmail("part"), TestApp.DEFAULT_PASSWORD);
    }
}
