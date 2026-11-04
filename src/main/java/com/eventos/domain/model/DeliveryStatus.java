package com.eventos.domain.model;

public enum DeliveryStatus {
    SENT("Enviado"),
    FAILED("Falhou");

    private final String description;

    DeliveryStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
