package com.eventos.domain.model;

import com.eventos.domain.exceptions.ValidationException;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Registro de frequência individual e auditável (RF-21, RF-22, RN-11, RN-12).
 */
public class AttendanceRecord implements Serializable {
    private final Long id;
    private final Long activityId;
    private final Long userId;
    private final LocalDateTime timestamp;
    private final AttendanceType type;
    private final String recordedBy; // "QR_SCAN", "ADMIN_ID", "ORGANIZER_ID"
    private final String notes; // Motivo do lançamento/correção manual

    public AttendanceRecord(Long id, Long activityId, Long userId, LocalDateTime timestamp,
                            AttendanceType type, String recordedBy, String notes) {
        if (activityId == null) throw new ValidationException("ID da atividade é obrigatório para frequência.");
        if (userId == null) throw new ValidationException("ID do participante é obrigatório para frequência.");
        if (type == null) throw new ValidationException("Tipo de marcação é obrigatório.");

        this.id = id;
        this.activityId = activityId;
        this.userId = userId;
        this.timestamp = timestamp != null ? timestamp : LocalDateTime.now();
        this.type = type;
        this.recordedBy = recordedBy != null ? recordedBy : "QR_SCAN";
        this.notes = notes;
    }

    public Long getId() {
        return id;
    }

    public Long getActivityId() {
        return activityId;
    }

    public Long getUserId() {
        return userId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public AttendanceType getType() {
        return type;
    }

    public String getRecordedBy() {
        return recordedBy;
    }

    public String getNotes() {
        return notes;
    }
}
