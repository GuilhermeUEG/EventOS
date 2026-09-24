package com.eventos.application.dtos;

import java.io.Serializable;
import java.util.List;

public class AttendanceReportDto implements Serializable {
    private Long eventId;
    private String eventTitle;
    private int totalParticipants;
    private double overallAttendanceRate;
    private List<ActivityAttendanceSummaryDto> activitySummaries;

    public AttendanceReportDto() {}

    public AttendanceReportDto(Long eventId, String eventTitle, int totalParticipants, double overallAttendanceRate, List<ActivityAttendanceSummaryDto> activitySummaries) {
        this.eventId = eventId;
        this.eventTitle = eventTitle;
        this.totalParticipants = totalParticipants;
        this.overallAttendanceRate = overallAttendanceRate;
        this.activitySummaries = activitySummaries;
    }

    public Long getEventId() { return eventId; }
    public String getEventTitle() { return eventTitle; }
    public int getTotalParticipants() { return totalParticipants; }
    public double getOverallAttendanceRate() { return overallAttendanceRate; }
    public List<ActivityAttendanceSummaryDto> getActivitySummaries() { return activitySummaries; }

    public static class ActivityAttendanceSummaryDto implements Serializable {
        private Long activityId;
        private String activityTitle;
        private String room;
        private int totalPresent;
        private int totalPartial;
        private int totalAbsent;

        public ActivityAttendanceSummaryDto() {}

        public ActivityAttendanceSummaryDto(Long activityId, String activityTitle, String room, int totalPresent, int totalPartial, int totalAbsent) {
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
}
