package com.eventos.domain.policies;

import com.eventos.domain.model.AttendanceRecord;
import com.eventos.domain.model.AttendanceStatus;
import com.eventos.domain.model.AttendanceType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public class SingleCheckInPolicy implements AttendancePolicy {
    public static final String KEY = "SINGLE_CHECKIN";

    @Override
    public String getPolicyKey() {
        return KEY;
    }

    @Override
    public String getPolicyName() {
        return "Check-in Único";
    }

    @Override
    public String getPolicyDescription() {
        return "Exige apenas 1 registro de check-in via QR Code ou validação manual.";
    }

    @Override
    public AttendanceStatus evaluate(List<AttendanceRecord> records) {
        return findPresence(records).isPresent() ? AttendanceStatus.PRESENT : AttendanceStatus.ABSENT;
    }

    @Override
    public AttendanceDecision decideQrMark(List<AttendanceRecord> records, LocalDateTime now) {
        return findPresence(records)
                .map(AttendanceDecision::alreadyRecorded)
                .orElseGet(() -> AttendanceDecision.record(AttendanceType.CHECK_IN));
    }

    private Optional<AttendanceRecord> findPresence(List<AttendanceRecord> records) {
        return AttendanceRecord.effective(records).stream()
                .filter(r -> r.getType() == AttendanceType.CHECK_IN || r.getType() == AttendanceType.MANUAL_ENTRY)
                .findFirst();
    }
}
