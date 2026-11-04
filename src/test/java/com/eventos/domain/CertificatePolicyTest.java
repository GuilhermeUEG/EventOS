package com.eventos.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eventos.domain.exceptions.ValidationException;
import com.eventos.domain.policies.CertificateEligibilityPolicy;
import com.eventos.domain.policies.CertificatePolicyFactory;
import com.eventos.domain.policies.CertificatePolicySpec;
import com.eventos.domain.policies.MinimumActivitiesPolicy;
import com.eventos.domain.policies.MinimumAttendancePercentagePolicy;
import com.eventos.domain.policies.ParticipationSummary;
import com.eventos.domain.policies.RequiredActivitiesPolicy;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Strategy de elegibilidade de certificado (RN-15, ROO-05)")
class CertificatePolicyTest {

    private ParticipationSummary summary(Set<Long> attended, int total, double hours, double totalHours) {
        return new ParticipationSummary(attended, total, hours, totalHours);
    }

    @Test
    @DisplayName("percentual mínimo: compara horas presentes com a carga horária total")
    void percentage() {
        CertificateEligibilityPolicy policy = new MinimumAttendancePercentagePolicy(75);
        assertTrue(policy.isEligible(summary(Set.of(1L, 2L, 3L), 4, 6, 8)));
        assertFalse(policy.isEligible(summary(Set.of(1L, 2L), 4, 4, 8)));
        assertTrue(policy.getPolicyDescription().contains("75%"));
    }

    @Test
    @DisplayName("percentual mínimo sem carga horária cadastrada exige ao menos uma presença")
    void percentageWithoutHours() {
        CertificateEligibilityPolicy policy = new MinimumAttendancePercentagePolicy(75);
        assertTrue(policy.isEligible(summary(Set.of(1L), 1, 0, 0)));
        assertFalse(policy.isEligible(summary(Set.of(), 1, 0, 0)));
    }

    @Test
    @DisplayName("quantidade mínima de atividades")
    void minimumCount() {
        CertificateEligibilityPolicy policy = new MinimumActivitiesPolicy(2);
        assertFalse(policy.isEligible(summary(Set.of(1L), 5, 1, 5)));
        assertTrue(policy.isEligible(summary(Set.of(1L, 2L), 5, 2, 5)));
    }

    @Test
    @DisplayName("atividades obrigatórias: precisa ter presença em todas elas")
    void requiredActivities() {
        CertificateEligibilityPolicy policy = new RequiredActivitiesPolicy(Set.of(10L, 20L));
        assertFalse(policy.isEligible(summary(Set.of(10L, 30L), 5, 2, 5)));
        assertTrue(policy.isEligible(summary(Set.of(10L, 20L, 30L), 5, 3, 5)));
        assertTrue(policy.getPolicyDescription().contains("2"));
    }

    @Test
    @DisplayName("parâmetros inválidos são recusados na criação da política")
    void invalidParameters() {
        assertThrows(ValidationException.class, () -> new MinimumAttendancePercentagePolicy(0));
        assertThrows(ValidationException.class, () -> new MinimumAttendancePercentagePolicy(101));
        assertThrows(ValidationException.class, () -> new MinimumActivitiesPolicy(0));
        assertThrows(ValidationException.class, () -> new RequiredActivitiesPolicy(Set.of()));
        assertThrows(ValidationException.class, () -> new RequiredActivitiesPolicy(null));
    }

    @Test
    @DisplayName("a fábrica reconstrói cada política a partir do seu descritor e aceita a chave legada")
    void factoryRoundTrip() {
        for (CertificateEligibilityPolicy original : new CertificateEligibilityPolicy[]{
                new MinimumAttendancePercentagePolicy(60.5), new MinimumActivitiesPolicy(3), new RequiredActivitiesPolicy(Set.of(7L, 8L))}) {
            CertificateEligibilityPolicy rebuilt = CertificatePolicyFactory.create(original.spec());
            assertEquals(original.getClass(), rebuilt.getClass());
            assertEquals(original.spec(), rebuilt.spec());
        }
        CertificateEligibilityPolicy legacy = CertificatePolicyFactory.create(new CertificatePolicySpec("MANDATORY_COUNT", null, 2, null));
        assertEquals(MinimumActivitiesPolicy.class, legacy.getClass());
    }

    @Test
    @DisplayName("tipo desconhecido ou ausente é recusado")
    void unknownType() {
        assertThrows(ValidationException.class, () -> CertificatePolicyFactory.create(new CertificatePolicySpec("XYZ", null, null, null)));
        assertThrows(ValidationException.class, () -> CertificatePolicyFactory.create(new CertificatePolicySpec(" ", null, null, null)));
        assertThrows(ValidationException.class, () -> CertificatePolicyFactory.create(null));
    }

    @Test
    @DisplayName("o resumo de participação calcula o percentual sem passar de 100%")
    void summaryPercentage() {
        assertEquals(50.0, summary(Set.of(), 2, 4, 8).getAttendedPercentage(), 0.0001);
        assertEquals(100.0, summary(Set.of(), 2, 10, 8).getAttendedPercentage(), 0.0001);
        assertEquals(0.0, summary(Set.of(), 0, 0, 0).getAttendedPercentage(), 0.0001);
    }
}
