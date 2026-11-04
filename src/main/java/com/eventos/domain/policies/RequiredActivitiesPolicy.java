package com.eventos.domain.policies;

import com.eventos.domain.exceptions.ValidationException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Elegível quem teve presença validada em todas as atividades obrigatórias do evento.
 */
public class RequiredActivitiesPolicy implements CertificateEligibilityPolicy {
    public static final String KEY = "REQUIRED_ACTIVITIES";

    private final Set<Long> requiredActivityIds;

    public RequiredActivitiesPolicy(Set<Long> requiredActivityIds) {
        if (requiredActivityIds == null || requiredActivityIds.isEmpty()) {
            throw new ValidationException("Informe ao menos uma atividade obrigatória para o certificado.");
        }
        this.requiredActivityIds = Collections.unmodifiableSet(new HashSet<>(requiredActivityIds));
    }

    public Set<Long> getRequiredActivityIds() {
        return requiredActivityIds;
    }

    @Override
    public String getPolicyKey() {
        return KEY;
    }

    @Override
    public String getPolicyDescription() {
        return "Exige presença validada em todas as " + requiredActivityIds.size() + " atividade(s) obrigatória(s).";
    }

    @Override
    public boolean isEligible(ParticipationSummary summary) {
        return summary.getAttendedActivityIds().containsAll(requiredActivityIds);
    }

    @Override
    public CertificatePolicySpec spec() {
        return new CertificatePolicySpec(KEY, null, null, requiredActivityIds);
    }
}
