package com.eventos.domain.model;

import com.eventos.domain.exceptions.BusinessRuleException;
import com.eventos.domain.exceptions.ValidationException;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Inscrição de um participante em um evento e suas atividades escolhidas (RF-12, RF-15, RF-16, RN-05).
 */
public class Registration implements Serializable {
    private final Long id;
    private final Long eventId;
    private final Long userId;
    private final Set<Long> selectedActivityIds;
    private final LocalDateTime registrationDate;
    private RegistrationStatus status;

    public Registration(Long id, Long eventId, Long userId, Set<Long> selectedActivityIds,
                        LocalDateTime registrationDate, RegistrationStatus status) {
        if (eventId == null) throw new ValidationException("ID do evento é obrigatório para inscrição.");
        if (userId == null) throw new ValidationException("ID do usuário é obrigatório para inscrição.");

        this.id = id;
        this.eventId = eventId;
        this.userId = userId;
        this.selectedActivityIds = selectedActivityIds != null ? new HashSet<>(selectedActivityIds) : new HashSet<>();
        this.registrationDate = registrationDate != null ? registrationDate : LocalDateTime.now();
        this.status = status != null ? status : RegistrationStatus.CONFIRMED;
    }

    public Long getId() {
        return id;
    }

    public Long getEventId() {
        return eventId;
    }

    public Long getUserId() {
        return userId;
    }

    public Set<Long> getSelectedActivityIds() {
        return Collections.unmodifiableSet(selectedActivityIds);
    }

    public LocalDateTime getRegistrationDate() {
        return registrationDate;
    }

    public RegistrationStatus getStatus() {
        return status;
    }

    public void selectActivity(Long activityId) {
        if (this.status == RegistrationStatus.CANCELLED) {
            throw new BusinessRuleException("Inscrição cancelada não pode adicionar atividades.");
        }
        if (activityId != null) {
            this.selectedActivityIds.add(activityId);
        }
    }

    public void removeActivity(Long activityId) {
        if (activityId != null) {
            this.selectedActivityIds.remove(activityId);
        }
    }

    public void cancel() {
        if (this.status == RegistrationStatus.CANCELLED) {
            throw new BusinessRuleException("A inscrição já está cancelada.");
        }
        this.status = RegistrationStatus.CANCELLED;
    }

    public boolean isConfirmed() {
        return this.status == RegistrationStatus.CONFIRMED;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Registration that = (Registration) o;
        return Objects.equals(eventId, that.eventId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, userId);
    }
}
