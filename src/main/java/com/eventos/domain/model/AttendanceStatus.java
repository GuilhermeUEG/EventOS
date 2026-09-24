package com.eventos.domain.model;

public enum AttendanceStatus {
    PRESENT("Presente"),
    PARTIAL("Presença Parcial"),
    ABSENT("Ausente");

    private final String description;

    AttendanceStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
