package com.eventos.domain.model;

public enum QuestionType {
    TEXT("Resposta Textual"),
    SINGLE_CHOICE("Escolha Única"),
    RATING_SCALE("Escala Numérica (1 a 5)");

    private final String description;

    QuestionType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
