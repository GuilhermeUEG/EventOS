package com.eventos.domain.model;

/**
 * Resposta à pergunta "este participante pode avaliar esta atividade?", sempre com o motivo
 * quando a resposta é não (RF-26, RN-13).
 */
public record EvaluationEligibility(boolean eligible, String reason) {
    public static EvaluationEligibility allowed() {
        return new EvaluationEligibility(true, null);
    }

    public static EvaluationEligibility denied(String reason) {
        return new EvaluationEligibility(false, reason);
    }
}
