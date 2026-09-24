package com.eventos.application.ports.output;

import com.eventos.domain.model.AttendanceRecord;
import java.util.List;

public interface AttendanceRepository {
    AttendanceRecord save(AttendanceRecord record);
    List<AttendanceRecord> findByActivityId(Long activityId);
    List<AttendanceRecord> findByActivityAndUser(Long activityId, Long userId);
    List<AttendanceRecord> findByUserAndEvent(Long userId, Long eventId);
}
