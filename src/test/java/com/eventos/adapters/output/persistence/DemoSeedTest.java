package com.eventos.adapters.output.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eventos.adapters.output.persistence.DatabaseSeeder.DemoAccount;
import com.eventos.domain.model.Email;
import com.eventos.domain.model.Event;
import com.eventos.domain.model.EventStatus;
import com.eventos.domain.model.User;
import com.eventos.support.TestApp;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Base de demonstração (RNF-04): contas e roteiro dos cenários CA-01 a CA-08")
class DemoSeedTest {
    private static TestApp app;
    private static Event symposium;

    @BeforeAll
    static void seed() {
        app = TestApp.start();
        DatabaseSeeder.seedDemoData(app.users, app.events, app.activities, app.registrations, app.attendance, app.surveys);
        symposium = app.events.findAll().stream().filter(e -> e.getTitle().startsWith("XV Simpósio")).findFirst().orElseThrow();
    }

    @AfterAll
    static void stop() {
        app.close();
    }

    private User user(String email) {
        return app.users.findByEmail(new Email(email)).orElseThrow();
    }

    @Test
    @DisplayName("todas as contas documentadas existem, entram com a senha do README e têm o perfil esperado")
    void accountsMatchDocumentation() {
        assertEquals(8, DatabaseSeeder.DEMO_ACCOUNTS.size());
        for (DemoAccount account : DatabaseSeeder.DEMO_ACCOUNTS) {
            User logged = app.auth.login(account.email(), account.password());
            assertEquals(account.role(), logged.getRole(), account.email());
            assertTrue(logged.getPasswordHash().startsWith("pbkdf2$"));
        }
    }

    @Test
    @DisplayName("o evento de demonstração está publicado, com 3 atividades de políticas de frequência diferentes")
    void eventShape() {
        Event loaded = app.eventUseCase.getEvent(symposium.getId());
        assertEquals(EventStatus.PUBLISHED, loaded.getStatus());
        assertEquals(List.of("SINGLE_CHECKIN", "CHECKIN_CHECKOUT", "SINGLE_CHECKIN"),
                loaded.getActivities().stream().map(a -> a.getAttendancePolicy().getPolicyKey()).toList());
        assertTrue(loaded.getRegistrationDeadline().isAfter(loaded.getPeriod().getStart()),
                "a inscrição fica aberta durante a janela de demonstração");
        assertEquals(symposium.getOrganizerId(), user("marcio.giovane@universidade.edu.br").getId());
    }

    @Test
    @DisplayName("Guilherme está apto a avaliar e a obter o certificado; Murilo e Danielly mostram a justificativa (CA-06, CA-08)")
    void scenarioData() {
        User gui = user("guilherme.barbosa@aluno.edu.br");
        User murilo = user("murilo.mendes@aluno.edu.br");
        User dani = user("danielly.mendes@aluno.edu.br");
        Long keynote = app.eventUseCase.getEvent(symposium.getId()).getActivities().get(0).getId();

        assertTrue(app.surveyUseCase.canUserEvaluate(gui.getId(), keynote));
        assertFalse(app.surveyUseCase.checkEvaluationEligibility(murilo.getId(), keynote).eligible());
        assertTrue(app.surveyUseCase.checkEvaluationEligibility(dani.getId(), keynote).reason().contains("presença"));

        assertTrue(app.certificateUseCase.checkEligibility(symposium.getId(), gui.getId()).eligible());
        var denied = app.certificateUseCase.checkEligibility(symposium.getId(), murilo.getId());
        assertFalse(denied.eligible());
        assertTrue(denied.justification().contains("50%"), denied.justification());
    }

    @Test
    @DisplayName("semear de novo não duplica eventos nem contas")
    void idempotent() {
        long events = app.events.findAll().stream().filter(e -> e.getTitle().startsWith("XV Simpósio")).count();
        DatabaseSeeder.seedDemoData(app.users, app.events, app.activities, app.registrations, app.attendance, app.surveys);
        assertEquals(events, app.events.findAll().stream().filter(e -> e.getTitle().startsWith("XV Simpósio")).count());
    }

    @Test
    @DisplayName("primeiro administrador de um servidor sem demonstração: criado uma vez, sem sobrescrever a senha")
    void bootstrapAdministrator() {
        String email = TestApp.uniqueEmail("raiz");
        assertTrue(DatabaseSeeder.ensureAdministrator(app.users, "Admin Raiz", email, "senhaInicial1"));
        assertEquals(com.eventos.domain.model.UserRole.ADMIN, app.auth.login(email, "senhaInicial1").getRole());

        assertFalse(DatabaseSeeder.ensureAdministrator(app.users, "Outro Nome", email, "outraSenha2"), "já existia");
        assertEquals("Admin Raiz", app.auth.login(email, "senhaInicial1").getName(), "a conta existente não foi alterada");
        org.junit.jupiter.api.Assertions.assertThrows(com.eventos.domain.exceptions.UnauthorizedException.class,
                () -> app.auth.login(email, "outraSenha2"));
    }

    @Test
    @DisplayName("eventos de vitrine (feira e hackathon) existem uma única vez, de outros organizadores, e repovoar não duplica")
    void showcaseEventsAreSeededOnce() {
        DatabaseSeeder.seedDemoData(app.users, app.events, app.activities, app.registrations, app.attendance, app.surveys);
        List<Event> fairs = app.events.findAll().stream().filter(e -> e.getTitle().equals("Feira de Carreiras em Tecnologia")).toList();
        List<Event> hacks = app.events.findAll().stream().filter(e -> e.getTitle().equals("Hackathon Solidário 24h")).toList();
        assertEquals(1, fairs.size());
        assertEquals(1, hacks.size());
        assertEquals(EventStatus.PUBLISHED, fairs.get(0).getStatus());
        assertEquals(EventStatus.PUBLISHED, hacks.get(0).getStatus());
        assertEquals(user("juliana.braga@universidade.edu.br").getId(), fairs.get(0).getOrganizerId());
        assertEquals(user("joilson.brito@universidade.edu.br").getId(), hacks.get(0).getOrganizerId());
        assertEquals(2, app.eventUseCase.getEvent(fairs.get(0).getId()).getActivities().size());
        assertEquals(3, app.eventUseCase.getEvent(hacks.get(0).getId()).getActivities().size());
        assertTrue(app.eventUseCase.featuredEvents(10, java.time.LocalDateTime.now()).size() >= 3, "a página inicial tem o que mostrar");
    }

    @Test
    @DisplayName("as vagas das atividades do seed refletem as inscrições (nenhum contador fora do lugar)")
    void slotsAreConsistent() {
        for (var activity : app.activities.findByEventId(symposium.getId())) {
            long chosen = app.registrations.findByEventId(symposium.getId()).stream()
                    .filter(r -> r.getSelectedActivityIds().contains(activity.getId())).count();
            int expected = activity.isRequiresRegistration() ? (int) chosen : 0;
            assertEquals(expected, activity.getCurrentEnrollments(), activity.getTitle());
        }
    }
}
