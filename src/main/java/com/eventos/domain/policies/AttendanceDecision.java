package com.eventos.domain.policies;

import com.eventos.domain.model.AttendanceRecord;
import com.eventos.domain.model.AttendanceType;

/**
 * Resultado de uma leitura de QR Code segundo a política da atividade: registrar uma nova marcação,
 * reconhecer que ela já existe (repetição acidental) ou recusar a leitura.
 */
public final class AttendanceDecision {
    public enum Kind { RECORD, ALREADY_RECORDED, REJECTED }

    private final Kind kind;
    private final AttendanceType typeToRecord;
    private final AttendanceRecord existing;
    private final String reason;

    private AttendanceDecision(Kind kind, AttendanceType typeToRecord, AttendanceRecord existing, String reason) {
        this.kind = kind;
        this.typeToRecord = typeToRecord;
        this.existing = existing;
        this.reason = reason;
    }

    public static AttendanceDecision record(AttendanceType type) {
        return new AttendanceDecision(Kind.RECORD, type, null, null);
    }

    public static AttendanceDecision alreadyRecorded(AttendanceRecord existing) {
        return new AttendanceDecision(Kind.ALREADY_RECORDED, null, existing, null);
    }

    public static AttendanceDecision rejected(String reason) {
        return new AttendanceDecision(Kind.REJECTED, null, null, reason);
    }

    public Kind getKind() {
        return kind;
    }

    public AttendanceType getTypeToRecord() {
        return typeToRecord;
    }

    public AttendanceRecord getExisting() {
        return existing;
    }

    public String getReason() {
        return reason;
    }
}
