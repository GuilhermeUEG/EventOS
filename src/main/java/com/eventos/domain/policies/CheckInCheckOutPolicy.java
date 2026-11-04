package com.eventos.domain.policies;

import com.eventos.domain.model.AttendanceRecord;
import com.eventos.domain.model.AttendanceStatus;
import com.eventos.domain.model.AttendanceType;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

public class CheckInCheckOutPolicy implements AttendancePolicy {
    public static final String KEY = "CHECKIN_CHECKOUT";
    /** Leituras repetidas dentro deste intervalo são tratadas como repetição acidental da entrada. */
    public static final Duration ACCIDENTAL_REPEAT_WINDOW = Duration.ofSeconds(30);

    @Override
    public String getPolicyKey() {
        return KEY;
    }

    @Override
    public String getPolicyName() {
        return "Entrada e Saída";
    }

    @Override
    public String getPolicyDescription() {
        return "Exige marcação de entrada (Check-in) e marcação de saída (Check-out).";
    }

    @Override
    public AttendanceStatus evaluate(List<AttendanceRecord> records) {
        List<AttendanceRecord> effective = AttendanceRecord.effective(records);
        if (effective.isEmpty()) {
            return AttendanceStatus.ABSENT;
        }
        if (last(effective, AttendanceType.MANUAL_ENTRY) != null) {
            return AttendanceStatus.PRESENT;
        }
        boolean hasIn = last(effective, AttendanceType.CHECK_IN) != null;
        boolean hasOut = last(effective, AttendanceType.CHECK_OUT) != null;
        if (hasIn && hasOut) {
            return AttendanceStatus.PRESENT;
        }
        return (hasIn || hasOut) ? AttendanceStatus.PARTIAL : AttendanceStatus.ABSENT;
    }

    @Override
    public AttendanceDecision decideQrMark(List<AttendanceRecord> records, LocalDateTime now) {
        List<AttendanceRecord> effective = AttendanceRecord.effective(records);
        AttendanceRecord manual = last(effective, AttendanceType.MANUAL_ENTRY);
        if (manual != null) {
            return AttendanceDecision.alreadyRecorded(manual);
        }
        AttendanceRecord in = last(effective, AttendanceType.CHECK_IN);
        AttendanceRecord out = last(effective, AttendanceType.CHECK_OUT);
        if (in == null) {
            return AttendanceDecision.record(AttendanceType.CHECK_IN);
        }
        if (out != null) {
            return AttendanceDecision.rejected("A entrada e a saída já foram registradas para esta atividade.");
        }
        if (Duration.between(in.getTimestamp(), now).compareTo(ACCIDENTAL_REPEAT_WINDOW) < 0) {
            return AttendanceDecision.alreadyRecorded(in);
        }
        return AttendanceDecision.record(AttendanceType.CHECK_OUT);
    }

    private AttendanceRecord last(List<AttendanceRecord> records, AttendanceType type) {
        AttendanceRecord found = null;
        for (AttendanceRecord r : records) {
            if (r.getType() == type) found = r;
        }
        return found;
    }
}
