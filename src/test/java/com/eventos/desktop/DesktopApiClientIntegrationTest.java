package com.eventos.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eventos.adapters.input.desktop.ApiException;
import com.eventos.adapters.input.desktop.DesktopModels.ActivityForm;
import com.eventos.adapters.input.desktop.DesktopModels.ActivityQuery;
import com.eventos.adapters.input.desktop.DesktopModels.ActivityView;
import com.eventos.adapters.input.desktop.DesktopModels.AttendanceOverviewRow;
import com.eventos.adapters.input.desktop.DesktopModels.AttendanceRecordView;
import com.eventos.adapters.input.desktop.DesktopModels.EventForm;
import com.eventos.adapters.input.desktop.DesktopModels.EventView;
import com.eventos.adapters.input.desktop.DesktopModels.PolicyForm;
import com.eventos.adapters.input.desktop.DesktopModels.QrCodeView;
import com.eventos.adapters.input.desktop.DesktopModels.QuestionForm;
import com.eventos.adapters.input.desktop.DesktopModels.SpeakerForm;
import com.eventos.adapters.input.desktop.DesktopModels.SurveyForm;
import com.eventos.adapters.input.desktop.DesktopModels.SurveyResultsView;
import com.eventos.adapters.input.desktop.DesktopModels.UserView;
import com.eventos.adapters.input.desktop.EventOsApiClient;
import com.eventos.domain.model.User;
import com.eventos.domain.model.UserRole;
import com.eventos.support.TestApp;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * O cliente do desktop exercitado contra a API real (casos de uso reais e banco H2 em memória):
 * mesmo contrato que o site consome, sem nenhuma interface gráfica.
 */
@DisplayName("Desktop: cliente HTTP contra a API real")
class DesktopApiClientIntegrationTest {
    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static TestApp app;

    @BeforeAll
    static void start() {
        app = TestApp.start();
    }

    @AfterAll
    static void stop() {
        app.close();
    }

    private EventOsApiClient loginAs(User user) {
        EventOsApiClient client = new EventOsApiClient(app.baseUrl);
        client.login(user.getEmail().getValue(), TestApp.DEFAULT_PASSWORD);
        return client;
    }

    private EventView newPublishedEvent(EventOsApiClient organizer, String suffix, LocalDateTime day) {
        EventForm form = new EventForm("Evento Desktop " + suffix, "Criado pelo cliente do desktop",
                day.withHour(8).format(BR), day.plusDays(1).withHour(18).format(BR), 100,
                day.withHour(8).format(BR), "America/Sao_Paulo", true, false,
                new PolicyForm("MIN_PERCENTAGE", 50.0, null, null));
        return organizer.createEvent(form);
    }

    private ActivityForm activity(String title, String start, String end, String room, String track, String type) {
        return new ActivityForm(title, "Descrição de " + title, start, end, room, track, 40, type,
                "SINGLE_CHECKIN", true, List.of(new SpeakerForm("Palestrante " + title, "Palestrante", "")));
    }

    @Test
    @DisplayName("fluxo do organizador: evento, atividades, conflito de sala, filtros, publicação e QR Code")
    void organizerFlow() throws Exception {
        User organizer = app.createUser("Organizador Desktop", UserRole.ORGANIZER);
        EventOsApiClient client = loginAs(organizer);
        assertEquals("ORGANIZER", client.currentUser().role());

        LocalDateTime day = LocalDateTime.now().plusDays(14);
        EventView event = newPublishedEvent(client, "fluxo", day);
        assertNotNull(event.id());
        assertEquals("DRAFT", event.status());
        assertEquals("MIN_PERCENTAGE", event.certificatePolicy().policyKey());

        String d = day.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        ActivityView talk = client.addActivity(event.id(), activity("Palestra TI", d + " 09:00", d + " 11:00", "Sala A", "TI", "LECTURE"));
        client.addActivity(event.id(), activity("Oficina Dados", d + " 09:00", d + " 11:00", "Sala B", "Dados", "WORKSHOP"));
        assertEquals("Palestrante Palestra TI", talk.speakers().get(0).name());

        ApiException conflict = assertThrows(ApiException.class, () ->
                client.addActivity(event.id(), activity("Choque", d + " 10:00", d + " 12:00", "Sala A", "TI", "LECTURE")));
        assertEquals(409, conflict.getStatus());
        assertTrue(conflict.getMessage().contains("Sala A"), conflict.getMessage());

        // filtros combináveis aplicados pelo servidor
        assertEquals(2, client.listActivities(event.id()).size());
        assertEquals(1, client.listActivities(event.id(), new ActivityQuery(null, null, null, "TI", null, null, null)).size());
        assertEquals(1, client.listActivities(event.id(), new ActivityQuery(null, null, null, null, "WORKSHOP", null, null)).size());
        assertEquals(1, client.listActivities(event.id(), new ActivityQuery(null, null, null, null, null, "Sala B", null)).size());
        assertEquals(1, client.listActivities(event.id(),
                new ActivityQuery(day.toLocalDate().toString(), "08:00", "10:00", "Dados", "WORKSHOP", "Sala B", "oficina")).size());
        assertEquals(0, client.listActivities(event.id(), new ActivityQuery(null, null, null, "TI", "WORKSHOP", null, null)).size());

        EventView published = client.publishEvent(event.id());
        assertEquals("PUBLISHED", published.status());
        assertTrue(client.listEvents().stream().anyMatch(e -> e.id().equals(event.id())));

        // critério de certificado com atividades obrigatórias
        EventView withPolicy = client.changeCertificatePolicy(event.id(),
                new PolicyForm("REQUIRED_ACTIVITIES", null, null, List.of(talk.id())));
        assertEquals("REQUIRED_ACTIVITIES", withPolicy.certificatePolicy().policyKey());

        // QR Code: imagem PNG decodificável + conteúdo assinado + validade
        QrCodeView qr = client.generateQrCode(talk.id());
        assertFalse(qr.qrToken().isBlank());
        assertTrue(qr.validForSeconds() > 0);
        assertNotNull(ImageIO.read(new ByteArrayInputStream(qr.imageBytes())), "a imagem do QR Code deve ser decodificável");

        // questionário configurável
        client.createSurvey(talk.id(), new SurveyForm("Avaliação da palestra", true, List.of(
                new QuestionForm("Nota geral", "RATING_SCALE", List.of(), true),
                new QuestionForm("Gostou?", "SINGLE_CHOICE", List.of("Sim", "Não"), true),
                new QuestionForm("Comentários", "TEXT", List.of(), false))));
        SurveyResultsView results = client.surveyResults(talk.id());
        assertEquals(0, results.totalResponses());
        assertTrue(results.anonymousResponses());
        assertEquals(3, results.questionMetrics().size());

        // relatórios exportáveis
        byte[] csv = client.enrolledCsv(event.id());
        assertEquals((byte) 0xEF, csv[0], "CSV em UTF-8 com BOM");
        assertTrue(client.attendanceCsv(event.id()).length > 0);
        assertTrue(client.enrolledPdf(event.id()).length > 100);
        assertTrue(client.attendancePdf(event.id()).length > 100);
        assertEquals(event.id(), client.attendanceReport(event.id()).eventId());
    }

    @Test
    @DisplayName("declaração de palestrante: o desktop baixa o PDF da pessoa vinculada; participante não consegue")
    void speakerDeclarationDownload() {
        User organizer = app.createUser("Org Declaracao", UserRole.ORGANIZER);
        User participant = app.createUser("Participante Declaracao", UserRole.PARTICIPANT);
        EventOsApiClient client = loginAs(organizer);

        LocalDateTime day = LocalDateTime.now().plusDays(25);
        EventView event = newPublishedEvent(client, "declaracao", day);
        String d = day.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        ActivityView act = client.addActivity(event.id(), activity("Mesa", d + " 09:00", d + " 10:00", "Sala D", "Geral", "ROUND_TABLE"));
        Long speakerId = act.speakers().get(0).id();

        byte[] pdf = client.speakerDeclarationPdf(act.id(), speakerId);
        assertEquals("%PDF", new String(pdf, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));

        ApiException notFound = assertThrows(ApiException.class, () -> client.speakerDeclarationPdf(act.id(), 987_654L));
        assertEquals(404, notFound.getStatus());
        ApiException forbidden = assertThrows(ApiException.class, () -> loginAs(participant).speakerDeclarationPdf(act.id(), speakerId));
        assertEquals(403, forbidden.getStatus());
    }

    @Test
    @DisplayName("frequência: lançamento manual com justificativa, revogação auditável e histórico")
    void manualAttendanceAndRevocation() {
        User organizer = app.createUser("Org Frequencia", UserRole.ORGANIZER);
        User participant = app.createUser("Participante Frequencia", UserRole.PARTICIPANT);
        EventOsApiClient client = loginAs(organizer);

        LocalDateTime day = LocalDateTime.now().plusDays(20);
        EventView event = newPublishedEvent(client, "frequencia", day);
        String d = day.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        ActivityView act = client.addActivity(event.id(), activity("Sessão", d + " 09:00", d + " 10:00", "Sala F", "Geral", "LECTURE"));
        client.publishEvent(event.id());
        app.registrationUseCase.registerForEvent(event.id(), participant.getId(), Set.of(act.id()));

        List<AttendanceOverviewRow> overview = client.attendanceOverview(act.id());
        assertEquals(1, overview.size());
        assertEquals("ABSENT", overview.get(0).status());

        ApiException noReason = assertThrows(ApiException.class, () ->
                client.recordManualAttendance(act.id(), participant.getId(), "MANUAL_ENTRY", "  "));
        assertTrue(noReason.getMessage().toLowerCase().contains("motivo"), noReason.getMessage());

        client.recordManualAttendance(act.id(), participant.getId(), "MANUAL_ENTRY", "Esqueceu de ler o QR Code");
        assertEquals("PRESENT", client.attendanceOverview(act.id()).get(0).status());

        client.revokeAttendance(act.id(), participant.getId(), "Lançamento feito por engano");
        assertEquals("ABSENT", client.attendanceOverview(act.id()).get(0).status());

        List<AttendanceRecordView> history = client.attendanceRecords(act.id());
        assertEquals(2, history.size(), "o histórico preserva o lançamento e a revogação");
        assertTrue(history.stream().anyMatch(r -> "REVOCATION".equals(r.type())));
        assertTrue(history.stream().allMatch(r -> r.recordedBy().startsWith("MANUAL_BY_USER_")));

        assertFalse(client.certificateEligibility(event.id(), participant.getId()).eligible());
        assertEquals(1, client.enrolledReport(event.id()).totalEnrolled());
    }

    @Test
    @DisplayName("erros da API chegam ao desktop como ApiException com a mensagem do servidor")
    void apiErrorsAreMapped() {
        EventOsApiClient anonymous = new EventOsApiClient(app.baseUrl);
        ApiException badLogin = assertThrows(ApiException.class, () -> anonymous.login("ninguem@teste.com", "senhaerrada"));
        assertEquals(401, badLogin.getStatus());
        assertTrue(badLogin.getMessage().toLowerCase().contains("credenciais"), badLogin.getMessage());

        User participant = app.createUser("Participante Comum", UserRole.PARTICIPANT);
        EventOsApiClient asParticipant = loginAs(participant);
        LocalDateTime day = LocalDateTime.now().plusDays(30);
        ApiException forbidden = assertThrows(ApiException.class, () -> newPublishedEvent(asParticipant, "proibido", day));
        assertEquals(403, forbidden.getStatus());

        User organizer = app.createUser("Org Validacao", UserRole.ORGANIZER);
        EventOsApiClient asOrganizer = loginAs(organizer);
        ApiException invalid = assertThrows(ApiException.class, () -> asOrganizer.createEvent(new EventForm(
                "  ", "", day.format(BR), day.plusDays(1).format(BR), 10, "", "America/Sao_Paulo", true, false,
                new PolicyForm("MIN_PERCENTAGE", 75.0, null, null))));
        assertEquals(400, invalid.getStatus());
        assertFalse(invalid.getMessage().isBlank());

        ApiException notAdmin = assertThrows(ApiException.class, () ->
                asOrganizer.createUser("Novo", TestApp.uniqueEmail("novo"), "senha123", "ORGANIZER"));
        assertEquals(403, notAdmin.getStatus());

        // logout invalida a sessão local
        asOrganizer.logout();
        assertThrows(ApiException.class, asOrganizer::listUsers);
    }

    @Test
    @DisplayName("administrador cria organizador, altera perfil e troca a própria senha; cadastro público cria participante")
    void adminUserManagement() {
        User admin = app.createUser("Administrador Desktop", UserRole.ADMIN);
        EventOsApiClient client = loginAs(admin);

        String email = TestApp.uniqueEmail("novo.organizador");
        UserView created = client.createUser("Novo Organizador", email, "senha123", "ORGANIZER");
        assertEquals("ORGANIZER", created.role());
        assertTrue(client.listUsers().stream().anyMatch(u -> u.email().equals(email)));

        UserView promoted = client.changeRole(created.id(), "ADMIN");
        assertEquals("ADMIN", promoted.role());

        UserView registered = new EventOsApiClient(app.baseUrl)
                .register("Visitante Novo", TestApp.uniqueEmail("visitante"), "senha123");
        assertEquals("PARTICIPANT", registered.role());

        ApiException duplicate = assertThrows(ApiException.class, () ->
                client.createUser("Duplicado", email, "senha123", "ORGANIZER"));
        assertEquals(400, duplicate.getStatus());

        client.changePassword(admin.getId(), TestApp.DEFAULT_PASSWORD, "novaSenha456");
        EventOsApiClient again = new EventOsApiClient(app.baseUrl);
        assertEquals("ADMIN", again.login(admin.getEmail().getValue(), "novaSenha456").role());
    }
}
