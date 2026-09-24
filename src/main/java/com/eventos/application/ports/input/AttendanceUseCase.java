package com.eventos.application.ports.input;

import com.eventos.application.dtos.AttendanceStatusDto;
import com.eventos.domain.model.AttendanceRecord;
import com.eventos.domain.model.AttendanceStatus;
import com.eventos.domain.model.AttendanceType;
import java.util.List;

public interface AttendanceUseCase {
    String generateQrToken(Long activityId);
    AttendanceRecord recordQrAttendance(String qrToken, Long userId);
    AttendanceRecord recordManualAttendance(Long activityId, Long userId, Long organizerId, AttendanceType type, String notes);
    List<AttendanceRecord> getActivityRecords(Long activityId);
    AttendanceStatus evaluateParticipantAttendance(Long activityId, Long userId);
    List<AttendanceStatusDto> getActivityAttendanceOverview(Long activityId);
}
