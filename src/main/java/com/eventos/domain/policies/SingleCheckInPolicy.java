package com.eventos.domain.policies;

import com.eventos.domain.model.AttendanceRecord;
import com.eventos.domain.model.AttendanceStatus;
import com.eventos.domain.model.AttendanceType;
import java.util.List;

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
        if (records == null || records.isEmpty()) {
            return AttendanceStatus.ABSENT;
        }
        boolean hasCheckInOrManual = records.stream()
                .anyMatch(r -> r.getType() == AttendanceType.CHECK_IN || r.getType() == AttendanceType.MANUAL_ENTRY);
        return hasCheckInOrManual ? AttendanceStatus.PRESENT : AttendanceStatus.ABSENT;
    }
}
