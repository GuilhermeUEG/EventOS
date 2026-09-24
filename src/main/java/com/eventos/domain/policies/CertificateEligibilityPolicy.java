package com.eventos.domain.policies;

import java.io.Serializable;

/**
 * Strategy Pattern para cálculo de elegibilidade para certificado por evento (RF-32, RN-16, ROO-05).
 */
public interface CertificateEligibilityPolicy extends Serializable {
    String getPolicyKey();
    String getPolicyDescription();
    boolean isEligible(int totalActivitiesAttended, int totalRequiredActivities, double totalHoursAttended, double totalEventHours);
}
