package com.eventos.domain.model;

import com.eventos.domain.exceptions.ValidationException;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Registro de frequência individual, imutável e auditável (RF-21, RF-22, RN-11, RN-12).
 *
 * <p>Registros nunca são alterados nem apagados. Uma correção é um novo registro do tipo
 * {@link AttendanceType#REVOCATION}: ela invalida os registros anteriores do mesmo participante
 * na mesma atividade, mas todos permanecem no histórico com autor, horário e justificativa.</p>
 */
public class AttendanceRecord implements Serializable {
    public static final String ORIGIN_QR = "QR_SCAN";
    public static final String ORIGIN_MANUAL_PREFIX = "MANUAL_BY_USER_";

    private final Long id;
    private final Long activityId;
    private final Long userId;
    private final LocalDateTime timestamp;
    private final AttendanceType type;
    private final String recordedBy; // origem: QR_SCAN ou MANUAL_BY_USER_<id do responsável>
    private final String notes; // motivo do lançamento/correção manual

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
        this.recordedBy = recordedBy != null ? recordedBy : ORIGIN_QR;
        this.notes = notes;
    }

    public static String manualOrigin(Long organizerId) {
        return ORIGIN_MANUAL_PREFIX + organizerId;
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

    public boolean isRevocation() {
        return type == AttendanceType.REVOCATION;
    }

    public boolean isManual() {
        return recordedBy != null && recordedBy.startsWith(ORIGIN_MANUAL_PREFIX);
    }

    /**
     * Registros que ainda valem: tudo o que veio depois da última revogação, sem as revogações.
     * A ordem é cronológica (empate resolvido pelo identificador de gravação).
     */
    public static List<AttendanceRecord> effective(List<AttendanceRecord> records) {
        if (records == null || records.isEmpty()) return new ArrayList<>();
        List<AttendanceRecord> ordered = new ArrayList<>(records);
        ordered.sort(Comparator.comparing(AttendanceRecord::getTimestamp)
                .thenComparing(r -> r.getId() == null ? Long.MAX_VALUE : r.getId()));
        int lastRevocation = -1;
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).isRevocation()) lastRevocation = i;
        }
        List<AttendanceRecord> result = new ArrayList<>();
        for (int i = lastRevocation + 1; i < ordered.size(); i++) {
            result.add(ordered.get(i));
        }
        return result;
    }
}
