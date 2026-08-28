package com.eventos.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

public class EventTest {

    private Event event;
    private LocalDateTime eventStart;
    private LocalDateTime eventEnd;

    @BeforeEach
    public void setUp() {
        eventStart = LocalDateTime.of(2026, 9, 1, 9, 0);
        eventEnd = LocalDateTime.of(2026, 9, 5, 18, 0);
        event = new Event(1L, "Congresso de POO II", "Simpósio de teste", eventStart, eventEnd);
    }

    @Test
    public void testAddActivitySuccessfully() {
        Activity activity = new Activity(1L, "Palestra de SOLID", "Princípios SOLID em Java", "PALESTRA",
                eventStart.plusHours(2), eventStart.plusHours(4), "Auditório A", 100);

        event.addActivity(activity);
        
        assertEquals(1, event.getActivities().size());
        assertEquals("Palestra de SOLID", event.getActivities().get(0).getTitle());
    }

    @Test
    public void testAddActivityOutsideEventTimelineShouldThrow() {
        // Activity starts before the event starts
        Activity earlyActivity = new Activity(1L, "Acredenciamento", "Credenciamento de participantes", "ORGANIZACAO",
                eventStart.minusHours(2), eventStart.plusHours(1), "Foyer", 500);

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            event.addActivity(earlyActivity);
        });

        assertTrue(exception.getMessage().contains("período do evento"));
    }

    @Test
    public void testAddConflictingActivityLocationShouldThrow() {
        // First activity in Auditorium A
        Activity act1 = new Activity(1L, "Palestra de SOLID", "Princípios SOLID em Java", "PALESTRA",
                eventStart.plusHours(2), eventStart.plusHours(4), "Auditório A", 100);
        event.addActivity(act1);

        // Second activity in Auditorium A at the same time
        Activity act2 = new Activity(2L, "Workshop de Kotlin", "Hands-on Kotlin", "WORKSHOP",
                eventStart.plusHours(3), eventStart.plusHours(5), "Auditório A", 50);

        Exception exception = assertThrows(IllegalStateException.class, () -> {
            event.addActivity(act2);
        });

        assertTrue(exception.getMessage().contains("Conflito de agendamento"));
    }

    @Test
    public void testAddNonConflictingActivityDifferentLocationSuccessfully() {
        // First activity in Auditorium A
        Activity act1 = new Activity(1L, "Palestra de SOLID", "Princípios SOLID em Java", "PALESTRA",
                eventStart.plusHours(2), eventStart.plusHours(4), "Auditório A", 100);
        event.addActivity(act1);

        // Second activity at the same time but in Auditorium B
        Activity act2 = new Activity(2L, "Workshop de Kotlin", "Hands-on Kotlin", "WORKSHOP",
                eventStart.plusHours(2), eventStart.plusHours(4), "Auditório B", 50);
        
        assertDoesNotThrow(() -> {
            event.addActivity(act2);
        });
        
        assertEquals(2, event.getActivities().size());
    }
}
