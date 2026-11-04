package com.eventos.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.eventos.domain.model.Activity;
import com.eventos.domain.model.ActivityFilter;
import com.eventos.domain.model.ActivityLocation;
import com.eventos.domain.model.ActivityType;
import com.eventos.domain.model.Period;
import com.eventos.domain.model.Speaker;
import com.eventos.domain.policies.SingleCheckInPolicy;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Filtros combináveis da programação (RF-09)")
class ActivityFilterTest {
    private final LocalDateTime day1 = LocalDateTime.of(2026, 10, 1, 8, 0);
    private final LocalDateTime day2 = day1.plusDays(1);

    private Activity activity(long id, String title, LocalDateTime start, String room, String track, ActivityType type, String speaker) {
        return new Activity(id, 1L, title, "", new Period(start, start.plusHours(1)), new ActivityLocation(room, track, 30),
                type, 30, 0, List.of(new Speaker(null, speaker, "Palestrante", "", null)), new SingleCheckInPolicy(), true);
    }

    private final List<Activity> all = List.of(
            activity(1, "Arquitetura Hexagonal", day1.withHour(9), "Auditório", "Arquitetura", ActivityType.LECTURE, "Ana Souza"),
            activity(2, "Oficina de Strategy", day1.withHour(14), "Lab 1", "Desenvolvimento", ActivityType.WORKSHOP, "Bruno Lima"),
            activity(3, "Pôsteres", day2.withHour(10), "Hall", "Arquitetura", ActivityType.POSTER, "Carla Dias"));

    private List<Long> ids(ActivityFilter filter) {
        return filter.apply(all).stream().map(Activity::getId).toList();
    }

    @Test
    @DisplayName("sem critérios devolve tudo em ordem cronológica")
    void noCriteria() {
        assertEquals(List.of(1L, 2L, 3L), ids(ActivityFilter.none()));
    }

    @Test
    @DisplayName("filtra por data, por faixa de horário de início, por trilha, tipo e sala")
    void singleCriteria() {
        assertEquals(List.of(1L, 2L), ids(new ActivityFilter(LocalDate.of(2026, 10, 1), null, null, null, null, null, null)));
        assertEquals(List.of(2L), ids(new ActivityFilter(null, LocalTime.of(12, 0), LocalTime.of(18, 0), null, null, null, null)));
        assertEquals(List.of(1L, 3L), ids(new ActivityFilter(null, null, null, "arquitetura", null, null, null)));
        assertEquals(List.of(3L), ids(new ActivityFilter(null, null, null, null, ActivityType.POSTER, null, null)));
        assertEquals(List.of(2L), ids(new ActivityFilter(null, null, null, null, null, "LAB 1", null)));
    }

    @Test
    @DisplayName("os critérios se combinam (E lógico)")
    void combined() {
        assertEquals(List.of(1L), ids(new ActivityFilter(LocalDate.of(2026, 10, 1), null, null, "Arquitetura", null, null, null)));
        assertEquals(List.of(), ids(new ActivityFilter(LocalDate.of(2026, 10, 2), null, null, "Desenvolvimento", null, null, null)));
    }

    @Test
    @DisplayName("a busca livre cobre título, sala, trilha e palestrantes")
    void freeText() {
        assertEquals(List.of(2L), ids(new ActivityFilter(null, null, null, null, null, null, "strategy")));
        assertEquals(List.of(3L), ids(new ActivityFilter(null, null, null, null, null, null, "hall")));
        assertEquals(List.of(1L), ids(new ActivityFilter(null, null, null, null, null, null, "ana souza")));
        assertEquals(List.of(1L, 3L), ids(new ActivityFilter(null, null, null, null, null, null, "arquitetura")));
    }

    @Test
    @DisplayName("critérios em branco não restringem")
    void blankCriteriaAreIgnored() {
        assertEquals(List.of(1L, 2L, 3L), ids(new ActivityFilter(null, null, null, " ", null, "", "  ")));
    }
}
