package com.eventos.domain.model;

import com.eventos.domain.exceptions.BusinessRuleException;
import com.eventos.domain.exceptions.ConflictException;
import com.eventos.domain.exceptions.ValidationException;
import com.eventos.domain.policies.CertificateEligibilityPolicy;
import com.eventos.domain.policies.MinimumAttendancePercentagePolicy;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Agregado Raiz de Evento com invariantes de estado, publicação e conflito (ROO-01, ROO-02, RF-04, RN-04).
 */
public class Event implements Serializable {
    private final Long id;
    private String title;
    private String description;
    private Period period;
    private EventStatus status;
    private Long organizerId;
    private final List<Activity> activities;
    private int maxCapacity;
    private CertificateEligibilityPolicy certificateEligibilityPolicy;

    public Event(Long id, String title, String description, Period period,
                 EventStatus status, Long organizerId, List<Activity> activities,
                 int maxCapacity, CertificateEligibilityPolicy certificateEligibilityPolicy) {
        if (title == null || title.trim().isEmpty()) {
            throw new ValidationException("Título do evento é obrigatório.");
        }
        if (period == null) {
            throw new ValidationException("Período do evento é obrigatório.");
        }
        this.id = id;
        this.title = title.trim();
        this.description = description != null ? description.trim() : "";
        this.period = period;
        this.status = status != null ? status : EventStatus.DRAFT;
        this.organizerId = organizerId;
        this.activities = activities != null ? new ArrayList<>(activities) : new ArrayList<>();
        this.maxCapacity = maxCapacity > 0 ? maxCapacity : 500;
        this.certificateEligibilityPolicy = certificateEligibilityPolicy != null
                ? certificateEligibilityPolicy
                : new MinimumAttendancePercentagePolicy(75.0);
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Period getPeriod() {
        return period;
    }

    public EventStatus getStatus() {
        return status;
    }

    public Long getOrganizerId() {
        return organizerId;
    }

    public List<Activity> getActivities() {
        return Collections.unmodifiableList(activities);
    }

    public int getMaxCapacity() {
        return maxCapacity;
    }

    public CertificateEligibilityPolicy getCertificateEligibilityPolicy() {
        return certificateEligibilityPolicy;
    }

    public void setCertificateEligibilityPolicy(CertificateEligibilityPolicy policy) {
        if (policy != null) {
            this.certificateEligibilityPolicy = policy;
        }
    }

    public void updateDetails(String newTitle, String newDescription, Period newPeriod, int newCapacity) {
        if (this.status == EventStatus.FINISHED || this.status == EventStatus.CANCELLED) {
            throw new BusinessRuleException("Não é permitido editar evento encerrado ou cancelado.");
        }
        if (newTitle != null && !newTitle.trim().isEmpty()) this.title = newTitle.trim();
        if (newDescription != null) this.description = newDescription.trim();
        if (newPeriod != null) this.period = newPeriod;
        if (newCapacity > 0) this.maxCapacity = newCapacity;
    }

    public void publish() {
        if (this.status == EventStatus.CANCELLED) {
            throw new BusinessRuleException("Um evento cancelado não pode ser publicado.");
        }
        if (this.activities.isEmpty()) {
            throw new BusinessRuleException("Para publicar um evento, cadastre ao menos uma atividade na programação.");
        }
        this.status = EventStatus.PUBLISHED;
    }

    public void cancel() {
        if (this.status == EventStatus.FINISHED) {
            throw new BusinessRuleException("Evento já encerrado não pode ser cancelado.");
        }
        this.status = EventStatus.CANCELLED;
    }

    public void finish() {
        this.status = EventStatus.FINISHED;
    }

    public boolean isEnrollmentOpen() {
        return this.status == EventStatus.PUBLISHED || this.status == EventStatus.IN_PROGRESS;
    }

    public void addActivity(Activity activity) {
        if (activity == null) throw new ValidationException("Atividade não pode ser nula.");
        
        // Invariante: Atividade deve estar contida no período do evento
        if (!activity.getPeriod().isWithin(this.period)) {
            throw new BusinessRuleException("O horário da atividade (" + activity.getPeriod() + ") deve estar dentro do período do evento (" + this.period + ").");
        }

        // Invariante: Não permitir conflito de sala no mesmo evento
        for (Activity existing : this.activities) {
            if (activity.conflictsWith(existing)) {
                throw new ConflictException("Conflito de agendamento detectado! A sala '" + activity.getLocation().getRoom() +
                        "' já está ocupada pela atividade '" + existing.getTitle() + "' no mesmo horário.");
            }
        }

        if (this.id != null) {
            activity.setEventId(this.id);
        }
        this.activities.add(activity);
    }

    public double calculateTotalHours() {
        return this.activities.stream()
                .mapToDouble(a -> a.getPeriod().getDurationHours())
                .sum();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Event event = (Event) o;
        return Objects.equals(id, event.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
