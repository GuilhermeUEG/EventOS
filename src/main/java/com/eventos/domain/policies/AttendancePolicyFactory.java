package com.eventos.domain.policies;

/**
 * Factory das políticas de frequência: traduz a chave configurada na atividade (e persistida)
 * para a estratégia correspondente. É o único ponto que conhece as classes concretas.
 */
public class AttendancePolicyFactory {
    public static AttendancePolicy create(String key) {
        if (key == null) return new SingleCheckInPolicy();
        return switch (key.toUpperCase().trim()) {
            case CheckInCheckOutPolicy.KEY, "ENTRADA_SAIDA" -> new CheckInCheckOutPolicy();
            case ManualOnlyPolicy.KEY, "MANUAL" -> new ManualOnlyPolicy();
            default -> new SingleCheckInPolicy();
        };
    }
}
