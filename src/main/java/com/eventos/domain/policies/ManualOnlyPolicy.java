package com.eventos.domain.policies;

import com.eventos.domain.model.AttendanceRecord;
import com.eventos.domain.model.AttendanceStatus;
import com.eventos.domain.model.AttendanceType;
import java.util.List;

public class ManualOnlyPolicy implements AttendancePolicy {
    public static final String KEY = "MANUAL_ONLY";

    @Override
    public String getPolicyKey() {
        return KEY;
    }

    @Override
    public String getPolicyName() {
        return "Validação Manual Exclusiva";
    }

    @Override
    public String getPolicyDescription() {
        return "Presença confirmada exclusivamente por lançamento manual de organizador autorizado.";
    }

    @Override
    public AttendanceStatus evaluate(List<AttendanceRecord> records) {
        if (records == null || records.isEmpty()) {
            return AttendanceStatus.ABSENT;
        }
        boolean hasManual = records.stream().anyMatch(r -> r.getType() == AttendanceType.MANUAL_ENTRY);
        return hasManual ? AttendanceStatus.PRESENT : AttendanceStatus.ABSENT;
    }
}
