package com.eventos.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eventos.application.commands.EventUpdate;
import com.eventos.domain.exceptions.BusinessRuleException;
import com.eventos.domain.exceptions.ConflictException;
import com.eventos.domain.model.Activity;
import com.eventos.domain.model.ActivityLocation;
import com.eventos.domain.model.ActivityType;
import com.eventos.domain.model.Event;
import com.eventos.domain.model.EventStatus;
import com.eventos.domain.model.Period;
import com.eventos.domain.model.Registration;
import com.eventos.domain.model.RegistrationStatus;
import com.eventos.domain.model.User;
import com.eventos.domain.model.UserRole;
import com.eventos.domain.policies.SingleCheckInPolicy;
import com.eventos.support.Fixtures;
import com.eventos.support.TestApp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Inscrições, vagas, agenda e conflitos (RF-11 a RF-18, RN-05 a RN-08)")
class RegistrationRulesTest {
    private static TestApp app;
    private static User organizer;

    @BeforeAll
    static void start() {
        app = TestApp.start();
        organizer = app.createUser("Organizador Inscrições", UserRole.ORGANIZER);
    }

    @AfterAll
    static void stop() {
        app.close();
    }

    private int enrollments(Activity a) {
        return app.activities.findById(a.getId()).orElseThrow().getCurrentEnrollments();
    }

    @Test
    @DisplayName("evento em rascunho não aceita inscrições")
    void draftEventIsClosed() {
        Event event = Fixtures.newEvent(app, organizer, 10);
        Fixtures.addActivity(app, event, "A", "Sala 1", 9, 1);
        User p = Fixtures.participant(app, "Visitante");
        BusinessRuleException e = assertThrows(BusinessRuleException.class,
                () -> app.registrationUseCase.registerForEvent(event.getId(), p.getId(), Set.of()));
        assertTrue(e.getMessage().contains("fechadas"));
    }

    @Test
    @DisplayName("a capacidade do evento é respeitada e a duplicidade é recusada")
    void eventCapacity() {
        Event event = Fixtures.newEvent(app, organizer, 2);
        Fixtures.addActivity(app, event, "A", "Sala 1", 9, 1);
        Fixtures.publish(app, event);
        User p1 = Fixtures.participant(app, "P1");
        User p2 = Fixtures.participant(app, "P2");
        User p3 = Fixtures.participant(app, "P3");
        app.registrationUseCase.registerForEvent(event.getId(), p1.getId(), Set.of());
        app.registrationUseCase.registerForEvent(event.getId(), p2.getId(), Set.of());
        assertThrows(BusinessRuleException.class, () -> app.registrationUseCase.registerForEvent(event.getId(), p1.getId(), Set.of()),
                "inscrição duplicada");
        BusinessRuleException full = assertThrows(BusinessRuleException.class,
                () -> app.registrationUseCase.registerForEvent(event.getId(), p3.getId(), Set.of()));
        assertTrue(full.getMessage().contains("capacidade"));
    }

    @Test
    @DisplayName("atividade lotada recusa a inscrição inteira e não deixa vaga reservada pela metade")
    void activityCapacityIsAtomic() {
        Event event = Fixtures.newEvent(app, organizer, 10);
        Activity roomy = Fixtures.addActivity(app, event, "Ampla", "Sala 1", 9, 1, 10, new SingleCheckInPolicy(), true);
        Activity tiny = Fixtures.addActivity(app, event, "Pequena", "Sala 2", 11, 1, 1, new SingleCheckInPolicy(), true);
        Fixtures.publish(app, event);
        User first = Fixtures.participant(app, "Primeiro");
        User second = Fixtures.participant(app, "Segundo");

        app.registrationUseCase.registerForEvent(event.getId(), first.getId(), Set.of(tiny.getId()));
        assertThrows(BusinessRuleException.class,
                () -> app.registrationUseCase.registerForEvent(event.getId(), second.getId(), Set.of(roomy.getId(), tiny.getId())));

        assertEquals(0, enrollments(roomy), "a vaga da atividade ampla não pode ter sido consumida");
        assertEquals(1, enrollments(tiny));
        assertTrue(app.registrations.findByEventAndUser(event.getId(), second.getId()).isEmpty(), "nenhuma inscrição parcial");
    }

    @Test
    @DisplayName("atividades com horários sobrepostos são bloqueadas na agenda, sem consumir vagas (RN-07)")
    void agendaConflict() {
        Event event = Fixtures.newEvent(app, organizer, 10);
        Activity a = Fixtures.addActivity(app, event, "A", "Sala 1", 9, 2);
        Activity b = Fixtures.addActivity(app, event, "B", "Sala 2", 10, 2);
        Activity c = Fixtures.addActivity(app, event, "C", "Sala 1", 11, 1);
        Fixtures.publish(app, event);
        User p = Fixtures.participant(app, "Agenda");

        ConflictException e = assertThrows(ConflictException.class,
                () -> app.registrationUseCase.registerForEvent(event.getId(), p.getId(), Set.of(a.getId(), b.getId())));
        assertTrue(e.getMessage().contains("Agenda"));
        assertEquals(0, enrollments(a));

        app.registrationUseCase.registerForEvent(event.getId(), p.getId(), Set.of(a.getId()));
        assertThrows(ConflictException.class, () -> app.registrationUseCase.addActivityToRegistration(event.getId(), p.getId(), b.getId()));
        app.registrationUseCase.addActivityToRegistration(event.getId(), p.getId(), c.getId());
        assertEquals(List.of("A", "C"), app.registrationUseCase.getParticipantAgenda(p.getId(), event.getId()).stream()
                .map(Activity::getTitle).toList(), "agenda em ordem cronológica");
    }

    @Test
    @DisplayName("cancelar a inscrição libera as vagas e permite reinscrição")
    void cancelReleasesSlots() {
        Event event = Fixtures.newEvent(app, organizer, 10);
        Activity a = Fixtures.addActivity(app, event, "A", "Sala 1", 9, 1, 1, new SingleCheckInPolicy(), true);
        Fixtures.publish(app, event);
        User p = Fixtures.participant(app, "Cancela");
        User other = Fixtures.participant(app, "Outro");

        app.registrationUseCase.registerForEvent(event.getId(), p.getId(), Set.of(a.getId()));
        assertEquals(1, enrollments(a));
        assertThrows(BusinessRuleException.class, () -> app.registrationUseCase.registerForEvent(event.getId(), other.getId(), Set.of(a.getId())));

        app.registrationUseCase.cancelRegistration(event.getId(), p.getId());
        assertEquals(0, enrollments(a));
        assertEquals(RegistrationStatus.CANCELLED, app.registrations.findByEventAndUser(event.getId(), p.getId()).orElseThrow().getStatus());
        assertTrue(app.registrationUseCase.getParticipantAgenda(p.getId(), event.getId()).isEmpty(), "inscrição cancelada não tem agenda");

        app.registrationUseCase.registerForEvent(event.getId(), other.getId(), Set.of(a.getId()));
        Registration again = app.registrationUseCase.registerForEvent(event.getId(), p.getId(), Set.of());
        assertTrue(again.isConfirmed());
        assertEquals(1, app.registrations.findByEventId(event.getId()).stream().filter(r -> r.getUserId().equals(p.getId())).count(),
                "reinscrição reaproveita a mesma linha");
    }

    @Test
    @DisplayName("remover atividade devolve a vaga; escolher atividade em inscrição cancelada é recusado")
    void addAndRemoveActivity() {
        Event event = Fixtures.newEvent(app, organizer, 10);
        Activity a = Fixtures.addActivity(app, event, "A", "Sala 1", 9, 1, 5, new SingleCheckInPolicy(), true);
        Fixtures.publish(app, event);
        User p = Fixtures.participant(app, "Escolhe");

        app.registrationUseCase.registerForEvent(event.getId(), p.getId(), Set.of());
        app.registrationUseCase.addActivityToRegistration(event.getId(), p.getId(), a.getId());
        assertEquals(1, enrollments(a));
        app.registrationUseCase.addActivityToRegistration(event.getId(), p.getId(), a.getId());
        assertEquals(1, enrollments(a), "escolher de novo é idempotente");
        app.registrationUseCase.removeActivityFromRegistration(event.getId(), p.getId(), a.getId());
        assertEquals(0, enrollments(a));

        app.registrationUseCase.cancelRegistration(event.getId(), p.getId());
        assertThrows(BusinessRuleException.class, () -> app.registrationUseCase.addActivityToRegistration(event.getId(), p.getId(), a.getId()));
        assertEquals(0, enrollments(a));
    }

    @Test
    @DisplayName("seleção obrigatória e prazo de inscrição são configuráveis por evento (RF-12)")
    void configurableRegistrationRules() {
        Event event = Fixtures.newEvent(app, organizer, 10);
        Activity a = Fixtures.addActivity(app, event, "A", "Sala 1", 9, 1);
        Fixtures.publish(app, event);
        User p = Fixtures.participant(app, "Config");

        app.eventUseCase.updateEvent(event.getId(), new EventUpdate(null, null, null, null, true, true, null, null));
        assertThrows(BusinessRuleException.class, () -> app.registrationUseCase.registerForEvent(event.getId(), p.getId(), Set.of()));
        app.registrationUseCase.registerForEvent(event.getId(), p.getId(), Set.of(a.getId()));
        assertThrows(BusinessRuleException.class, () -> app.registrationUseCase.removeActivityFromRegistration(event.getId(), p.getId(), a.getId()),
                "não pode ficar sem atividade quando a escolha é obrigatória");

        Event late = Fixtures.newEvent(app, organizer, 10);
        Fixtures.addActivity(app, late, "B", "Sala 1", 9, 1);
        Fixtures.publish(app, late);
        app.eventUseCase.updateEvent(late.getId(), new EventUpdate(null, null, null, null, null, null,
                LocalDateTime.now().minusDays(1), null));
        User q = Fixtures.participant(app, "Atrasado");
        BusinessRuleException closed = assertThrows(BusinessRuleException.class,
                () -> app.registrationUseCase.registerForEvent(late.getId(), q.getId(), Set.of()));
        assertTrue(closed.getMessage().contains("prazo"));
    }

    @Test
    @DisplayName("atividade de outro evento não pode entrar na inscrição")
    void activityFromAnotherEvent() {
        Event event = Fixtures.newEvent(app, organizer, 10);
        Fixtures.addActivity(app, event, "A", "Sala 1", 9, 1);
        Fixtures.publish(app, event);
        Event other = Fixtures.newEvent(app, organizer, 10);
        Activity foreign = Fixtures.addActivity(app, other, "Estrangeira", "Sala 1", 9, 1);
        User p = Fixtures.participant(app, "Confuso");
        assertThrows(BusinessRuleException.class,
                () -> app.registrationUseCase.registerForEvent(event.getId(), p.getId(), Set.of(foreign.getId())));
    }

    @Test
    @DisplayName("atividade sem controle de vagas não consome capacidade")
    void uncontrolledActivity() {
        Event event = Fixtures.newEvent(app, organizer, 10);
        Activity open = Fixtures.addActivity(app, event, "Aberta", "Sala 1", 9, 1, 1, new SingleCheckInPolicy(), false);
        Fixtures.publish(app, event);
        User p1 = Fixtures.participant(app, "Aberta 1");
        User p2 = Fixtures.participant(app, "Aberta 2");
        app.registrationUseCase.registerForEvent(event.getId(), p1.getId(), Set.of(open.getId()));
        app.registrationUseCase.registerForEvent(event.getId(), p2.getId(), Set.of(open.getId()));
        assertEquals(0, enrollments(open));
    }

    @Test
    @DisplayName("inscrições simultâneas disputando a última vaga: exatamente uma é confirmada")
    void concurrentRegistrationsNeverOversell() throws Exception {
        Event event = Fixtures.newEvent(app, organizer, 100);
        Activity lastSeat = Fixtures.addActivity(app, event, "Última vaga", "Sala 1", 9, 1, 1, new SingleCheckInPolicy(), true);
        Fixtures.publish(app, event);
        int contenders = 12;
        List<User> users = new ArrayList<>();
        for (int i = 0; i < contenders; i++) users.add(Fixtures.participant(app, "Concorrente " + i));

        ExecutorService pool = Executors.newFixedThreadPool(contenders);
        List<Future<Boolean>> results = new ArrayList<>();
        for (User u : users) {
            Callable<Boolean> task = () -> {
                try {
                    app.registrationUseCase.registerForEvent(event.getId(), u.getId(), Set.of(lastSeat.getId()));
                    return true;
                } catch (BusinessRuleException e) {
                    return false;
                }
            };
            results.add(pool.submit(task));
        }
        long confirmed = 0;
        for (Future<Boolean> f : results) if (f.get()) confirmed++;
        pool.shutdown();

        assertEquals(1, confirmed);
        assertEquals(1, enrollments(lastSeat));
        assertEquals(1, app.registrations.findByEventId(event.getId()).size());
    }

    @Test
    @DisplayName("evento cancelado some da listagem comum, mas continua acessível pela situação explícita")
    void cancelledEventLeavesTheList() {
        Event event = Fixtures.newEvent(app, organizer, 10);
        Fixtures.addActivity(app, event, "A", "Sala 1", 9, 1);
        Fixtures.publish(app, event);
        assertTrue(app.eventUseCase.filterEvents(null, null, null, null, true).stream()
                .anyMatch(e -> e.getId().equals(event.getId())), "publicado aparece para a organização");
        app.eventUseCase.cancelEvent(event.getId());
        assertFalse(app.eventUseCase.filterEvents(null, null, null, null, true).stream()
                .anyMatch(e -> e.getId().equals(event.getId())), "cancelado não aparece na listagem da organização");
        assertTrue(app.eventUseCase.filterEvents(null, null, null, EventStatus.CANCELLED, true).stream()
                .anyMatch(e -> e.getId().equals(event.getId())), "pedindo CANCELLED, ele é devolvido");
        assertEquals(EventStatus.CANCELLED, app.eventUseCase.getEvent(event.getId()).getStatus(), "o registro não é apagado");
    }

    @Test
    @DisplayName("não permite adicionar à programação de evento encerrado")
    void finishedEventKeepsProgram() {
        Event event = Fixtures.newEvent(app, organizer, 10);
        Fixtures.addActivity(app, event, "A", "Sala 1", 9, 1);
        Fixtures.publish(app, event);
        app.eventUseCase.finishEvent(event.getId());
        assertThrows(BusinessRuleException.class, () -> Fixtures.addActivity(app, event, "Tardia", "Sala 2", 11, 1));
        assertFalse(app.eventUseCase.filterEvents(null, null, null, null, false).stream()
                .anyMatch(e -> e.getId().equals(event.getId())), "evento encerrado sai da vitrine pública");
        Activity outside = new Activity(null, event.getId(), "Fora", "", new Period(Fixtures.firstDay().plusDays(30), Fixtures.firstDay().plusDays(30).plusHours(1)),
                new ActivityLocation("X", "Y", 5), ActivityType.OTHER, 5, 0, null, null, true);
        assertThrows(BusinessRuleException.class, () -> app.eventUseCase.addActivityToEvent(event.getId(), outside));
    }
}
