package com.eventos.domain.policies;

import java.util.Collections;
import java.util.Set;

/**
 * Descrição neutra de uma política de certificado (tipo + parâmetros). Permite persistir e expor
 * a política sem que adaptadores precisem conhecer as classes concretas.
 */
public record CertificatePolicySpec(String type, Double percentage, Integer minActivities, Set<Long> activityIds) {
    public CertificatePolicySpec {
        activityIds = activityIds == null ? Collections.emptySet() : Collections.unmodifiableSet(activityIds);
    }
}
