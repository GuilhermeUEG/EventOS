package com.eventos.domain.model;

public enum UserRole {
    ADMIN("Administrador"),
    ORGANIZER("Organizador"),
    PARTICIPANT("Participante");

    private final String displayName;

    UserRole(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
