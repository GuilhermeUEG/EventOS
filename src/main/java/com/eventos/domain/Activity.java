package com.eventos.domain;

import java.time.LocalDateTime;

public class Activity {
    private Long id;
    private String title;
    private String description;
    private String type; // "PALESTRA", "WORKSHOP", "OFICINA", "POSTER" etc.
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String location;
    private int capacity;
    private int enrolledCount;

    public Activity() {}

    public Activity(Long id, String title, String description, String type, LocalDateTime startTime, LocalDateTime endTime, String location, int capacity) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.type = type;
        setSchedule(startTime, endTime);
        this.location = location;
        setCapacity(capacity);
        this.enrolledCount = 0;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setSchedule(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("Horários de início e fim são obrigatórios.");
        }
        if (endTime.isBefore(startTime)) {
            throw new IllegalArgumentException("Horário de fim deve ser posterior ao início.");
        }
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("A capacidade deve ser maior que zero.");
        }
        this.capacity = capacity;
    }

    public int getEnrolledCount() {
        return enrolledCount;
    }

    public void setEnrolledCount(int enrolledCount) {
        this.enrolledCount = enrolledCount;
    }

    public boolean hasVacancies() {
        return enrolledCount < capacity;
    }

    public void enroll() {
        if (!hasVacancies()) {
            throw new IllegalStateException("Esta atividade não possui vagas disponíveis.");
        }
        enrolledCount++;
    }

    public void cancelEnrollment() {
        if (enrolledCount > 0) {
            enrolledCount--;
        }
    }

    // Detect time and location conflicts (RF-07, RN-07)
    public boolean conflictsWith(Activity other) {
        if (other == null) return false;
        
        // Check if there is a time overlap
        boolean timeOverlap = this.startTime.isBefore(other.endTime) && other.startTime.isBefore(this.endTime);
        
        // If there is a time overlap and they share the same location, there's a conflict
        if (timeOverlap && this.location != null && this.location.equalsIgnoreCase(other.location)) {
            return true;
        }
        
        return false;
    }

    // Detect general time overlaps (regardless of location) for a participant's personal agenda (RF-18)
    public boolean overlapsInTime(Activity other) {
        if (other == null) return false;
        return this.startTime.isBefore(other.endTime) && other.startTime.isBefore(this.endTime);
    }
}
