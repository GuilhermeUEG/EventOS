package com.eventos.application.dtos;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

public class EnrolledReportDto implements Serializable {
    private Long eventId;
    private String eventTitle;
    private int totalEnrolled;
    private int eventCapacity;
    private List<EnrolledParticipantDto> participants;

    public EnrolledReportDto() {}

    public EnrolledReportDto(Long eventId, String eventTitle, int totalEnrolled, int eventCapacity, List<EnrolledParticipantDto> participants) {
        this.eventId = eventId;
        this.eventTitle = eventTitle;
        this.totalEnrolled = totalEnrolled;
        this.eventCapacity = eventCapacity;
        this.participants = participants;
    }

    public Long getEventId() { return eventId; }
    public String getEventTitle() { return eventTitle; }
    public int getTotalEnrolled() { return totalEnrolled; }
    public int getEventCapacity() { return eventCapacity; }
    public List<EnrolledParticipantDto> getParticipants() { return participants; }

    public static class EnrolledParticipantDto implements Serializable {
        private Long userId;
        private String name;
        private String email;
        private String status;
        private LocalDateTime registrationDate;
        private int selectedActivitiesCount;

        public EnrolledParticipantDto() {}

        public EnrolledParticipantDto(Long userId, String name, String email, String status, LocalDateTime registrationDate, int selectedActivitiesCount) {
            this.userId = userId;
            this.name = name;
            this.email = email;
            this.status = status;
            this.registrationDate = registrationDate;
            this.selectedActivitiesCount = selectedActivitiesCount;
        }

        public Long getUserId() { return userId; }
        public String getName() { return name; }
        public String getEmail() { return email; }
        public String getStatus() { return status; }
        public LocalDateTime getRegistrationDate() { return registrationDate; }
        public int getSelectedActivitiesCount() { return selectedActivitiesCount; }
    }
}
