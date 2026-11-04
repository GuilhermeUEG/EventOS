package com.eventos.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eventos.application.dtos.AttendanceResult;
import com.eventos.application.dtos.EnrolledReportDto;
import com.eventos.application.dtos.QrCodeDto;
import com.eventos.application.dtos.SurveyResultsDto;
import com.eventos.domain.exceptions.BusinessRuleException;
import com.eventos.domain.model.Activity;
import com.eventos.domain.model.AttendanceStatus;
import com.eventos.domain.model.AttendanceType;
import com.eventos.domain.model.Certificate;
import com.eventos.domain.model.Event;
import com.eventos.domain.model.EventStatus;
import com.eventos.domain.model.EvaluationEligibility;
import com.eventos.domain.model.FeedbackSurvey;
import com.eventos.domain.model.QuestionType;
import com.eventos.domain.model.Registration;
import com.eventos.domain.model.SurveyQuestion;
import com.eventos.domain.model.User;
import com.eventos.domain.model.UserRole;
import com.eventos.support.Fixtures;
import com.eventos.support.TestApp;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Cenários de aceitação CA-01 a CA-08 integrados (casos de uso reais + banco H2)")
class EndToEndScenarioTest {
    private static TestApp app;

    @BeforeAll
    static void start() {
        app = TestApp.start();
    }

    @AfterAll
    static void stop() {
        app.close();
    }

    @Test
    @DisplayName("publicação, inscrição, agenda, QR, manual, avaliação, relatório e certificado")
    void fullFlow() {
        User organizer = app.createUser("Organizador", UserRole.ORGANIZER);
        User present = Fixtures.participant(app, "Participante Presente");
        User absent = Fixtures.participant(app, "Participante Ausente");

        // CA-01: o organizador cria evento e atividade e publica; o visitante passa a ver o evento.
        Event event = Fixtures.newEvent(app, organizer, 50);
        Activity talk = Fixtures.addActivity(app, event, "Palestra de Arquitetura", "Auditório", 9, 2);
        assertFalse(app.eventUseCase.filterEvents(null, null, null, null, false).stream()
                .anyMatch(e -> e.getId().equals(event.getId())), "rascunho não aparece para visitantes");
        assertEquals(EventStatus.PUBLISHED, Fixtures.publish(app, event).getStatus());
        assertTrue(app.eventUseCase.filterEvents(null, null, null, null, false).stream()
                .anyMatch(e -> e.getId().equals(event.getId())), "evento publicado aparece para visitantes");

        // CA-02: inscrição confirmada e consultável.
        Registration reg = app.registrationUseCase.registerForEvent(event.getId(), present.getId(), Set.of(talk.getId()));
        app.registrationUseCase.registerForEvent(event.getId(), absent.getId(), Set.of(talk.getId()));
        assertTrue(reg.isConfirmed());
        assertEquals(2, app.activities.findById(talk.getId()).orElseThrow().getCurrentEnrollments());

        // CA-03: agenda cronológica só com o escolhido.
        List<Activity> agenda = app.registrationUseCase.getParticipantAgenda(present.getId(), event.getId());
        assertEquals(List.of("Palestra de Arquitetura"), agenda.stream().map(Activity::getTitle).toList());

        // CA-04: presença por QR Code, com situação calculada pela política da atividade.
        QrCodeDto qr = app.attendanceUseCase.generateQrCode(talk.getId());
        assertEquals((byte) 0x89, qr.pngImage()[0]);
        assertEquals('P', qr.pngImage()[1]);
        assertEquals(15 * 60, qr.validForSeconds());
        AttendanceResult scan = app.attendanceUseCase.recordQrAttendance(qr.token(), present.getId());
        assertFalse(scan.alreadyRecorded());
        assertEquals(AttendanceStatus.PRESENT, scan.status());

        // CA-05: lançamento manual preserva autoria, horário e justificativa.
        var manual = app.attendanceUseCase.recordManualAttendance(talk.getId(), absent.getId(), organizer.getId(),
                AttendanceType.MANUAL_ENTRY, "Sem leitura do QR Code por falha no celular");
        assertEquals("MANUAL_BY_USER_" + organizer.getId(), manual.getRecordedBy());
        assertNotNull(manual.getTimestamp());
        app.attendanceUseCase.revokeAttendance(talk.getId(), absent.getId(), organizer.getId(), "Lançamento feito no participante errado");
        assertEquals(AttendanceStatus.ABSENT, app.attendanceUseCase.evaluateParticipantAttendance(talk.getId(), absent.getId()));

        // CA-06: o presente avalia; o ausente é impedido, com o motivo.
        FeedbackSurvey survey = app.surveyUseCase.createSurvey(new FeedbackSurvey(null, talk.getId(), "Pesquisa",
                List.of(new SurveyQuestion(null, "Nota geral?", QuestionType.RATING_SCALE, List.of(), true),
                        new SurveyQuestion(null, "Comentário", QuestionType.TEXT, List.of(), false)), true, true));
        Long ratingId = survey.getQuestions().get(0).getId();
        Long commentId = survey.getQuestions().get(1).getId();
        assertTrue(app.surveyUseCase.canUserEvaluate(present.getId(), talk.getId()));
        EvaluationEligibility blocked = app.surveyUseCase.checkEvaluationEligibility(absent.getId(), talk.getId());
        assertFalse(blocked.eligible());
        assertTrue(blocked.reason().contains("presença"));
        assertThrows(BusinessRuleException.class,
                () -> app.surveyUseCase.submitResponse(talk.getId(), absent.getId(), Map.of(ratingId, "5")));
        var saved = app.surveyUseCase.submitResponse(talk.getId(), present.getId(), Map.of(ratingId, "5", commentId, "Excelente"));
        assertTrue(saved.isAnonymous(), "a política de anonimato vem do questionário");
        assertThrows(BusinessRuleException.class,
                () -> app.surveyUseCase.submitResponse(talk.getId(), present.getId(), Map.of(ratingId, "1")), "uma resposta por participante");
        SurveyResultsDto results = app.surveyUseCase.getConsolidatedResults(talk.getId());
        assertEquals(1, results.getTotalResponses());
        assertEquals(5.0, results.getAverageRating(), 0.0001);
        assertTrue(results.isAnonymousResponses());
        assertEquals(List.of("[Anônimo] Excelente"), results.getTextComments());

        // CA-07: relatório coerente com inscrições e presenças.
        EnrolledReportDto enrolled = app.reportUseCase.getEnrolledReport(event.getId());
        assertEquals(2, enrolled.getTotalEnrolled());
        var attendance = app.reportUseCase.getAttendanceReport(event.getId());
        assertEquals(1, attendance.getActivitySummaries().get(0).getTotalPresent());
        assertEquals(1, attendance.getActivitySummaries().get(0).getTotalAbsent());
        assertEquals(2, attendance.getRows().size());
        assertTrue(new String(app.reportUseCase.exportAttendanceCsv(event.getId()), java.nio.charset.StandardCharsets.UTF_8)
                .contains("Participante Presente"));
        assertTrue(app.reportUseCase.exportEnrolledPdf(event.getId()).length > 100);

        // CA-08: elegível obtém PDF; inelegível recebe a justificativa.
        Certificate certificate = app.certificateUseCase.issueCertificateIfEligible(event.getId(), present.getId());
        assertNotNull(certificate.getVerificationCode());
        assertEquals(2.0, certificate.getCompletionHours(), 0.0001);
        assertEquals(certificate.getVerificationCode(),
                app.certificateUseCase.getCertificateByVerificationCode(certificate.getVerificationCode()).getVerificationCode());
        byte[] pdf = app.certificateUseCase.exportCertificatePdf(certificate.getId());
        assertEquals("%PDF", new String(pdf, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
        assertTrue(new String(pdf, java.nio.charset.StandardCharsets.ISO_8859_1).contains("/Subtype/Image")
                        || new String(pdf, java.nio.charset.StandardCharsets.ISO_8859_1).contains("/Subtype /Image"),
                "o certificado leva o símbolo do EventOS como imagem");

        var denied = app.certificateUseCase.checkEligibility(event.getId(), absent.getId());
        assertFalse(denied.eligible());
        assertTrue(denied.justification().contains("0/1"), denied.justification());
        BusinessRuleException refusal = assertThrows(BusinessRuleException.class,
                () -> app.certificateUseCase.issueCertificateIfEligible(event.getId(), absent.getId()));
        assertTrue(refusal.getMessage().contains("critérios"));
    }
}
