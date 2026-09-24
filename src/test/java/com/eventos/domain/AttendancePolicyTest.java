package com.eventos.domain;

import com.eventos.domain.model.AttendanceRecord;
import com.eventos.domain.model.AttendanceStatus;
import com.eventos.domain.model.AttendanceType;
import com.eventos.domain.policies.CheckInCheckOutPolicy;
import com.eventos.domain.policies.ManualOnlyPolicy;
import com.eventos.domain.policies.SingleCheckInPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Testes de Domínio: Padrão Strategy para Políticas de Frequência (ROO-05, ROO-10)")
public class AttendancePolicyTest {

    @Test
    @DisplayName("SingleCheckInPolicy deve aprovar presença com 1 check-in ou manual")
    void testSingleCheckInPolicy() {
        SingleCheckInPolicy policy = new SingleCheckInPolicy();

        assertEquals(AttendanceStatus.ABSENT, policy.evaluate(List.of()));

        AttendanceRecord rec = new AttendanceRecord(1L, 10L, 20L, LocalDateTime.now(), AttendanceType.CHECK_IN, "QR", null);
        assertEquals(AttendanceStatus.PRESENT, policy.evaluate(List.of(rec)));
    }

    @Test
    @DisplayName("CheckInCheckOutPolicy deve avaliar PRESENT apenas com entrada e saída")
    void testCheckInCheckOutPolicy() {
        CheckInCheckOutPolicy policy = new CheckInCheckOutPolicy();

        AttendanceRecord in = new AttendanceRecord(1L, 10L, 20L, LocalDateTime.now().minusHours(2), AttendanceType.CHECK_IN, "QR", null);
        assertEquals(AttendanceStatus.PARTIAL, policy.evaluate(List.of(in)));

        AttendanceRecord out = new AttendanceRecord(2L, 10L, 20L, LocalDateTime.now(), AttendanceType.CHECK_OUT, "QR", null);
        assertEquals(AttendanceStatus.PRESENT, policy.evaluate(List.of(in, out)));
    }

    @Test
    @DisplayName("ManualOnlyPolicy deve exigir lançamento manual por organizador")
    void testManualOnlyPolicy() {
        ManualOnlyPolicy policy = new ManualOnlyPolicy();

        AttendanceRecord qr = new AttendanceRecord(1L, 10L, 20L, LocalDateTime.now(), AttendanceType.CHECK_IN, "QR", null);
        assertEquals(AttendanceStatus.ABSENT, policy.evaluate(List.of(qr)));

        AttendanceRecord man = new AttendanceRecord(2L, 10L, 20L, LocalDateTime.now(), AttendanceType.MANUAL_ENTRY, "ORGANIZER_1", "Conferido na lista");
        assertEquals(AttendanceStatus.PRESENT, policy.evaluate(List.of(man)));
    }
}
