package com.eventos.domain.policies;

import com.eventos.domain.exceptions.ValidationException;

public class MinimumActivitiesPolicy implements CertificateEligibilityPolicy {
    public static final String KEY = "MIN_ACTIVITIES";
    /** Chave gravada por versões anteriores do sistema para esta mesma regra. */
    public static final String LEGACY_KEY = "MANDATORY_COUNT";

    private final int minActivities;

    public MinimumActivitiesPolicy(int minActivities) {
        if (minActivities < 1) {
            throw new ValidationException("A quantidade mínima de atividades deve ser de pelo menos 1.");
        }
        this.minActivities = minActivities;
    }

    public int getMinActivities() {
        return minActivities;
    }

    @Override
    public String getPolicyKey() {
        return KEY;
    }

    @Override
    public String getPolicyDescription() {
        return "Exige presença validada em no mínimo " + minActivities + " atividade(s) do evento.";
    }

    @Override
    public boolean isEligible(ParticipationSummary summary) {
        return summary.getAttendedCount() >= minActivities;
    }

    @Override
    public CertificatePolicySpec spec() {
        return new CertificatePolicySpec(KEY, null, minActivities, null);
    }
}
