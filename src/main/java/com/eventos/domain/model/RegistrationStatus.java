package com.eventos.domain.model;

public enum RegistrationStatus {
    CONFIRMED("Confirmada"),
    WAITLIST("Lista de Espera"),
    CANCELLED("Cancelada");

    private final String description;

    RegistrationStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
