package com.eventos.domain.policies;

public class MinimumAttendancePercentagePolicy implements CertificateEligibilityPolicy {
    public static final String KEY = "MIN_PERCENTAGE";
    private final double minPercentage; // e.g. 75.0

    public MinimumAttendancePercentagePolicy(double minPercentage) {
        this.minPercentage = minPercentage > 0 ? minPercentage : 75.0;
    }

    public double getMinPercentage() {
        return minPercentage;
    }

    @Override
    public String getPolicyKey() {
        return KEY;
    }

    @Override
    public String getPolicyDescription() {
        return "Exige no mínimo " + minPercentage + "% de presença na carga horária total do evento.";
    }

    @Override
    public boolean isEligible(int totalActivitiesAttended, int totalRequiredActivities, double totalHoursAttended, double totalEventHours) {
        if (totalEventHours <= 0) {
            return totalActivitiesAttended > 0;
        }
        double percentage = (totalHoursAttended / totalEventHours) * 100.0;
        return percentage >= minPercentage;
    }
}
