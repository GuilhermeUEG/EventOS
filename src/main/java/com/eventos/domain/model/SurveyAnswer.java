package com.eventos.domain.model;

/**
 * Resposta de um participante a uma pergunta, já com o rótulo de quem respondeu
 * (anônimo ou identificado, conforme a política do questionário).
 */
public record SurveyAnswer(String respondent, String value) {
}
