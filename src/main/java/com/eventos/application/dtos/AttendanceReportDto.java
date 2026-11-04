package com.eventos.application.dtos;

import com.eventos.domain.model.AttendanceStatus;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Relatório de frequência e participação (RF-30): totais por atividade e a situação calculada
 * de cada participante em cada atividade em que está inscrito.
 */
public class AttendanceReportDto implements Serializable {
    private Long eventId;
    private String eventTitle;
    private int totalParticipants;
    private double overallAttendanceRate;
    private List<ActivityAttendanceSummaryDto> activitySummaries;
    private List<ParticipantAttendanceRowDto> rows;

    public AttendanceReportDto() {}

    public AttendanceReportDto(Long eventId, String eventTitle, int totalParticipants, double overallAttendanceRate,
                               List<ActivityAttendanceSummaryDto> activitySummaries,
                               List<ParticipantAttendanceRowDto> rows) {
        this.eventId = eventId;
        this.eventTitle = eventTitle;
        this.totalParticipants = totalParticipants;
        this.overallAttendanceRate = overallAttendanceRate;
        this.activitySummaries = activitySummaries;
        this.rows = rows;
    }

    public Long getEventId() { return eventId; }
    public String getEventTitle() { return eventTitle; }
    public int getTotalParticipants() { return totalParticipants; }
    public double getOverallAttendanceRate() { return overallAttendanceRate; }
    public List<ActivityAttendanceSummaryDto> getActivitySummaries() { return activitySummaries; }
    public List<ParticipantAttendanceRowDto> getRows() { return rows; }

    public static class ActivityAttendanceSummaryDto implements Serializable {
        private Long activityId;
        private String activityTitle;
        private String room;
        private int totalPresent;
        private int totalPartial;
        private int totalAbsent;

        public ActivityAttendanceSummaryDto() {}

        public ActivityAttendanceSummaryDto(Long activityId, String activityTitle, String room,
                                            int totalPresent, int totalPartial, int totalAbsent) {
            this.activityId = activityId;
            this.activityTitle = activityTitle;
            this.room = room;
            this.totalPresent = totalPresent;
            this.totalPartial = totalPartial;
            this.totalAbsent = totalAbsent;
        }

        public Long getActivityId() { return activityId; }
        public String getActivityTitle() { return activityTitle; }
        public String getRoom() { return room; }
        public int getTotalPresent() { return totalPresent; }
        public int getTotalPartial() { return totalPartial; }
        public int getTotalAbsent() { return totalAbsent; }
    }

    public static class ParticipantAttendanceRowDto implements Serializable {
        private Long userId;
        private String userName;
        private String userEmail;
        private Long activityId;
        private String activityTitle;
        private AttendanceStatus status;
        private int recordsCount;
        private LocalDateTime lastRecordTime;
        private String lastRecordedBy;

        public ParticipantAttendanceRowDto() {}

        public ParticipantAttendanceRowDto(Long userId, String userName, String userEmail, Long activityId,
                                           String activityTitle, AttendanceStatus status, int recordsCount,
                                           LocalDateTime lastRecordTime, String lastRecordedBy) {
            this.userId = userId;
            this.userName = userName;
            this.userEmail = userEmail;
            this.activityId = activityId;
            this.activityTitle = activityTitle;
            this.status = status;
            this.recordsCount = recordsCount;
            this.lastRecordTime = lastRecordTime;
            this.lastRecordedBy = lastRecordedBy;
        }

        public Long getUserId() { return userId; }
        public String getUserName() { return userName; }
        public String getUserEmail() { return userEmail; }
        public Long getActivityId() { return activityId; }
        public String getActivityTitle() { return activityTitle; }
        public AttendanceStatus getStatus() { return status; }
        public int getRecordsCount() { return recordsCount; }
        public LocalDateTime getLastRecordTime() { return lastRecordTime; }
        public String getLastRecordedBy() { return lastRecordedBy; }
    }
}
