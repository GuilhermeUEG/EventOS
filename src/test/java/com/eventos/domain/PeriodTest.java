package com.eventos.domain;

import com.eventos.domain.exceptions.ValidationException;
import com.eventos.domain.model.Period;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes de Domínio: Objeto de Valor Period")
public class PeriodTest {

    @Test
    @DisplayName("Deve criar período válido quando start < end")
    void shouldCreateValidPeriod() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 25, 9, 0);
        LocalDateTime end = LocalDateTime.of(2026, 9, 25, 12, 0);
        Period period = new Period(start, end);

        assertEquals(start, period.getStart());
        assertEquals(end, period.getEnd());
        assertEquals(3.0, period.getDurationHours());
    }

    @Test
    @DisplayName("Deve rejeitar quando start for posterior a end")
    void shouldRejectInvalidStartEnd() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 25, 14, 0);
        LocalDateTime end = LocalDateTime.of(2026, 9, 25, 10, 0);

        assertThrows(ValidationException.class, () -> new Period(start, end));
    }

    @Test
    @DisplayName("Deve detectar sobreposição de horários corretamente")
    void shouldDetectOverlaps() {
        Period p1 = new Period(LocalDateTime.of(2026, 9, 25, 9, 0), LocalDateTime.of(2026, 9, 25, 11, 0));
        Period p2 = new Period(LocalDateTime.of(2026, 9, 25, 10, 0), LocalDateTime.of(2026, 9, 25, 12, 0));
        Period p3 = new Period(LocalDateTime.of(2026, 9, 25, 11, 0), LocalDateTime.of(2026, 9, 25, 13, 0));

        assertTrue(p1.overlaps(p2), "p1 e p2 se sobrepõem");
        assertFalse(p1.overlaps(p3), "p1 e p3 apenas se tocam no limite, não sobrepõem");
    }
}
