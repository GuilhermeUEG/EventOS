package com.eventos.domain.policies;

import com.eventos.domain.model.AttendanceRecord;
import com.eventos.domain.model.AttendanceStatus;
import com.eventos.domain.model.AttendanceType;
import java.util.List;

public class CheckInCheckOutPolicy implements AttendancePolicy {
    public static final String KEY = "CHECKIN_CHECKOUT";

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
        if (records == null || records.isEmpty()) {
            return AttendanceStatus.ABSENT;
        }
        boolean hasManual = records.stream().anyMatch(r -> r.getType() == AttendanceType.MANUAL_ENTRY);
        if (hasManual) {
            return AttendanceStatus.PRESENT;
        }

        boolean hasIn = records.stream().anyMatch(r -> r.getType() == AttendanceType.CHECK_IN);
        boolean hasOut = records.stream().anyMatch(r -> r.getType() == AttendanceType.CHECK_OUT);

        if (hasIn && hasOut) {
            return AttendanceStatus.PRESENT;
        } else if (hasIn || hasOut) {
            return AttendanceStatus.PARTIAL;
        }
        return AttendanceStatus.ABSENT;
    }
}
