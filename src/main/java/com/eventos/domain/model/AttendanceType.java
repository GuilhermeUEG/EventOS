package com.eventos.domain.model;

public enum AttendanceType {
    CHECK_IN("Entrada / Check-in"),
    CHECK_OUT("Saída / Check-out"),
    MANUAL_ENTRY("Lançamento Manual"),
    REVOCATION("Revogação de presença");

    private final String description;

    AttendanceType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
