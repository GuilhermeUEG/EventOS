package com.eventos.domain.policies;

public class MandatoryActivitiesPolicy implements CertificateEligibilityPolicy {
    public static final String KEY = "MANDATORY_COUNT";
    private final int minMandatoryCount;

    public MandatoryActivitiesPolicy(int minMandatoryCount) {
        this.minMandatoryCount = Math.max(1, minMandatoryCount);
    }

    public int getMinMandatoryCount() {
        return minMandatoryCount;
    }

    @Override
    public String getPolicyKey() {
        return KEY;
    }

    @Override
    public String getPolicyDescription() {
        return "Exige participação em no mínimo " + minMandatoryCount + " atividade(s) do evento.";
    }

    @Override
    public boolean isEligible(int totalActivitiesAttended, int totalRequiredActivities, double totalHoursAttended, double totalEventHours) {
        return totalActivitiesAttended >= minMandatoryCount;
    }
}
