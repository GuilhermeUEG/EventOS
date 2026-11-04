package com.eventos.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eventos.domain.exceptions.BusinessRuleException;
import com.eventos.domain.exceptions.ConflictException;
import com.eventos.domain.exceptions.ValidationException;
import com.eventos.domain.model.Activity;
import com.eventos.domain.model.ActivityLocation;
import com.eventos.domain.model.ActivityType;
import com.eventos.domain.model.Event;
import com.eventos.domain.model.EventStatus;
import com.eventos.domain.model.Period;
import com.eventos.domain.policies.MinimumActivitiesPolicy;
import com.eventos.domain.policies.MinimumAttendancePercentagePolicy;
import com.eventos.domain.policies.SingleCheckInPolicy;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Agregado Event: estado, programação e regras de inscrição")
class EventTest {
    private final LocalDateTime start = LocalDateTime.of(2026, 10, 1, 8, 0);
    private Event event;

    @BeforeEach
    void setUp() {
        event = newEvent(1L);
    }

    private Event newEvent(Long id) {
        return new Event(id, "Semana de POO", "Descrição", new Period(start, start.plusDays(2)),
                EventStatus.DRAFT, 10L, null, 2, new MinimumAttendancePercentagePolicy(75));
    }

    private Activity activity(String room, int fromHour, int toHour) {
        return new Activity(null, null, "Atividade " + room + fromHour, "", new Period(start.withHour(fromHour), start.withHour(toHour)),
                new ActivityLocation(room, "Trilha", 30), ActivityType.LECTURE, 30, 0, List.of(), new SingleCheckInPolicy(), true);
    }

    @Test
    @DisplayName("não publica sem atividades, e publica com pelo menos uma")
    void publishNeedsActivities() {
        assertThrows(BusinessRuleException.class, event::publish);
        event.addActivity(activity("A", 9, 10));
        event.publish();
        assertEquals(EventStatus.PUBLISHED, event.getStatus());
        assertTrue(event.isPubliclyVisible());
    }

    @Test
    @DisplayName("evento cancelado ou encerrado não pode ser publicado nem editado")
    void terminalStates() {
        event.addActivity(activity("A", 9, 10));
        event.publish();
        event.finish();
        assertThrows(BusinessRuleException.class, event::publish);
        assertThrows(BusinessRuleException.class, () -> event.updateDetails("Novo", null, null, 0));
        assertThrows(BusinessRuleException.class, event::cancel);
        assertFalse(event.isPubliclyVisible());
    }

    @Test
    @DisplayName("rascunho e cancelado não são visíveis nem abrem inscrições (RN-04)")
    void visibilityFollowsState() {
        assertFalse(event.isPubliclyVisible());
        assertFalse(event.isEnrollmentOpen());
        event.addActivity(activity("A", 9, 10));
        event.publish();
        assertTrue(event.isEnrollmentOpen());
        event.cancel();
        assertFalse(event.isEnrollmentOpen());
    }

    @Test
    @DisplayName("recusa atividade fora do período do evento")
    void activityMustBeInsideEventPeriod() {
        Activity outside = new Activity(null, null, "Fora", "", new Period(start.minusDays(1), start.minusDays(1).plusHours(2)),
                new ActivityLocation("A", "T", 10), ActivityType.LECTURE, 10, 0, null, null, true);
        assertThrows(BusinessRuleException.class, () -> event.addActivity(outside));
    }

    @Test
    @DisplayName("recusa duas atividades na mesma sala ao mesmo tempo, mas aceita salas diferentes (RF-07)")
    void roomConflict() {
        event.addActivity(activity("A", 9, 11));
        assertThrows(ConflictException.class, () -> event.addActivity(activity("A", 10, 12)));
        assertThrows(ConflictException.class, () -> event.addActivity(activity("a", 10, 12)));
        assertDoesNotThrow(() -> event.addActivity(activity("B", 10, 12)));
        assertDoesNotThrow(() -> event.addActivity(activity("A", 11, 12)));
        assertEquals(3, event.getActivities().size());
    }

    @Test
    @DisplayName("vincula a atividade ao evento e recusa atividade de outro evento")
    void activityOwnership() {
        Activity a = activity("A", 9, 10);
        event.addActivity(a);
        assertEquals(1L, a.getEventId());
        Activity foreign = new Activity(null, 99L, "Outra", "", new Period(start.withHour(12), start.withHour(13)),
                new ActivityLocation("C", "T", 10), ActivityType.LECTURE, 10, 0, null, null, true);
        assertThrows(BusinessRuleException.class, () -> event.addActivity(foreign));
    }

    @Test
    @DisplayName("alterar o período não pode deixar atividades de fora")
    void updateKeepsActivitiesInside() {
        event.addActivity(activity("A", 9, 10));
        Period shorter = new Period(start.plusHours(5), start.plusDays(1));
        assertThrows(BusinessRuleException.class, () -> event.updateDetails(null, null, shorter, 0));
        event.updateDetails("Título novo", null, new Period(start, start.plusDays(3)), 10);
        assertEquals("Título novo", event.getTitle());
        assertEquals(10, event.getMaxCapacity());
    }

    @Test
    @DisplayName("seleção obrigatória exige seleção habilitada; fuso e prazo são validados")
    void registrationConfiguration() {
        assertThrows(ValidationException.class, () -> event.configureRegistration(false, true, start, "America/Sao_Paulo"));
        assertThrows(ValidationException.class, () -> event.configureRegistration(true, false, start, "Marte/Olimpo"));
        assertThrows(ValidationException.class, () -> event.configureRegistration(true, false, start.plusDays(5), "America/Sao_Paulo"));
        event.configureRegistration(true, true, start, "America/Manaus");
        assertEquals("America/Manaus", event.getTimeZone());
        assertEquals("America/Manaus", event.zoneId().getId());
    }

    @Test
    @DisplayName("regras de inscrição: aberta, prazo, seleção e capacidade (RN-04, RN-05, RF-13)")
    void registrationRules() {
        event.addActivity(activity("A", 9, 10));
        LocalDateTime beforeDeadline = start.minusHours(1);

        assertThrows(BusinessRuleException.class, () -> event.assertCanRegister(beforeDeadline, 0, Set.of()), "rascunho está fechado");
        event.publish();
        assertDoesNotThrow(() -> event.assertCanRegister(beforeDeadline, 0, Set.of()));

        BusinessRuleException late = assertThrows(BusinessRuleException.class,
                () -> event.assertCanRegister(start.plusMinutes(1), 0, Set.of()));
        assertTrue(late.getMessage().contains("prazo"));

        BusinessRuleException full = assertThrows(BusinessRuleException.class,
                () -> event.assertCanRegister(beforeDeadline, 2, Set.of()));
        assertTrue(full.getMessage().contains("capacidade"));

        event.configureRegistration(true, true, start, "America/Sao_Paulo");
        assertThrows(BusinessRuleException.class, () -> event.assertCanRegister(beforeDeadline, 0, Set.of()));
        assertDoesNotThrow(() -> event.assertCanRegister(beforeDeadline, 0, Set.of(1L)));

        event.configureRegistration(false, false, start, "America/Sao_Paulo");
        assertThrows(BusinessRuleException.class, () -> event.assertCanRegister(beforeDeadline, 0, Set.of(1L)));
    }

    @Test
    @DisplayName("alterar seleção e cancelar inscrição respeitam o prazo")
    void deadlineForChangesAndCancellation() {
        event.addActivity(activity("A", 9, 10));
        event.publish();
        assertDoesNotThrow(() -> event.assertCanChangeSelection(start.minusHours(2)));
        assertDoesNotThrow(() -> event.assertCanCancelRegistration(start.minusHours(2)));
        assertThrows(BusinessRuleException.class, () -> event.assertCanChangeSelection(start.plusHours(2)));
        assertThrows(BusinessRuleException.class, () -> event.assertCanCancelRegistration(start.plusHours(2)));
    }

    @Test
    @DisplayName("a política de certificado muda enquanto o evento está ativo e congela ao encerrar (RN-16)")
    void certificatePolicyFreezes() {
        event.addActivity(activity("A", 9, 10));
        event.changeCertificatePolicy(new MinimumActivitiesPolicy(1));
        assertEquals("MIN_ACTIVITIES", event.getCertificateEligibilityPolicy().getPolicyKey());
        assertThrows(ValidationException.class, () -> event.changeCertificatePolicy(null));
        event.publish();
        event.finish();
        assertThrows(BusinessRuleException.class, () -> event.changeCertificatePolicy(new MinimumAttendancePercentagePolicy(10)));
    }

    @Test
    @DisplayName("exige título e período")
    void requiredFields() {
        assertThrows(ValidationException.class, () -> new Event(null, " ", "", new Period(start, start.plusHours(1)),
                EventStatus.DRAFT, 1L, null, 10, null));
        assertThrows(ValidationException.class, () -> new Event(null, "Título", "", null, EventStatus.DRAFT, 1L, null, 10, null));
    }
}
