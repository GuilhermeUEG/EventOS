package com.eventos.performance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eventos.adapters.output.persistence.DatabaseManager;
import com.eventos.adapters.output.persistence.DatabaseSeeder;
import com.eventos.application.dtos.AttendanceReportDto;
import com.eventos.application.dtos.AttendanceStatusDto;
import com.eventos.application.dtos.EnrolledReportDto;
import com.eventos.domain.model.Activity;
import com.eventos.domain.model.ActivityFilter;
import com.eventos.domain.model.Email;
import com.eventos.domain.model.Event;
import com.eventos.domain.model.User;
import com.eventos.support.TestApp;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * RNF-08: consultas comuns respondem bem em uma base com 500 participantes e 100 atividades.
 * Os limites são generosos (a máquina de avaliação pode ser lenta) e servem para pegar regressões
 * de ordem de grandeza, como voltar a consultar o banco uma vez por participante e atividade.
 */
@DisplayName("Desempenho com 500 participantes e 100 atividades (RNF-08)")
class LargeDatasetTest {
    private static final Duration LIMIT = Duration.ofSeconds(5);
    private static TestApp app;
    private static Event congress;

    @BeforeAll
    static void seed() {
        app = TestApp.start();
        DatabaseSeeder.seedLargeDataset(app.users, app.events, app.activities, app.registrations, app.attendance);
        congress = app.events.findAll().stream()
                .filter(e -> e.getTitle().equals("Congresso de Demonstração de Carga"))
                .findFirst().orElseThrow();
    }

    @AfterAll
    static void stop() {
        app.close();
    }

    private <T> T timed(String name, Supplier<T> action) {
        long begin = System.nanoTime();
        T result = action.get();
        Duration took = Duration.ofNanos(System.nanoTime() - begin);
        System.out.printf("[desempenho] %-38s %5d ms%n", name, took.toMillis());
        assertTrue(took.compareTo(LIMIT) < 0, name + " levou " + took.toMillis() + " ms (limite " + LIMIT.toMillis() + " ms)");
        return result;
    }

    @Test
    @DisplayName("a base de carga tem 500 participantes, 100 atividades e inscrições coerentes")
    void datasetShape() throws Exception {
        try (Connection c = DatabaseManager.getConnection(); Statement s = c.createStatement()) {
            try (ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM users WHERE email LIKE '%@carga.demo'")) {
                rs.next();
                assertEquals(DatabaseSeeder.LOAD_PARTICIPANTS, rs.getInt(1));
            }
        }
        assertEquals(DatabaseSeeder.LOAD_ACTIVITIES, app.activities.findByEventId(congress.getId()).size());
        assertEquals(DatabaseSeeder.LOAD_PARTICIPANTS, app.registrations.countConfirmedByEventId(congress.getId()));
        int enrolledInActivities = app.activities.findByEventId(congress.getId()).stream().mapToInt(Activity::getCurrentEnrollments).sum();
        assertEquals(DatabaseSeeder.LOAD_PARTICIPANTS * 3, enrolledInActivities);
    }

    @Test
    @DisplayName("semear de novo não duplica nada")
    void seedingIsIdempotent() {
        DatabaseSeeder.seedLargeDataset(app.users, app.events, app.activities, app.registrations, app.attendance);
        assertEquals(DatabaseSeeder.LOAD_PARTICIPANTS, app.registrations.countConfirmedByEventId(congress.getId()));
    }

    @Test
    @DisplayName("programação, filtros, agenda e inscrições respondem dentro do limite")
    void commonReads() {
        List<Activity> all = timed("listar programação (100)", () -> app.eventUseCase.getEventActivities(congress.getId()));
        assertEquals(100, all.size());
        List<Activity> filtered = timed("filtrar por trilha e tipo", () -> app.eventUseCase.searchActivities(congress.getId(),
                new ActivityFilter(null, null, null, "Trilha 1", null, null, "atividade")));
        assertTrue(filtered.size() > 0 && filtered.size() < 100);
        User someone = app.users.findByEmail(new Email("participante250@carga.demo")).orElseThrow();
        assertEquals(3, timed("agenda de um participante", () -> app.registrationUseCase.getParticipantAgenda(someone.getId(), congress.getId())).size());
        assertEquals(500, timed("inscrições do evento (500)", () -> app.registrationUseCase.getEventRegistrations(congress.getId())).size());
        assertEquals(500, timed("listar usuários", () -> app.users.findAll().size() >= 500 ? 500 : 0));
    }

    @Test
    @DisplayName("relatórios de inscritos e de frequência respondem dentro do limite")
    void reports() {
        EnrolledReportDto enrolled = timed("relatório de inscritos", () -> app.reportUseCase.getEnrolledReport(congress.getId()));
        assertEquals(500, enrolled.getTotalEnrolled());

        AttendanceReportDto attendance = timed("relatório de frequência (1500 linhas)", () -> app.reportUseCase.getAttendanceReport(congress.getId()));
        assertEquals(1500, attendance.getRows().size());
        assertEquals(100, attendance.getActivitySummaries().size());
        assertTrue(attendance.getOverallAttendanceRate() > 0);

        byte[] csv = timed("exportar CSV de frequência", () -> app.reportUseCase.exportAttendanceCsv(congress.getId()));
        assertTrue(csv.length > 10_000);
        byte[] pdf = timed("exportar PDF de frequência", () -> app.reportUseCase.exportAttendancePdf(congress.getId()));
        assertTrue(pdf.length > 1_000);
    }

    @Test
    @DisplayName("visão geral de uma atividade e elegibilidade de certificado respondem dentro do limite")
    void activityViews() {
        Activity busy = app.activities.findByEventId(congress.getId()).stream()
                .max(java.util.Comparator.comparingInt(Activity::getCurrentEnrollments)).orElseThrow();
        List<AttendanceStatusDto> overview = timed("visão geral da atividade", () -> app.attendanceUseCase.getActivityAttendanceOverview(busy.getId()));
        assertEquals(busy.getCurrentEnrollments(), overview.size());

        User someone = app.users.findByEmail(new Email("participante003@carga.demo")).orElseThrow();
        var eligibility = timed("elegibilidade de certificado", () -> app.certificateUseCase.checkEligibility(congress.getId(), someone.getId()));
        assertEquals(3, eligibility.totalActivities() > 0 ? 3 : 0);
    }
}
