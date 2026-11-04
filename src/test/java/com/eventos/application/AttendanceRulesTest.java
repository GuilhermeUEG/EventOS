package com.eventos.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eventos.application.dtos.AttendanceResult;
import com.eventos.application.dtos.AttendanceStatusDto;
import com.eventos.domain.exceptions.BusinessRuleException;
import com.eventos.domain.exceptions.EntityNotFoundException;
import com.eventos.domain.exceptions.ForbiddenException;
import com.eventos.domain.exceptions.ValidationException;
import com.eventos.domain.model.Activity;
import com.eventos.domain.model.AttendanceRecord;
import com.eventos.domain.model.AttendanceStatus;
import com.eventos.domain.model.AttendanceType;
import com.eventos.domain.model.Event;
import com.eventos.domain.model.EventStatus;
import com.eventos.domain.model.FeedbackSurvey;
import com.eventos.domain.model.QuestionType;
import com.eventos.domain.model.SurveyQuestion;
import com.eventos.domain.model.User;
import com.eventos.domain.model.UserRole;
import com.eventos.domain.policies.CheckInCheckOutPolicy;
import com.eventos.domain.policies.ManualOnlyPolicy;
import com.eventos.domain.policies.SingleCheckInPolicy;
import com.eventos.support.Fixtures;
import com.eventos.support.TestApp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Frequência: QR Code, lançamento manual, revogação e rastreabilidade (RF-19 a RF-23, RN-09 a RN-12)")
class AttendanceRulesTest {
    private static TestApp app;
    private static User organizer;

    private Event event;
    private Activity single;
    private Activity inOut;
    private Activity manualOnly;
    private User enrolled;

    @BeforeAll
    static void start() {
        app = TestApp.start();
        organizer = app.createUser("Organizador Frequência", UserRole.ORGANIZER);
    }

    @AfterAll
    static void stop() {
        app.close();
    }

    @BeforeEach
    void setUp() {
        event = Fixtures.newEvent(app, organizer, 100);
        single = Fixtures.addActivity(app, event, "Check-in único", "Sala 1", 9, 1, 50, new SingleCheckInPolicy(), true);
        inOut = Fixtures.addActivity(app, event, "Entrada e saída", "Sala 2", 11, 1, 50, new CheckInCheckOutPolicy(), true);
        manualOnly = Fixtures.addActivity(app, event, "Só manual", "Sala 3", 13, 1, 50, new ManualOnlyPolicy(), true);
        Fixtures.publish(app, event);
        enrolled = Fixtures.participant(app, "Inscrito");
        app.registrationUseCase.registerForEvent(event.getId(), enrolled.getId(),
                Set.of(single.getId(), inOut.getId(), manualOnly.getId()));
    }

    private String token(Activity a) {
        return app.attendanceUseCase.generateQrCode(a.getId()).token();
    }

    private AttendanceStatus status(Activity a, User u) {
        return app.attendanceUseCase.evaluateParticipantAttendance(a.getId(), u.getId());
    }

    @Test
    @DisplayName("o QR Code não contém dado pessoal nem senha em texto aberto (RN-10)")
    void qrPayloadHasNoPersonalData() {
        String decoded = new String(java.util.Base64.getUrlDecoder().decode(token(single)), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(decoded.startsWith("ACT_" + single.getId() + "_"));
        assertFalse(decoded.contains(enrolled.getName()));
        assertFalse(decoded.contains(enrolled.getEmail().getValue()));
    }

    @Test
    @DisplayName("participante não inscrito no evento não registra presença")
    void notRegisteredCannotCheckIn() {
        User stranger = Fixtures.participant(app, "Estranho");
        BusinessRuleException e = assertThrows(BusinessRuleException.class,
                () -> app.attendanceUseCase.recordQrAttendance(token(single), stranger.getId()));
        assertTrue(e.getMessage().contains("inscrito"));
    }

    @Test
    @DisplayName("inscrito no evento, mas sem ter escolhido a atividade, não registra presença nela")
    void mustHaveChosenTheActivity() {
        User partial = Fixtures.participant(app, "Escolheu pouco");
        app.registrationUseCase.registerForEvent(event.getId(), partial.getId(), Set.of(inOut.getId()));
        assertThrows(BusinessRuleException.class, () -> app.attendanceUseCase.recordQrAttendance(token(single), partial.getId()));
        assertEquals(AttendanceStatus.PARTIAL, app.attendanceUseCase.recordQrAttendance(token(inOut), partial.getId()).status());
    }

    @Test
    @DisplayName("token adulterado, malformado ou vazio é recusado")
    void invalidTokens() {
        String good = token(single);
        String tampered = good.substring(0, good.length() - 3) + (good.endsWith("AAA") ? "BBB" : "AAA");
        for (String bad : new String[]{tampered, "lixo", "", null, "QUNUXzFfMQ"}) {
            BusinessRuleException e = assertThrows(BusinessRuleException.class,
                    () -> app.attendanceUseCase.recordQrAttendance(bad, enrolled.getId()));
            assertTrue(e.getMessage().contains("inválido"));
        }
        assertEquals(AttendanceStatus.ABSENT, status(single, enrolled));
    }

    @Test
    @DisplayName("leitura repetida do mesmo QR não duplica o registro (RNF-10)")
    void repeatedScanIsIdempotent() {
        String token = token(single);
        AttendanceResult first = app.attendanceUseCase.recordQrAttendance(token, enrolled.getId());
        AttendanceResult second = app.attendanceUseCase.recordQrAttendance(token, enrolled.getId());
        assertFalse(first.alreadyRecorded());
        assertTrue(second.alreadyRecorded());
        assertEquals(first.record().getId(), second.record().getId());
        assertEquals(1, app.attendanceUseCase.getActivityRecords(single.getId()).size());
        assertEquals(AttendanceStatus.PRESENT, second.status());
    }

    @Test
    @DisplayName("entrada e saída: a leitura acidental logo após a entrada é ignorada e o status fica parcial")
    void inOutAccidentalRepeat() {
        String token = token(inOut);
        AttendanceResult in = app.attendanceUseCase.recordQrAttendance(token, enrolled.getId());
        AttendanceResult repeat = app.attendanceUseCase.recordQrAttendance(token, enrolled.getId());
        assertEquals(AttendanceType.CHECK_IN, in.record().getType());
        assertEquals(AttendanceStatus.PARTIAL, in.status());
        assertTrue(repeat.alreadyRecorded());
        assertEquals(1, app.attendanceUseCase.getActivityRecords(inOut.getId()).size());

        app.attendanceUseCase.recordManualAttendance(inOut.getId(), enrolled.getId(), organizer.getId(),
                AttendanceType.CHECK_OUT, "Saída conferida pela lista de presença");
        assertEquals(AttendanceStatus.PRESENT, status(inOut, enrolled));
    }

    @Test
    @DisplayName("atividade de validação manual recusa o QR Code, mas aceita o lançamento manual")
    void manualOnlyActivity() {
        BusinessRuleException e = assertThrows(BusinessRuleException.class,
                () -> app.attendanceUseCase.recordQrAttendance(token(manualOnly), enrolled.getId()));
        assertTrue(e.getMessage().contains("manual"));
        app.attendanceUseCase.recordManualAttendance(manualOnly.getId(), enrolled.getId(), organizer.getId(), null, "Conferido na porta");
        assertEquals(AttendanceStatus.PRESENT, status(manualOnly, enrolled));
    }

    @Test
    @DisplayName("lançamento manual exige justificativa, perfil de gestão e participante inscrito (RN-11)")
    void manualRules() {
        assertThrows(BusinessRuleException.class, () -> app.attendanceUseCase.recordManualAttendance(
                single.getId(), enrolled.getId(), organizer.getId(), AttendanceType.MANUAL_ENTRY, "  "));
        assertThrows(BusinessRuleException.class, () -> app.attendanceUseCase.recordManualAttendance(
                single.getId(), enrolled.getId(), organizer.getId(), AttendanceType.MANUAL_ENTRY, null));
        assertThrows(ForbiddenException.class, () -> app.attendanceUseCase.recordManualAttendance(
                single.getId(), enrolled.getId(), enrolled.getId(), AttendanceType.MANUAL_ENTRY, "Eu mesmo"));
        User stranger = Fixtures.participant(app, "Não inscrito");
        assertThrows(BusinessRuleException.class, () -> app.attendanceUseCase.recordManualAttendance(
                single.getId(), stranger.getId(), organizer.getId(), AttendanceType.MANUAL_ENTRY, "Motivo"));
        assertThrows(EntityNotFoundException.class, () -> app.attendanceUseCase.recordManualAttendance(
                single.getId(), 99_999_999L, organizer.getId(), AttendanceType.MANUAL_ENTRY, "Motivo"));
        assertThrows(ValidationException.class, () -> app.attendanceUseCase.recordManualAttendance(
                single.getId(), enrolled.getId(), organizer.getId(), AttendanceType.REVOCATION, "Motivo"));
        assertEquals(0, app.attendanceUseCase.getActivityRecords(single.getId()).size());
    }

    @Test
    @DisplayName("revogar presença corrige a situação sem apagar nada e identifica autor e motivo (RN-12)")
    void revocationKeepsAuditTrail() {
        app.attendanceUseCase.recordQrAttendance(token(single), enrolled.getId());
        assertEquals(AttendanceStatus.PRESENT, status(single, enrolled));

        AttendanceRecord revocation = app.attendanceUseCase.revokeAttendance(single.getId(), enrolled.getId(), organizer.getId(),
                "Leitura feita por colega com o celular do participante");
        assertEquals(AttendanceType.REVOCATION, revocation.getType());
        assertEquals("MANUAL_BY_USER_" + organizer.getId(), revocation.getRecordedBy());
        assertTrue(revocation.getNotes().contains("colega"));
        assertEquals(AttendanceStatus.ABSENT, status(single, enrolled));

        List<AttendanceRecord> history = app.attendanceUseCase.getActivityRecords(single.getId());
        assertEquals(List.of(AttendanceType.CHECK_IN, AttendanceType.REVOCATION), history.stream().map(AttendanceRecord::getType).toList(),
                "o histórico preserva o registro original");

        assertThrows(BusinessRuleException.class, () -> app.attendanceUseCase.revokeAttendance(
                single.getId(), enrolled.getId(), organizer.getId(), "De novo"), "não há mais presença vigente");
        assertThrows(BusinessRuleException.class, () -> app.attendanceUseCase.revokeAttendance(
                single.getId(), enrolled.getId(), organizer.getId(), " "));
        assertThrows(ForbiddenException.class, () -> app.attendanceUseCase.revokeAttendance(
                single.getId(), enrolled.getId(), enrolled.getId(), "Quero me revogar"));

        app.attendanceUseCase.recordManualAttendance(single.getId(), enrolled.getId(), organizer.getId(), null, "Presença confirmada na lista");
        assertEquals(AttendanceStatus.PRESENT, status(single, enrolled));
    }

    @Test
    @DisplayName("revogar a presença remove a elegibilidade para avaliar (RN-13)")
    void revokedAttendanceBlocksEvaluation() {
        FeedbackSurvey survey = app.surveyUseCase.createSurvey(new FeedbackSurvey(null, single.getId(), "Pesquisa",
                List.of(new SurveyQuestion(null, "Nota?", QuestionType.RATING_SCALE, List.of(), true)), true));
        app.attendanceUseCase.recordQrAttendance(token(single), enrolled.getId());
        assertTrue(app.surveyUseCase.canUserEvaluate(enrolled.getId(), single.getId()));
        app.attendanceUseCase.revokeAttendance(single.getId(), enrolled.getId(), organizer.getId(), "Presença indevida");
        var eligibility = app.surveyUseCase.checkEvaluationEligibility(enrolled.getId(), single.getId());
        assertFalse(eligibility.eligible());
        assertThrows(BusinessRuleException.class, () -> app.surveyUseCase.submitResponse(single.getId(), enrolled.getId(),
                Map.of(survey.getQuestions().get(0).getId(), "5")));
    }

    @Test
    @DisplayName("a visão geral lista os inscritos na atividade com a situação calculada")
    void overview() {
        User other = Fixtures.participant(app, "Outro inscrito");
        app.registrationUseCase.registerForEvent(event.getId(), other.getId(), Set.of(inOut.getId()));
        app.attendanceUseCase.recordQrAttendance(token(single), enrolled.getId());

        List<AttendanceStatusDto> overview = app.attendanceUseCase.getActivityAttendanceOverview(single.getId());
        assertEquals(1, overview.size(), "só quem escolheu a atividade aparece");
        assertEquals(AttendanceStatus.PRESENT, overview.get(0).getStatus());
        assertEquals("QR_SCAN", overview.get(0).getRecordedBy());
        assertEquals(2, app.attendanceUseCase.getActivityAttendanceOverview(inOut.getId()).size());
    }

    @Test
    @DisplayName("o horário do registro segue o fuso do evento (RN-19)")
    void timestampUsesEventTimeZone() {
        app.eventUseCase.updateEvent(event.getId(), new com.eventos.application.commands.EventUpdate(
                null, null, null, null, null, null, null, "Pacific/Auckland"));
        AttendanceResult result = app.attendanceUseCase.recordQrAttendance(token(single), enrolled.getId());
        LocalDateTime inEventZone = LocalDateTime.now(ZoneId.of("Pacific/Auckland"));
        assertTrue(Duration.between(result.record().getTimestamp(), inEventZone).abs().toMinutes() < 2);
    }

    @Test
    @DisplayName("evento sem escolha de atividades: a inscrição no evento basta para frequência, agenda e avaliação (RN-05)")
    void wholeEventTicketGrantsAccessToEveryActivity() {
        Event open = Fixtures.newEvent(app, organizer, 100);
        Activity talk = Fixtures.addActivity(app, open, "Palestra única", "Sala 1", 9, 1, 5, new SingleCheckInPolicy(), true);
        Activity workshop = Fixtures.addActivity(app, open, "Oficina", "Sala 2", 11, 1, 5, new SingleCheckInPolicy(), true);
        Fixtures.publish(app, open);
        app.eventUseCase.updateEvent(open.getId(), new com.eventos.application.commands.EventUpdate(
                null, null, null, null, false, false, null, null));
        FeedbackSurvey survey = app.surveyUseCase.createSurvey(new FeedbackSurvey(null, talk.getId(), "Pesquisa",
                List.of(new SurveyQuestion(null, "Nota?", QuestionType.RATING_SCALE, List.of(), true)), true));
        User guest = Fixtures.participant(app, "Ingresso único");
        app.registrationUseCase.registerForEvent(open.getId(), guest.getId(), Set.of());

        assertEquals(List.of("Palestra única", "Oficina"), app.registrationUseCase.getParticipantAgenda(guest.getId(), open.getId())
                .stream().map(Activity::getTitle).toList(), "agenda é a programação inteira");
        AttendanceResult scan = app.attendanceUseCase.recordQrAttendance(token(talk), guest.getId());
        assertEquals(AttendanceStatus.PRESENT, scan.status());
        assertTrue(app.surveyUseCase.canUserEvaluate(guest.getId(), talk.getId()));
        app.surveyUseCase.submitResponse(talk.getId(), guest.getId(), Map.of(survey.getQuestions().get(0).getId(), "4"));
        assertEquals(1, app.attendanceUseCase.getActivityAttendanceOverview(workshop.getId()).size());
        assertEquals(2, app.reportUseCase.getAttendanceReport(open.getId()).getRows().size());
    }

    @Test
    @DisplayName("evento cancelado não impede consultar frequência já registrada")
    void historyRemainsAfterCancellation() {
        app.attendanceUseCase.recordQrAttendance(token(single), enrolled.getId());
        app.eventUseCase.cancelEvent(event.getId());
        assertEquals(EventStatus.CANCELLED, app.eventUseCase.getEvent(event.getId()).getStatus());
        assertEquals(1, app.attendanceUseCase.getActivityRecords(single.getId()).size());
        assertEquals(AttendanceStatus.PRESENT, status(single, enrolled));
    }
}
