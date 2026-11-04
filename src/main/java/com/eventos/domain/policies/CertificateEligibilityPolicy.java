package com.eventos.domain.policies;

import java.io.Serializable;

/**
 * Strategy para a elegibilidade de certificado por evento (RF-32, RN-15, RN-16, ROO-05).
 * Uma política nova entra como uma nova classe, sem alterar o serviço de certificados.
 */
public interface CertificateEligibilityPolicy extends Serializable {
    String getPolicyKey();

    String getPolicyDescription();

    boolean isEligible(ParticipationSummary summary);

    CertificatePolicySpec spec();
}
