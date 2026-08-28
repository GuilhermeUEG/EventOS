package com.eventos.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Event {
    private Long id;
    private String title;
    private String description;
    private String status; // "RASCUNHO" (DRAFT), "PUBLICADO" (PUBLISHED), "ENCERRADO" (CLOSED)
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private List<Activity> activities;

    public Event() {
        this.activities = new ArrayList<>();
        this.status = "RASCUNHO";
    }

    public Event(Long id, String title, String description, LocalDateTime startDate, LocalDateTime endDate) {
        this();
        this.id = id;
        this.title = title;
        this.description = description;
        setPeriod(startDate, endDate);
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

    public String getStatus() {
        return status;
    }

    public void publish() {
        if ("ENCERRADO".equals(this.status)) {
            throw new IllegalStateException("Um evento encerrado não pode ser publicado novamente.");
        }
        this.status = "PUBLICADO";
    }

    public void close() {
        this.status = "ENCERRADO";
    }

    public LocalDateTime getStartDate() {
        return startDate;
    }

    public LocalDateTime getEndDate() {
        return endDate;
    }

    public void setPeriod(LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Datas de início e fim são obrigatórias.");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("Data de fim deve ser após a data de início.");
        }
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public List<Activity> getActivities() {
        return new ArrayList<>(activities);
    }

    public void addActivity(Activity activity) {
        if (activity == null) {
            throw new IllegalArgumentException("Atividade inválida.");
        }
        
        // Ensure activity fits within event timeline
        if (activity.getStartTime().isBefore(this.startDate) || activity.getEndTime().isAfter(this.endDate)) {
            throw new IllegalArgumentException("O horário da atividade deve estar contido no período do evento.");
        }

        // Check for space/time conflicts with existing activities (RF-07, RN-07)
        for (Activity existing : activities) {
            if (existing.conflictsWith(activity)) {
                throw new IllegalStateException("Conflito de agendamento: o local '" + activity.getLocation() + 
                        "' já está ocupado no horário selecionado.");
            }
        }
        
        this.activities.add(activity);
    }

    public void removeActivity(Activity activity) {
        if (activity != null) {
            this.activities.removeIf(a -> a.getId() != null && a.getId().equals(activity.getId()));
        }
    }
}
