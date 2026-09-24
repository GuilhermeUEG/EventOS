package com.eventos.domain.model;

public enum ActivityType {
    LECTURE("Palestra"),
    WORKSHOP("Oficina / Mini-curso"),
    ORAL_PRESENTATION("Apresentação Oral"),
    POSTER("Sessão de Pôster"),
    ROUND_TABLE("Mesa Redonda"),
    OTHER("Outro");

    private final String description;

    ActivityType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
