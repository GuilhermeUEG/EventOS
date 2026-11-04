package com.eventos.application.dtos;

/**
 * Situação de elegibilidade de um participante ao certificado, com a justificativa (CA-08).
 */
public record CertificateEligibilityDto(
        boolean eligible,
        String justification,
        String policyDescription,
        int activitiesAttended,
        int totalActivities,
        double hoursAttended,
        double totalHours,
        boolean alreadyIssued
) {
}
