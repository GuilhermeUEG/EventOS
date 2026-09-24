package com.eventos.domain.policies;

import com.eventos.domain.model.AttendanceRecord;
import com.eventos.domain.model.AttendanceStatus;
import java.io.Serializable;
import java.util.List;

/**
 * Strategy Pattern para cálculo de frequência configurável por atividade (RF-19, RF-23, ROO-05, ROO-10).
 */
public interface AttendancePolicy extends Serializable {
    String getPolicyKey();
    String getPolicyName();
    String getPolicyDescription();
    AttendanceStatus evaluate(List<AttendanceRecord> records);
}
