package com.eventos.domain.model;

public enum EventStatus {
    DRAFT("Rascunho"),
    PUBLISHED("Publicado"),
    IN_PROGRESS("Em Andamento"),
    FINISHED("Encerrado"),
    CANCELLED("Cancelado");

    private final String description;

    EventStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
