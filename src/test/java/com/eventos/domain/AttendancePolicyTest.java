package com.eventos.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.eventos.domain.exceptions.ValidationException;
import com.eventos.domain.model.AttendanceRecord;
import com.eventos.domain.model.AttendanceStatus;
import com.eventos.domain.model.AttendanceType;
import com.eventos.domain.policies.AttendanceDecision;
import com.eventos.domain.policies.AttendancePolicy;
import com.eventos.domain.policies.AttendancePolicyFactory;
import com.eventos.domain.policies.CheckInCheckOutPolicy;
import com.eventos.domain.policies.ManualOnlyPolicy;
import com.eventos.domain.policies.SingleCheckInPolicy;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Strategy de frequência por atividade (RN-09, ROO-05, ROO-10)")
class AttendancePolicyTest {
    private final LocalDateTime t0 = LocalDateTime.of(2026, 10, 1, 9, 0);

    private AttendanceRecord record(long id, AttendanceType type, LocalDateTime when) {
        return new AttendanceRecord(id, 1L, 2L, when, type, AttendanceRecord.ORIGIN_QR, null);
    }

    @Test
    @DisplayName("check-in único: presente com entrada ou lançamento manual, ausente sem registros")
    void singleCheckIn() {
        AttendancePolicy policy = new SingleCheckInPolicy();
        assertEquals(AttendanceStatus.ABSENT, policy.evaluate(List.of()));
        assertEquals(AttendanceStatus.ABSENT, policy.evaluate(null));
        assertEquals(AttendanceStatus.PRESENT, policy.evaluate(List.of(record(1, AttendanceType.CHECK_IN, t0))));
        assertEquals(AttendanceStatus.PRESENT, policy.evaluate(List.of(record(1, AttendanceType.MANUAL_ENTRY, t0))));
        assertEquals(AttendanceStatus.ABSENT, policy.evaluate(List.of(record(1, AttendanceType.CHECK_OUT, t0))));
    }

    @Test
    @DisplayName("entrada e saída: parcial só com entrada, presente com as duas")
    void checkInCheckOut() {
        AttendancePolicy policy = new CheckInCheckOutPolicy();
        AttendanceRecord in = record(1, AttendanceType.CHECK_IN, t0);
        AttendanceRecord out = record(2, AttendanceType.CHECK_OUT, t0.plusHours(1));
        assertEquals(AttendanceStatus.PARTIAL, policy.evaluate(List.of(in)));
        assertEquals(AttendanceStatus.PRESENT, policy.evaluate(List.of(in, out)));
        assertEquals(AttendanceStatus.PRESENT, policy.evaluate(List.of(record(3, AttendanceType.MANUAL_ENTRY, t0))));
    }

    @Test
    @DisplayName("validação manual: QR Code não vale, só o lançamento manual")
    void manualOnly() {
        AttendancePolicy policy = new ManualOnlyPolicy();
        assertEquals(AttendanceStatus.ABSENT, policy.evaluate(List.of(record(1, AttendanceType.CHECK_IN, t0))));
        assertEquals(AttendanceStatus.PRESENT, policy.evaluate(List.of(record(2, AttendanceType.MANUAL_ENTRY, t0))));
    }

    @Test
    @DisplayName("revogação invalida os registros anteriores, e registros posteriores voltam a valer (RN-12)")
    void revocationInvalidatesPreviousRecords() {
        AttendancePolicy policy = new SingleCheckInPolicy();
        AttendanceRecord in = record(1, AttendanceType.CHECK_IN, t0);
        AttendanceRecord revoke = record(2, AttendanceType.REVOCATION, t0.plusMinutes(5));
        AttendanceRecord again = record(3, AttendanceType.MANUAL_ENTRY, t0.plusMinutes(10));

        assertEquals(AttendanceStatus.ABSENT, policy.evaluate(List.of(in, revoke)));
        assertEquals(AttendanceStatus.PRESENT, policy.evaluate(List.of(in, revoke, again)));
        assertEquals(List.of(again), AttendanceRecord.effective(List.of(again, revoke, in)), "ordem de entrada é irrelevante");
    }

    @Test
    @DisplayName("QR com check-in único: primeira leitura registra, repetição reconhece o registro existente")
    void qrDecisionSingleCheckIn() {
        AttendancePolicy policy = new SingleCheckInPolicy();
        AttendanceDecision first = policy.decideQrMark(List.of(), t0);
        assertEquals(AttendanceDecision.Kind.RECORD, first.getKind());
        assertEquals(AttendanceType.CHECK_IN, first.getTypeToRecord());

        AttendanceRecord in = record(1, AttendanceType.CHECK_IN, t0);
        AttendanceDecision second = policy.decideQrMark(List.of(in), t0.plusMinutes(3));
        assertEquals(AttendanceDecision.Kind.ALREADY_RECORDED, second.getKind());
        assertSame(in, second.getExisting());
    }

    @Test
    @DisplayName("QR com entrada e saída: leitura acidental seguida é ignorada; depois vira saída; depois é recusada")
    void qrDecisionCheckInCheckOut() {
        AttendancePolicy policy = new CheckInCheckOutPolicy();
        AttendanceRecord in = record(1, AttendanceType.CHECK_IN, t0);

        assertEquals(AttendanceType.CHECK_IN, policy.decideQrMark(List.of(), t0).getTypeToRecord());
        assertEquals(AttendanceDecision.Kind.ALREADY_RECORDED, policy.decideQrMark(List.of(in), t0.plusSeconds(5)).getKind());

        AttendanceDecision out = policy.decideQrMark(List.of(in), t0.plusMinutes(30));
        assertEquals(AttendanceDecision.Kind.RECORD, out.getKind());
        assertEquals(AttendanceType.CHECK_OUT, out.getTypeToRecord());

        AttendanceRecord checkOut = record(2, AttendanceType.CHECK_OUT, t0.plusMinutes(30));
        AttendanceDecision third = policy.decideQrMark(List.of(in, checkOut), t0.plusHours(1));
        assertEquals(AttendanceDecision.Kind.REJECTED, third.getKind());
    }

    @Test
    @DisplayName("QR não é aceito em atividade de validação manual")
    void qrRejectedWhenManualOnly() {
        AttendanceDecision decision = new ManualOnlyPolicy().decideQrMark(List.of(), t0);
        assertEquals(AttendanceDecision.Kind.REJECTED, decision.getKind());
    }

    @Test
    @DisplayName("a fábrica traduz a chave configurada para a estratégia, com padrão seguro")
    void factory() {
        assertEquals(CheckInCheckOutPolicy.class, AttendancePolicyFactory.create("checkin_checkout").getClass());
        assertEquals(ManualOnlyPolicy.class, AttendancePolicyFactory.create("MANUAL").getClass());
        assertEquals(SingleCheckInPolicy.class, AttendancePolicyFactory.create("qualquer").getClass());
        assertEquals(SingleCheckInPolicy.class, AttendancePolicyFactory.create(null).getClass());
    }

    @Test
    @DisplayName("o registro exige atividade, participante e tipo")
    void recordInvariants() {
        assertThrows(ValidationException.class, () -> new AttendanceRecord(null, null, 1L, t0, AttendanceType.CHECK_IN, "x", null));
        assertThrows(ValidationException.class, () -> new AttendanceRecord(null, 1L, null, t0, AttendanceType.CHECK_IN, "x", null));
        assertThrows(ValidationException.class, () -> new AttendanceRecord(null, 1L, 1L, t0, null, "x", null));
    }
}
