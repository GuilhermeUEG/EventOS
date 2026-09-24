package com.eventos.domain;

import com.eventos.domain.exceptions.BusinessRuleException;
import com.eventos.domain.model.Activity;
import com.eventos.domain.model.ActivityLocation;
import com.eventos.domain.model.ActivityType;
import com.eventos.domain.model.Period;
import com.eventos.domain.model.Speaker;
import com.eventos.domain.policies.SingleCheckInPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes de Domínio: Entidade Activity")
public class ActivityTest {

    @Test
    @DisplayName("Deve reservar vaga e lançar erro ao exceder capacidade")
    void shouldBookSlotAndRespectCapacity() {
        Period period = new Period(LocalDateTime.of(2026, 9, 25, 9, 0), LocalDateTime.of(2026, 9, 25, 11, 0));
        ActivityLocation location = new ActivityLocation("Lab 1", "TI", 2);
        Activity activity = new Activity(1L, 10L, "Workshop Java", "Desc", period, location,
                ActivityType.WORKSHOP, 2, 0, List.of(), new SingleCheckInPolicy(), true);

        assertTrue(activity.hasAvailableSlots());
        assertEquals(2, activity.getAvailableSlots());

        activity.bookSlot();
        assertEquals(1, activity.getAvailableSlots());

        activity.bookSlot();
        assertEquals(0, activity.getAvailableSlots());
        assertFalse(activity.hasAvailableSlots());

        // Terceira reserva deve falhar
        assertThrows(BusinessRuleException.class, activity::bookSlot);

        // Ao liberar vaga, volta a ficar disponível
        activity.releaseSlot();
        assertTrue(activity.hasAvailableSlots());
        assertEquals(1, activity.getAvailableSlots());
    }

    @Test
    @DisplayName("Deve adicionar palestrante corretamente")
    void shouldAddSpeaker() {
        Period period = new Period(LocalDateTime.of(2026, 9, 25, 9, 0), LocalDateTime.of(2026, 9, 25, 11, 0));
        ActivityLocation location = new ActivityLocation("Auditório", "Geral", 100);
        Activity activity = new Activity(1L, 10L, "Palestra", "Desc", period, location,
                ActivityType.LECTURE, 100, 0, null, new SingleCheckInPolicy(), false);

        Speaker speaker = new Speaker(null, "Prof. Roberto", "Keynote Speaker", "Bio", null);
        activity.addSpeaker(speaker);

        assertEquals(1, activity.getSpeakers().size());
        assertEquals("Prof. Roberto", activity.getSpeakers().get(0).getName());
    }
}
