package com.eventos.domain.policies;

import com.eventos.domain.exceptions.ValidationException;

public class MinimumAttendancePercentagePolicy implements CertificateEligibilityPolicy {
    public static final String KEY = "MIN_PERCENTAGE";

    private final double minPercentage;

    public MinimumAttendancePercentagePolicy(double minPercentage) {
        if (minPercentage <= 0 || minPercentage > 100) {
            throw new ValidationException("O percentual mínimo de presença deve estar entre 0 (exclusivo) e 100.");
        }
        this.minPercentage = minPercentage;
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
        return "Exige no mínimo " + format(minPercentage) + "% de presença na carga horária total do evento.";
    }

    @Override
    public boolean isEligible(ParticipationSummary summary) {
        if (summary.getTotalHours() <= 0) {
            return summary.getAttendedCount() > 0;
        }
        return summary.getAttendedPercentage() >= minPercentage;
    }

    @Override
    public CertificatePolicySpec spec() {
        return new CertificatePolicySpec(KEY, minPercentage, null, null);
    }

    private static String format(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
