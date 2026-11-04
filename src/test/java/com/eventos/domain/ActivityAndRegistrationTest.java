package com.eventos.domain;

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
import com.eventos.domain.model.AttendanceRecord;
import com.eventos.domain.model.AttendanceStatus;
import com.eventos.domain.model.AttendanceType;
import com.eventos.domain.model.Period;
import com.eventos.domain.model.Registration;
import com.eventos.domain.model.RegistrationStatus;
import com.eventos.domain.model.Speaker;
import com.eventos.domain.policies.SingleCheckInPolicy;
import com.eventos.domain.services.ConflictValidator;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Activity e Registration: vagas, conflitos e acesso à atividade")
class ActivityAndRegistrationTest {
    private final LocalDateTime t0 = LocalDateTime.of(2026, 10, 1, 9, 0);

    private Activity activity(Long id, String room, int fromHour, int hours, int capacity, boolean controlsSlots) {
        return new Activity(id, 1L, "Atividade " + id, "", new Period(t0.withHour(fromHour), t0.withHour(fromHour).plusHours(hours)),
                new ActivityLocation(room, "Trilha", capacity), ActivityType.WORKSHOP, capacity, 0, List.of(),
                new SingleCheckInPolicy(), controlsSlots);
    }

    @Test
    @DisplayName("controla vagas até a capacidade e libera ao cancelar (RN-06)")
    void slotsRespectCapacity() {
        Activity a = activity(1L, "A", 9, 1, 2, true);
        a.bookSlot();
        a.bookSlot();
        assertFalse(a.hasAvailableSlots());
        assertEquals(0, a.getAvailableSlots());
        BusinessRuleException full = assertThrows(BusinessRuleException.class, a::bookSlot);
        assertTrue(full.getMessage().contains("capacidade"));
        a.releaseSlot();
        assertEquals(1, a.getAvailableSlots());
        assertEquals(1, a.getCurrentEnrollments());
    }

    @Test
    @DisplayName("atividade sem controle de vagas nunca lota")
    void unlimitedWhenNotControlled() {
        Activity a = activity(1L, "A", 9, 1, 1, false);
        a.bookSlot();
        a.bookSlot();
        assertTrue(a.hasAvailableSlots());
    }

    @Test
    @DisplayName("não libera mais vagas do que as reservadas")
    void releaseNeverGoesNegative() {
        Activity a = activity(1L, "A", 9, 1, 5, true);
        a.releaseSlot();
        assertEquals(0, a.getCurrentEnrollments());
    }

    @Test
    @DisplayName("a lista de palestrantes é protegida contra alteração externa")
    void speakersAreEncapsulated() {
        Activity a = activity(1L, "A", 9, 1, 5, true);
        a.addSpeaker(new Speaker(null, "Ana", "Palestrante", "", null));
        assertEquals(1, a.getSpeakers().size());
        assertThrows(UnsupportedOperationException.class, () -> a.getSpeakers().add(new Speaker(null, "Bia", null, null, null)));
        assertThrows(ValidationException.class, () -> a.addSpeaker(null));
    }

    @Test
    @DisplayName("exige título, período e local")
    void requiredFields() {
        Period p = new Period(t0, t0.plusHours(1));
        ActivityLocation l = new ActivityLocation("A", "T", 10);
        assertThrows(ValidationException.class, () -> new Activity(null, 1L, " ", "", p, l, null, 10, 0, null, null, true));
        assertThrows(ValidationException.class, () -> new Activity(null, 1L, "X", "", null, l, null, 10, 0, null, null, true));
        assertThrows(ValidationException.class, () -> new Activity(null, 1L, "X", "", p, null, null, 10, 0, null, null, true));
        assertThrows(ValidationException.class, () -> new ActivityLocation(" ", "T", 10));
        assertThrows(ValidationException.class, () -> new ActivityLocation("A", "T", 0));
    }

    @Test
    @DisplayName("conflito de sala é por sala e horário; conflito de agenda é só por horário")
    void conflicts() {
        Activity a = activity(1L, "A", 9, 2, 10, true);
        Activity sameRoomOverlap = activity(2L, "A", 10, 2, 10, true);
        Activity otherRoomOverlap = activity(3L, "B", 10, 2, 10, true);
        Activity sameRoomAfter = activity(4L, "A", 11, 1, 10, true);

        assertTrue(a.conflictsWith(sameRoomOverlap));
        assertFalse(a.conflictsWith(otherRoomOverlap));
        assertFalse(a.conflictsWith(sameRoomAfter));
        assertTrue(a.overlapsInTimeWith(otherRoomOverlap));
        assertFalse(a.overlapsInTimeWith(sameRoomAfter));
        assertFalse(a.conflictsWith(a), "uma atividade não conflita consigo mesma");

        assertThrows(ConflictException.class, () -> ConflictValidator.validateActivityRoomConflict(sameRoomOverlap, List.of(a)));
        assertThrows(ConflictException.class, () -> ConflictValidator.validateParticipantAgendaConflict(otherRoomOverlap, List.of(a)));
        ConflictValidator.validateParticipantAgendaConflict(sameRoomAfter, List.of(a));
    }

    @Test
    @DisplayName("a política de frequência é definida na criação e a situação ignora registros revogados")
    void attendanceEvaluationUsesEffectiveRecords() {
        Activity a = activity(1L, "A", 9, 1, 10, true);
        AttendanceRecord in = new AttendanceRecord(1L, 1L, 7L, t0, AttendanceType.CHECK_IN, AttendanceRecord.ORIGIN_QR, null);
        AttendanceRecord revoke = new AttendanceRecord(2L, 1L, 7L, t0.plusMinutes(1), AttendanceType.REVOCATION,
                AttendanceRecord.manualOrigin(9L), "Marcação indevida");
        assertEquals(AttendanceStatus.PRESENT, a.evaluateAttendance(List.of(in)));
        assertEquals(AttendanceStatus.ABSENT, a.evaluateAttendance(List.of(in, revoke)));
        assertTrue(revoke.isManual());
        assertFalse(in.isManual());
    }

    @Test
    @DisplayName("a atividade ocorre nas datas do seu período")
    void occursOn() {
        Activity a = activity(1L, "A", 9, 1, 10, true);
        assertTrue(a.occursOn(LocalDate.of(2026, 10, 1)));
        assertFalse(a.occursOn(LocalDate.of(2026, 10, 2)));
    }

    @Test
    @DisplayName("inscrição cancelada não aceita escolhas nem alterações e só cancela uma vez")
    void cancelledRegistrationIsFrozen() {
        Registration r = new Registration(1L, 1L, 7L, Set.of(5L), t0, RegistrationStatus.CONFIRMED);
        r.selectActivity(6L);
        assertEquals(Set.of(5L, 6L), r.getSelectedActivityIds());
        r.cancel();
        assertThrows(BusinessRuleException.class, r::cancel);
        assertThrows(BusinessRuleException.class, () -> r.selectActivity(8L));
        assertThrows(BusinessRuleException.class, () -> r.removeActivity(5L));
        assertThrows(UnsupportedOperationException.class, () -> r.getSelectedActivityIds().add(1L));
    }

    @Test
    @DisplayName("reinscrição só é possível depois do cancelamento e mantém a identidade da inscrição")
    void reconfirm() {
        Registration confirmed = new Registration(10L, 1L, 7L, Set.of(), t0, RegistrationStatus.CONFIRMED);
        assertThrows(BusinessRuleException.class, () -> confirmed.reconfirm(Set.of(), t0));
        confirmed.cancel();
        Registration again = confirmed.reconfirm(Set.of(5L), t0.plusDays(1));
        assertTrue(again.isConfirmed());
        assertEquals(10L, again.getId());
        assertEquals(Set.of(5L), again.getSelectedActivityIds());
    }

    @Test
    @DisplayName("acesso à atividade: inscrição confirmada e, se a atividade controla inscrição, escolha dela (RN-08, RN-13)")
    void grantsAccess() {
        Activity controlled = activity(5L, "A", 9, 1, 10, true);
        Activity open = activity(6L, "B", 11, 1, 10, false);
        Registration reg = new Registration(1L, 1L, 7L, Set.of(5L), t0, RegistrationStatus.CONFIRMED);
        assertTrue(reg.grantsAccessTo(controlled));
        assertTrue(reg.grantsAccessTo(open));
        assertFalse(new Registration(2L, 1L, 7L, Set.of(), t0, RegistrationStatus.CONFIRMED).grantsAccessTo(controlled));
        assertFalse(new Registration(3L, 99L, 7L, Set.of(5L), t0, RegistrationStatus.CONFIRMED).grantsAccessTo(controlled),
                "inscrição de outro evento não vale");
        reg.cancel();
        assertFalse(reg.grantsAccessTo(controlled));
    }
}
