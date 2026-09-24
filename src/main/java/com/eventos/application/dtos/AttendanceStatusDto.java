package com.eventos.application.dtos;

import com.eventos.domain.model.AttendanceStatus;
import java.io.Serializable;
import java.time.LocalDateTime;

public class AttendanceStatusDto implements Serializable {
    private Long userId;
    private String userName;
    private String userEmail;
    private Long activityId;
    private String activityTitle;
    private AttendanceStatus status;
    private LocalDateTime lastRecordTime;
    private String recordedBy;

    public AttendanceStatusDto() {}

    public AttendanceStatusDto(Long userId, String userName, String userEmail,
                               Long activityId, String activityTitle, AttendanceStatus status,
                               LocalDateTime lastRecordTime, String recordedBy) {
        this.userId = userId;
        this.userName = userName;
        this.userEmail = userEmail;
        this.activityId = activityId;
        this.activityTitle = activityTitle;
        this.status = status;
        this.lastRecordTime = lastRecordTime;
        this.recordedBy = recordedBy;
    }

    public Long getUserId() { return userId; }
    public String getUserName() { return userName; }
    public String getUserEmail() { return userEmail; }
    public Long getActivityId() { return activityId; }
    public String getActivityTitle() { return activityTitle; }
    public AttendanceStatus getStatus() { return status; }
    public LocalDateTime getLastRecordTime() { return lastRecordTime; }
    public String getRecordedBy() { return recordedBy; }
}
