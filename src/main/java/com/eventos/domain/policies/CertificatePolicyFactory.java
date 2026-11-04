package com.eventos.domain.policies;

import com.eventos.domain.exceptions.ValidationException;

/**
 * Factory das políticas de certificado. Traduz o tipo escolhido pelo organizador (ou lido do banco)
 * para a estratégia correspondente e rejeita tipos desconhecidos.
 */
public class CertificatePolicyFactory {
    public static final double DEFAULT_PERCENTAGE = 75.0;

    public static CertificateEligibilityPolicy defaultPolicy() {
        return new MinimumAttendancePercentagePolicy(DEFAULT_PERCENTAGE);
    }

    public static CertificateEligibilityPolicy create(CertificatePolicySpec spec) {
        if (spec == null || spec.type() == null || spec.type().isBlank()) {
            throw new ValidationException("Tipo da política de certificado é obrigatório.");
        }
        return switch (spec.type().trim().toUpperCase()) {
            case MinimumAttendancePercentagePolicy.KEY -> new MinimumAttendancePercentagePolicy(
                    spec.percentage() != null ? spec.percentage() : DEFAULT_PERCENTAGE);
            case MinimumActivitiesPolicy.KEY, MinimumActivitiesPolicy.LEGACY_KEY -> new MinimumActivitiesPolicy(
                    spec.minActivities() != null ? spec.minActivities() : 1);
            case RequiredActivitiesPolicy.KEY -> new RequiredActivitiesPolicy(spec.activityIds());
            default -> throw new ValidationException("Política de certificado desconhecida: " + spec.type());
        };
    }
}
