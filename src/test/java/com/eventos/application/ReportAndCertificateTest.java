package com.eventos.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eventos.application.dtos.AttendanceReportDto;
import com.eventos.application.dtos.CertificateEligibilityDto;
import com.eventos.application.dtos.EnrolledReportDto;
import com.eventos.domain.exceptions.BusinessRuleException;
import com.eventos.domain.exceptions.ValidationException;
import com.eventos.domain.model.Activity;
import com.eventos.domain.model.AttendanceType;
import com.eventos.domain.model.Certificate;
import com.eventos.domain.model.Event;
import com.eventos.domain.model.User;
import com.eventos.domain.model.UserRole;
import com.eventos.domain.policies.MinimumAttendancePercentagePolicy;
import com.eventos.domain.policies.RequiredActivitiesPolicy;
import com.eventos.domain.policies.SingleCheckInPolicy;
import com.eventos.support.Fixtures;
import com.eventos.support.TestApp;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Relatórios (RF-29 a RF-31) e certificados (RF-32, RF-33, RN-15, RN-16)")
class ReportAndCertificateTest {
    private static TestApp app;
    private static User organizer;

    @BeforeAll
    static void start() {
        app = TestApp.start();
        organizer = app.createUser("Organizador Relatórios", UserRole.ORGANIZER);
    }

    @AfterAll
    static void stop() {
        app.close();
    }

    private void present(Activity a, User u) {
        app.attendanceUseCase.recordManualAttendance(a.getId(), u.getId(), organizer.getId(), AttendanceType.MANUAL_ENTRY, "Conferido");
    }

    private String csv(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("relatório de inscritos separa confirmadas e canceladas e exporta CSV com BOM")
    void enrolledReport() {
        Event event = Fixtures.newEvent(app, organizer, 20);
        Activity a = Fixtures.addActivity(app, event, "A", "Sala 1", 9, 1);
        Fixtures.publish(app, event);
        User kept = Fixtures.participant(app, "Mantida");
        User cancelled = Fixtures.participant(app, "Cancelada");
        app.registrationUseCase.registerForEvent(event.getId(), kept.getId(), Set.of(a.getId()));
        app.registrationUseCase.registerForEvent(event.getId(), cancelled.getId(), Set.of());
        app.registrationUseCase.cancelRegistration(event.getId(), cancelled.getId());

        EnrolledReportDto report = app.reportUseCase.getEnrolledReport(event.getId());
        assertEquals(1, report.getTotalEnrolled());
        assertEquals(1, report.getTotalCancelled());
        assertEquals(20, report.getEventCapacity());
        assertEquals(2, report.getParticipants().size());

        byte[] bytes = app.reportUseCase.exportEnrolledCsv(event.getId());
        assertEquals((byte) 0xEF, bytes[0], "BOM UTF-8 para o Excel");
        String text = csv(bytes);
        assertTrue(text.contains("ID_PARTICIPANTE,NOME,EMAIL,STATUS_INSCRICAO"));
        assertTrue(text.contains("\"Mantida\""));
        assertTrue(text.contains("\"Cancelada\""));
    }

    @Test
    @DisplayName("o CSV neutraliza fórmulas e escapa aspas")
    void csvSafety() {
        Event event = Fixtures.newEvent(app, organizer, 20);
        Fixtures.addActivity(app, event, "A", "Sala 1", 9, 1);
        Fixtures.publish(app, event);
        User evil = Fixtures.participant(app, "=HYPERLINK(\"http://x\")");
        app.registrationUseCase.registerForEvent(event.getId(), evil.getId(), Set.of());
        String text = csv(app.reportUseCase.exportEnrolledCsv(event.getId()));
        assertTrue(text.contains("\"'=HYPERLINK(\"\"http://x\"\")\""), text);
    }

    @Test
    @DisplayName("relatório de frequência traz a situação calculada por participante e atividade")
    void attendanceReport() {
        Event event = Fixtures.newEvent(app, organizer, 20);
        Activity a = Fixtures.addActivity(app, event, "A", "Sala 1", 9, 1);
        Activity b = Fixtures.addActivity(app, event, "B", "Sala 1", 11, 1);
        Fixtures.publish(app, event);
        User ana = Fixtures.participant(app, "Ana");
        User beto = Fixtures.participant(app, "Beto");
        app.registrationUseCase.registerForEvent(event.getId(), ana.getId(), Set.of(a.getId(), b.getId()));
        app.registrationUseCase.registerForEvent(event.getId(), beto.getId(), Set.of(a.getId()));
        present(a, ana);

        AttendanceReportDto report = app.reportUseCase.getAttendanceReport(event.getId());
        assertEquals(2, report.getTotalParticipants());
        assertEquals(3, report.getRows().size(), "ana x (a,b) + beto x a");
        var summaryA = report.getActivitySummaries().stream().filter(s -> s.getActivityId().equals(a.getId())).findFirst().orElseThrow();
        assertEquals(1, summaryA.getTotalPresent());
        assertEquals(1, summaryA.getTotalAbsent());
        assertEquals(33.3, report.getOverallAttendanceRate(), 0.1);
        var anaRow = report.getRows().stream()
                .filter(r -> r.getUserId().equals(ana.getId()) && r.getActivityId().equals(a.getId())).findFirst().orElseThrow();
        assertEquals("PRESENT", anaRow.getStatus().name());
        assertTrue(anaRow.getLastRecordedBy().startsWith("MANUAL_BY_USER_"));

        String text = csv(app.reportUseCase.exportAttendanceCsv(event.getId()));
        assertTrue(text.contains("SITUACAO"));
        assertTrue(text.contains("\"Presente\""));
        assertTrue(text.contains("\"Ausente\""));
        assertTrue(app.reportUseCase.exportAttendancePdf(event.getId()).length > 100);
    }

    @Test
    @DisplayName("elegibilidade por percentual: justifica quando não atinge e emite quando atinge")
    void percentageEligibility() {
        Event event = Fixtures.newEvent(app, organizer, 20, new MinimumAttendancePercentagePolicy(60));
        Activity a = Fixtures.addActivity(app, event, "A", "Sala 1", 9, 2);
        Activity b = Fixtures.addActivity(app, event, "B", "Sala 1", 12, 2);
        Fixtures.publish(app, event);
        User p = Fixtures.participant(app, "Meio a meio");
        app.registrationUseCase.registerForEvent(event.getId(), p.getId(), Set.of(a.getId(), b.getId()));
        present(a, p);

        CertificateEligibilityDto notYet = app.certificateUseCase.checkEligibility(event.getId(), p.getId());
        assertFalse(notYet.eligible());
        assertEquals(1, notYet.activitiesAttended());
        assertEquals(2, notYet.totalActivities());
        assertEquals(2.0, notYet.hoursAttended(), 0.001);
        assertEquals(4.0, notYet.totalHours(), 0.001);
        assertTrue(notYet.justification().contains("60%"), notYet.justification());
        assertThrows(BusinessRuleException.class, () -> app.certificateUseCase.issueCertificateIfEligible(event.getId(), p.getId()));

        present(b, p);
        CertificateEligibilityDto now = app.certificateUseCase.checkEligibility(event.getId(), p.getId());
        assertTrue(now.eligible());
        Certificate cert = app.certificateUseCase.issueCertificateIfEligible(event.getId(), p.getId());
        assertEquals(4.0, cert.getCompletionHours(), 0.001);
        assertEquals(cert.getVerificationCode(), app.certificateUseCase.issueCertificateIfEligible(event.getId(), p.getId()).getVerificationCode(),
                "emitir de novo devolve o mesmo certificado");
        assertEquals(1, app.certificateUseCase.getUserCertificates(p.getId()).size());
        assertTrue(app.certificateUseCase.checkEligibility(event.getId(), p.getId()).alreadyIssued());
    }

    @Test
    @DisplayName("elegibilidade por atividades obrigatórias configurada depois que a programação existe (RF-32)")
    void requiredActivitiesEligibility() {
        Event event = Fixtures.newEvent(app, organizer, 20);
        Activity must = Fixtures.addActivity(app, event, "Obrigatória", "Sala 1", 9, 1, 50, new SingleCheckInPolicy(), true);
        Activity optional = Fixtures.addActivity(app, event, "Opcional", "Sala 1", 11, 1, 50, new SingleCheckInPolicy(), true);
        Fixtures.publish(app, event);
        app.eventUseCase.changeCertificatePolicy(event.getId(), new RequiredActivitiesPolicy(Set.of(must.getId())));

        User onlyOptional = Fixtures.participant(app, "So opcional");
        User both = Fixtures.participant(app, "Completo");
        for (User u : List.of(onlyOptional, both)) {
            app.registrationUseCase.registerForEvent(event.getId(), u.getId(), Set.of(must.getId(), optional.getId()));
        }
        present(optional, onlyOptional);
        present(must, both);

        assertFalse(app.certificateUseCase.checkEligibility(event.getId(), onlyOptional.getId()).eligible());
        assertTrue(app.certificateUseCase.checkEligibility(event.getId(), both.getId()).eligible());

        ValidationException foreign = assertThrows(ValidationException.class, () ->
                app.eventUseCase.changeCertificatePolicy(event.getId(), new RequiredActivitiesPolicy(Set.of(999_999L))));
        assertTrue(foreign.getMessage().contains("não pertence"));
    }

    @Test
    @DisplayName("certificado emitido não muda quando a regra do evento muda depois (RN-16), e a regra congela ao encerrar")
    void issuedCertificatesAreHistorical() {
        Event event = Fixtures.newEvent(app, organizer, 20, new MinimumAttendancePercentagePolicy(50));
        Activity a = Fixtures.addActivity(app, event, "A", "Sala 1", 9, 1);
        Fixtures.addActivity(app, event, "B", "Sala 1", 11, 3);
        Fixtures.publish(app, event);
        User p = Fixtures.participant(app, "Histórico");
        User late = Fixtures.participant(app, "Tardio");
        app.registrationUseCase.registerForEvent(event.getId(), p.getId(), Set.of(a.getId()));
        app.registrationUseCase.registerForEvent(event.getId(), late.getId(), Set.of(a.getId()));
        present(a, p);
        present(a, late);

        // 1h de 4h = 25%: ainda não elegível com 50%; baixando a exigência para 20% fica elegível.
        assertFalse(app.certificateUseCase.checkEligibility(event.getId(), p.getId()).eligible());
        app.eventUseCase.changeCertificatePolicy(event.getId(), new MinimumAttendancePercentagePolicy(20));
        Certificate issued = app.certificateUseCase.issueCertificateIfEligible(event.getId(), p.getId());

        app.eventUseCase.changeCertificatePolicy(event.getId(), new MinimumAttendancePercentagePolicy(90));
        assertEquals(issued.getVerificationCode(), app.certificateUseCase.getUserCertificates(p.getId()).get(0).getVerificationCode());
        assertTrue(app.certificateUseCase.checkEligibility(event.getId(), p.getId()).eligible(), "já emitido continua válido");
        assertFalse(app.certificateUseCase.checkEligibility(event.getId(), late.getId()).eligible(), "novos pedidos seguem a regra vigente");

        app.eventUseCase.finishEvent(event.getId());
        assertThrows(BusinessRuleException.class,
                () -> app.eventUseCase.changeCertificatePolicy(event.getId(), new MinimumAttendancePercentagePolicy(10)));
    }

    /** Participante com presença validada e certificado emitido. */
    private Certificate issuedCertificate(User participant) {
        Event event = Fixtures.newEvent(app, organizer, 20);
        Activity a = Fixtures.addActivity(app, event, "A", "Sala 1", 9, 1);
        Fixtures.publish(app, event);
        app.registrationUseCase.registerForEvent(event.getId(), participant.getId(), Set.of(a.getId()));
        present(a, participant);
        return app.certificateUseCase.issueCertificateIfEligible(event.getId(), participant.getId());
    }

    @Test
    @DisplayName("envia o certificado em PDF ao e-mail cadastrado e registra a tentativa (RF-34)")
    void sendCertificateByEmail() {
        app.emailSender.failWith(null);
        User ana = Fixtures.participant(app, "Ana Destinatária");
        Certificate cert = issuedCertificate(ana);

        var delivery = app.certificateUseCase.sendCertificateByEmail(cert.getId());

        assertEquals(com.eventos.domain.model.DeliveryStatus.SENT, delivery.getStatus());
        assertEquals(ana.getEmail().getValue(), delivery.getRecipient());
        var message = app.emailSender.sent().stream().filter(m -> m.to().equals(ana.getEmail().getValue())).findFirst().orElseThrow();
        assertTrue(message.subject().contains(cert.getEventTitle()));
        assertTrue(message.body().contains(cert.getVerificationCode()));
        assertEquals("application/pdf", message.attachmentContentType());
        assertEquals("%PDF", new String(message.attachment(), 0, 4, StandardCharsets.US_ASCII));
        assertEquals(List.of(delivery.getId()), app.certificateUseCase.getDeliveries(cert.getId()).stream()
                .map(com.eventos.domain.model.CertificateDelivery::getId).toList(), "a tentativa ficou registrada");
    }

    @Test
    @DisplayName("falha no envio é registrada com o motivo, não derruba a operação e o download continua possível")
    void failedDeliveryIsRecordedAndDownloadStillWorks() {
        User beto = Fixtures.participant(app, "Beto Sem Servidor");
        Certificate cert = issuedCertificate(beto);
        app.emailSender.failWith("Servidor SMTP indisponível");

        var delivery = app.certificateUseCase.sendCertificateByEmail(cert.getId());

        assertEquals(com.eventos.domain.model.DeliveryStatus.FAILED, delivery.getStatus());
        assertTrue(delivery.getDetail().contains("indisponível"));
        assertEquals("%PDF", new String(app.certificateUseCase.exportCertificatePdf(cert.getId()), 0, 4, StandardCharsets.US_ASCII),
                "RF-34: a falha não elimina a possibilidade de download");
        assertEquals(1, app.certificateUseCase.getDeliveries(cert.getId()).size());

        app.emailSender.failWith(null);
        var retry = app.certificateUseCase.sendCertificateByEmail(cert.getId());
        assertEquals(com.eventos.domain.model.DeliveryStatus.SENT, retry.getStatus(), "falha anterior não bloqueia nova tentativa");
        assertEquals(2, app.certificateUseCase.getDeliveries(cert.getId()).size());
        assertEquals(com.eventos.domain.model.DeliveryStatus.SENT, app.certificateUseCase.getDeliveries(cert.getId()).get(0).getStatus(),
                "a mais recente vem primeiro");
    }

    @Test
    @DisplayName("clique repetido logo após um envio bem-sucedido não manda o mesmo e-mail duas vezes (RNF-10)")
    void repeatedSendDoesNotDuplicate() {
        app.emailSender.failWith(null);
        User carla = Fixtures.participant(app, "Carla Clique");
        Certificate cert = issuedCertificate(carla);

        var first = app.certificateUseCase.sendCertificateByEmail(cert.getId());
        var second = app.certificateUseCase.sendCertificateByEmail(cert.getId());

        assertEquals(first.getId(), second.getId());
        assertEquals(1, app.emailSender.sent().stream().filter(m -> m.to().equals(carla.getEmail().getValue())).count());
        assertEquals(1, app.certificateUseCase.getDeliveries(cert.getId()).size());
        assertThrows(com.eventos.domain.exceptions.EntityNotFoundException.class, () -> app.certificateUseCase.sendCertificateByEmail(987_654L));
    }

    @Test
    @DisplayName("declaração para palestrante reflete a atividade e o papel exercido (RF-35)")
    void speakerDeclaration() {
        Event event = Fixtures.newEvent(app, organizer, 20);
        Activity talk = app.eventUseCase.addActivityToEvent(event.getId(), new Activity(null, event.getId(), "Mesa de Arquitetura", "",
                new com.eventos.domain.model.Period(Fixtures.firstDay().withHour(9), Fixtures.firstDay().withHour(11)),
                new com.eventos.domain.model.ActivityLocation("Auditório", "Trilha", 30),
                com.eventos.domain.model.ActivityType.ROUND_TABLE, 30, 0,
                List.of(new com.eventos.domain.model.Speaker(null, "Dra. Helena Prado", "Mediadora", "Bio", null)),
                new SingleCheckInPolicy(), true));
        Long speakerId = talk.getSpeakers().get(0).getId();

        byte[] pdf = app.certificateUseCase.exportSpeakerDeclarationPdf(talk.getId(), speakerId);
        assertEquals("%PDF", new String(pdf, 0, 4, StandardCharsets.US_ASCII));
        assertThrows(com.eventos.domain.exceptions.EntityNotFoundException.class,
                () -> app.certificateUseCase.exportSpeakerDeclarationPdf(talk.getId(), 987_654L));
    }

    @Test
    @DisplayName("participante sem inscrição confirmada não obtém certificado")
    void unregisteredUser() {
        Event event = Fixtures.newEvent(app, organizer, 20);
        Fixtures.addActivity(app, event, "A", "Sala 1", 9, 1);
        Fixtures.publish(app, event);
        User stranger = Fixtures.participant(app, "Sem inscrição");
        assertFalse(app.certificateUseCase.checkEligibility(event.getId(), stranger.getId()).eligible());
        assertThrows(BusinessRuleException.class, () -> app.certificateUseCase.issueCertificateIfEligible(event.getId(), stranger.getId()));
    }
}
