package com.eventos.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eventos.domain.exceptions.ValidationException;
import com.eventos.domain.model.Period;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Objeto de valor Period (ROO-03)")
class PeriodTest {
    private final LocalDateTime base = LocalDateTime.of(2026, 10, 1, 8, 0);

    @Test
    @DisplayName("calcula a duração em horas")
    void durationInHours() {
        assertEquals(1.5, new Period(base, base.plusMinutes(90)).getDurationHours(), 0.0001);
    }

    @Test
    @DisplayName("rejeita início posterior ao fim e datas ausentes")
    void rejectsInvalidPeriods() {
        assertThrows(ValidationException.class, () -> new Period(base.plusHours(1), base));
        assertThrows(ValidationException.class, () -> new Period(null, base));
        assertThrows(ValidationException.class, () -> new Period(base, null));
    }

    @Test
    @DisplayName("detecta sobreposição, mas períodos consecutivos não se sobrepõem")
    void overlap() {
        Period morning = new Period(base, base.plusHours(2));
        assertTrue(morning.overlaps(new Period(base.plusHours(1), base.plusHours(3))));
        assertFalse(morning.overlaps(new Period(base.plusHours(2), base.plusHours(4))));
        assertFalse(morning.overlaps(null));
    }

    @Test
    @DisplayName("verifica se está contido em outro período")
    void within() {
        Period day = new Period(base, base.plusHours(10));
        assertTrue(new Period(base.plusHours(1), base.plusHours(2)).isWithin(day));
        assertFalse(new Period(base.minusHours(1), base.plusHours(2)).isWithin(day));
    }

    @Test
    @DisplayName("é um valor: igualdade por início e fim")
    void valueEquality() {
        assertEquals(new Period(base, base.plusHours(1)), new Period(base, base.plusHours(1)));
    }
}
