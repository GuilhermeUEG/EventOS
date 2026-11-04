package com.eventos.adapters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eventos.domain.model.Event;
import com.eventos.domain.model.User;
import com.eventos.domain.model.UserRole;
import com.eventos.support.ApiTestClient;
import com.eventos.support.ApiTestClient.Response;
import com.eventos.support.Fixtures;
import com.eventos.support.TestApp;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("API REST: autenticação, autorização no servidor e tratamento de erros (RNF-06, RNF-07, RN-04, RN-17)")
class ApiSecurityTest {
    private static TestApp app;
    private static ApiTestClient api;
    private static User admin;
    private static User ownerOrg;
    private static User otherOrg;
    private static User participant;
    private static String adminToken;
    private static String ownerToken;
    private static String otherToken;
    private static String participantToken;
    private static Event draft;
    private static Event published;
    private static long publishedActivityId;

    @BeforeAll
    static void start() {
        app = TestApp.start();
        api = new ApiTestClient(app.baseUrl);
        admin = app.createUser("Admin API", UserRole.ADMIN);
        ownerOrg = app.createUser("Dono API", UserRole.ORGANIZER);
        otherOrg = app.createUser("Outro API", UserRole.ORGANIZER);
        participant = app.createUser("Participante API", UserRole.PARTICIPANT);
        adminToken = api.login(admin.getEmail().getValue(), TestApp.DEFAULT_PASSWORD);
        ownerToken = api.login(ownerOrg.getEmail().getValue(), TestApp.DEFAULT_PASSWORD);
        otherToken = api.login(otherOrg.getEmail().getValue(), TestApp.DEFAULT_PASSWORD);
        participantToken = api.login(participant.getEmail().getValue(), TestApp.DEFAULT_PASSWORD);

        draft = Fixtures.newEvent(app, ownerOrg, 50);
        published = Fixtures.newEvent(app, ownerOrg, 50);
        publishedActivityId = Fixtures.addActivity(app, published, "Palestra", "Sala 1", 9, 2).getId();
        Fixtures.publish(app, published);
    }

    @AfterAll
    static void stop() {
        app.close();
    }

    private List<Long> eventIds(Response response) {
        List<Long> ids = new ArrayList<>();
        response.json().forEach(node -> ids.add(node.path("id").asLong()));
        return ids;
    }

    @Test
    @DisplayName("rotas protegidas respondem 401 sem sessão e 403 com sessão sem permissão")
    void unauthorizedVersusForbidden() {
        assertEquals(401, api.get("/api/users", null).status());
        assertEquals(401, api.get("/api/users", "token-inventado").status());
        assertEquals(401, api.post("/api/registrations", null, Map.of("eventId", published.getId())).status());

        assertEquals(403, api.get("/api/users", participantToken).status());
        assertEquals(403, api.get("/api/users", ownerToken).status(), "a lista completa de usuários é só do administrador");
        assertEquals(200, api.get("/api/users", adminToken).status());
        assertEquals(403, api.post("/api/events", participantToken, Map.of("title", "x")).status());
        assertEquals(403, api.post("/api/users", ownerToken,
                Map.of("name", "Novo", "email", TestApp.uniqueEmail("n"), "password", "senha123", "role", "ORGANIZER")).status());
        assertEquals(403, api.get("/api/reports/enrolled/" + published.getId(), participantToken).status());
        assertEquals(403, api.get("/api/attendance/qr/generate/" + publishedActivityId, participantToken).status());
        assertTrue(api.get("/api/users", participantToken).error().contains("permissão"));
    }

    @Test
    @DisplayName("listagem de eventos: paginação com total no cabeçalho, busca por nome e filtro 'meus eventos'")
    void eventListingPagesSearchesAndFiltersMine() {
        for (int i = 1; i <= 3; i++) {
            assertEquals(201, api.post("/api/events", ownerToken, Map.of("title", "Busca Zeta " + i, "description", "paginação",
                    "start", "2027-03-0" + i + "T08:00:00", "end", "2027-03-0" + i + "T18:00:00")).status());
        }
        Response firstPage = api.get("/api/events?search=busca%20zeta&limit=2", ownerToken);
        assertEquals(200, firstPage.status());
        assertEquals(2, firstPage.json().size());
        assertEquals("3", firstPage.raw().headers().firstValue("X-Total-Count").orElse(""), "o total independe da página");

        Response secondPage = api.get("/api/events?search=busca%20zeta&limit=2&offset=2", ownerToken);
        assertEquals(1, secondPage.json().size());
        assertTrue(firstPage.json().findValuesAsText("title").stream().noneMatch(t -> t.equals(secondPage.json().get(0).path("title").asText())),
                "a segunda página não repete a primeira");

        Response none = api.get("/api/events?search=nenhum-evento-assim&limit=5", ownerToken);
        assertEquals(0, none.json().size());
        assertEquals("0", none.raw().headers().firstValue("X-Total-Count").orElse(""));

        Response mine = api.get("/api/events?mine=true", ownerToken);
        assertTrue(mine.json().size() >= 3);
        mine.json().forEach(node -> assertEquals(ownerOrg.getId().longValue(), node.path("organizerId").asLong()));
        assertTrue(api.get("/api/events?mine=true&search=busca%20zeta", otherToken).json().isEmpty(),
                "o filtro 'meus eventos' não traz eventos de outro organizador");

        assertEquals(400, api.get("/api/events?limit=abc", ownerToken).status());
        assertEquals(400, api.get("/api/events?offset=-1", ownerToken).status());
    }

    @Test
    @DisplayName("organizador só gerencia os próprios eventos; administrador gerencia todos (RN-17)")
    void ownershipIsEnforced() {
        assertEquals(403, api.post("/api/events/" + published.getId() + "/cancel", otherToken, null).status());
        assertEquals(403, api.get("/api/reports/enrolled/" + published.getId(), otherToken).status());
        assertEquals(403, api.get("/api/attendance/qr/generate/" + publishedActivityId, otherToken).status());
        assertEquals(403, api.put("/api/events/" + published.getId(), otherToken, Map.of("title", "Sequestrado")).status());

        assertEquals(200, api.get("/api/reports/enrolled/" + published.getId(), ownerToken).status());
        assertEquals(200, api.get("/api/reports/enrolled/" + published.getId(), adminToken).status());
        assertEquals(200, api.get("/api/attendance/qr/generate/" + publishedActivityId, ownerToken).status());
    }

    @Test
    @DisplayName("visitantes não veem rascunhos nem por filtro de status (RN-04)")
    void draftsAreHiddenFromVisitors() {
        assertFalse(eventIds(api.get("/api/events", null)).contains(draft.getId()));
        Response forcedStatus = api.get("/api/events?status=DRAFT", null);
        assertEquals(200, forcedStatus.status());
        assertFalse(eventIds(forcedStatus).contains(draft.getId()), "?status=DRAFT não pode vazar rascunhos");
        assertFalse(eventIds(api.get("/api/events?status=DRAFT", participantToken)).contains(draft.getId()));
        assertTrue(eventIds(api.get("/api/events", null)).contains(published.getId()));

        assertEquals(401, api.get("/api/events/" + draft.getId(), null).status());
        assertEquals(403, api.get("/api/events/" + draft.getId(), otherToken).status());
        assertEquals(200, api.get("/api/events/" + draft.getId(), ownerToken).status());
        assertEquals(200, api.get("/api/events/" + draft.getId(), adminToken).status());
        assertEquals(401, api.get("/api/events/" + draft.getId() + "/activities", null).status());
    }

    @Test
    @DisplayName("organizador vê seus rascunhos na listagem, mas não os de outro organizador")
    void organizerListsOnlyOwnDrafts() {
        assertTrue(eventIds(api.get("/api/events", ownerToken)).contains(draft.getId()));
        assertFalse(eventIds(api.get("/api/events", otherToken)).contains(draft.getId()));
        assertTrue(eventIds(api.get("/api/events", adminToken)).contains(draft.getId()));
    }

    @Test
    @DisplayName("o cadastro público sempre cria participante, mesmo se o cliente pedir outro perfil")
    void publicRegistrationCannotEscalate() {
        Response r = api.post("/api/auth/register", null, Map.of("name", "Esperto", "email", TestApp.uniqueEmail("esp"),
                "password", "senha123", "role", "ADMIN"));
        assertEquals(201, r.status());
        assertEquals("PARTICIPANT", r.json().path("user").path("role").asText());
        assertNotNull(r.json().path("token").asText(null));
        assertNull(r.json().path("user").path("passwordHash").textValue(), "hash nunca é exposto");
    }

    @Test
    @DisplayName("administrador cria organizador e altera perfis pela API; os demais não conseguem")
    void adminManagesUsers() {
        String email = TestApp.uniqueEmail("novoorg");
        Response created = api.post("/api/users", adminToken, Map.of("name", "Novo Org", "email", email, "password", "senha123", "role", "organizer"));
        assertEquals(201, created.status());
        assertEquals("ORGANIZER", created.json().path("role").asText());
        long id = created.json().path("id").asLong();

        assertEquals(403, api.put("/api/users/" + id + "/role", ownerToken, Map.of("role", "ADMIN")).status());
        Response changed = api.put("/api/users/" + id + "/role", adminToken, Map.of("role", "ADMIN"));
        assertEquals(200, changed.status());
        assertEquals("ADMIN", changed.json().path("role").asText());
        assertEquals(400, api.put("/api/users/" + id + "/role", adminToken, Map.of("role", "SUPER")).status());
        assertTrue(api.put("/api/users/" + id + "/role", adminToken, Map.of("role", "SUPER")).error().contains("Valores aceitos"));
    }

    @Test
    @DisplayName("participante só acessa os próprios dados; equipe acessa os de qualquer um")
    void selfOrStaff() {
        long mine = participant.getId();
        assertEquals(200, api.get("/api/users/" + mine, participantToken).status());
        assertEquals(403, api.get("/api/users/" + ownerOrg.getId(), participantToken).status());
        assertEquals(403, api.get("/api/registrations/user/" + ownerOrg.getId(), participantToken).status());
        assertEquals(403, api.put("/api/users/" + ownerOrg.getId(), participantToken, Map.of("name", "Hackeado")).status());
        assertEquals(200, api.get("/api/users/" + mine, ownerToken).status());
        assertEquals(403, api.put("/api/users/" + mine + "/password", ownerToken,
                Map.of("currentPassword", TestApp.DEFAULT_PASSWORD, "newPassword", "novasenha")).status(), "ninguém troca senha de terceiros");
    }

    @Test
    @DisplayName("a identidade do participante vem da sessão, nunca do corpo da requisição")
    void identityComesFromSession() {
        User victim = Fixtures.participant(app, "Vítima");
        Response r = api.post("/api/registrations", participantToken,
                Map.of("eventId", published.getId(), "userId", victim.getId(), "activityIds", List.of(publishedActivityId)));
        assertEquals(201, r.status());
        assertEquals(participant.getId(), r.json().path("userId").asLong());
        assertTrue(app.registrations.findByEventAndUser(published.getId(), victim.getId()).isEmpty());
    }

    @Test
    @DisplayName("erros de entrada viram 400/404/409 com mensagem legível, nunca 500")
    void errorMapping() {
        assertEquals(400, api.postRaw("/api/auth/login", null, "{isto não é json").status());
        assertEquals(400, api.postRaw("/api/auth/login", null, "").status());
        assertEquals(400, api.post("/api/auth/login", null, Map.of("email", 123, "password", List.of("x"))).status());
        assertEquals(401, api.post("/api/auth/login", null, Map.of("email", "ninguem@teste.com", "password", "qualquer")).status());
        assertEquals(400, api.post("/api/auth/register", null, Map.of("name", "A", "email", "invalido", "password", "senha123")).status());
        assertEquals(400, api.get("/api/events/abc", ownerToken).status());
        assertEquals(404, api.get("/api/events/987654321", ownerToken).status());
        assertEquals(404, api.get("/api/activities/987654321", null).status());
        assertEquals(400, api.get("/api/events?type=INEXISTENTE", null).status());
        assertEquals(400, api.get("/api/events/" + published.getId() + "/activities?date=ontem", null).status());
        assertEquals(400, api.post("/api/events", ownerToken, Map.of("title", "Sem datas")).status());

        Response bad = api.post("/api/events", ownerToken, Map.of("title", "Datas ruins", "start", "amanhã", "end", "depois"));
        assertEquals(400, bad.status());
        assertTrue(bad.error().contains("Data/hora inválida"));
        assertFalse(bad.body().contains("Exception"), "não vaza detalhes internos");
    }

    @Test
    @DisplayName("conflito de sala é 409 e a atividade não é criada")
    void roomConflict() {
        Map<String, Object> body = Map.of("title", "Conflitante", "start", Fixtures.firstDay().withHour(9).plusMinutes(30).toString(),
                "end", Fixtures.firstDay().withHour(10).toString(), "room", "Sala 1", "track", "T", "capacity", 10);
        Response r = api.post("/api/events/" + published.getId() + "/activities", ownerToken, body);
        assertEquals(409, r.status());
        assertTrue(r.error().contains("Sala 1"));
    }

    @Test
    @DisplayName("declaração de palestrante só é emitida pelo responsável pelo evento e devolve um PDF")
    void speakerDeclarationEndpoint() {
        Map<String, Object> body = Map.of("title", "Com palestrante", "start", Fixtures.firstDay().withHour(14).toString(),
                "end", Fixtures.firstDay().withHour(15).toString(), "room", "Sala 9", "track", "T", "capacity", 10,
                "speakers", List.of(Map.of("name", "Dr. Exemplo", "role", "Palestrante convidado")));
        Response created = api.post("/api/events/" + published.getId() + "/activities", ownerToken, body);
        assertEquals(201, created.status(), created.body());
        long activityId = created.json().path("id").asLong();
        long speakerId = created.json().path("speakers").get(0).path("id").asLong();
        String path = "/api/activities/" + activityId + "/speakers/" + speakerId + "/declaration";

        assertEquals(401, api.get(path, null).status());
        assertEquals(403, api.get(path, participantToken).status());
        assertEquals(403, api.get(path, otherToken).status());
        Response pdf = api.get(path, ownerToken);
        assertEquals(200, pdf.status());
        assertTrue(pdf.raw().headers().firstValue("Content-Type").orElse("").startsWith("application/pdf"));
        assertTrue(pdf.body().startsWith("%PDF"));
        assertEquals(404, api.get("/api/activities/" + activityId + "/speakers/123456789/declaration", ownerToken).status());
    }

    @Test
    @DisplayName("envio do certificado por e-mail: só o titular ou a equipe; falha de entrega é resultado, não erro (RF-34)")
    void certificateEmailEndpoint() {
        Event event = Fixtures.newEvent(app, ownerOrg, 20);
        var activity = Fixtures.addActivity(app, event, "Atividade do envio", "Sala E", 9, 1);
        Fixtures.publish(app, event);
        app.registrationUseCase.registerForEvent(event.getId(), participant.getId(), java.util.Set.of(activity.getId()));
        app.attendanceUseCase.recordManualAttendance(activity.getId(), participant.getId(), ownerOrg.getId(), null, "Conferido");
        long certificateId = app.certificateUseCase.issueCertificateIfEligible(event.getId(), participant.getId()).getId();
        String path = "/api/certificates/" + certificateId;

        assertEquals(401, api.post(path + "/send", null, null).status());
        assertEquals(403, api.post(path + "/send", otherParticipantToken(), null).status(), "outro participante não envia certificado alheio");
        assertEquals(404, api.post("/api/certificates/987654321/send", participantToken, null).status());

        app.emailSender.failWith("Servidor SMTP indisponível");
        Response failed = api.post(path + "/send", participantToken, null);
        assertEquals(200, failed.status(), "falha de entrega não é erro da requisição");
        assertEquals("FAILED", failed.json().path("status").asText());
        assertTrue(failed.json().path("detail").asText().contains("indisponível"));
        assertEquals(200, api.get("/api/certificates/download/" + certificateId, participantToken).status(), "o download segue disponível");

        app.emailSender.failWith(null);
        Response sent = api.post(path + "/send", ownerToken, null);
        assertEquals(200, sent.status());
        assertEquals("SENT", sent.json().path("status").asText());
        assertEquals(participant.getEmail().getValue(), sent.json().path("recipient").asText());

        Response history = api.get(path + "/deliveries", participantToken);
        assertEquals(200, history.status());
        assertEquals(2, history.json().size());
        assertEquals("SENT", history.json().get(0).path("status").asText(), "mais recente primeiro");
        assertEquals(403, api.get(path + "/deliveries", otherParticipantToken()).status());
    }

    private String otherParticipantToken() {
        String email = TestApp.uniqueEmail("intruso");
        api.post("/api/auth/register", null, Map.of("name", "Intruso", "email", email, "password", "senha123"));
        return api.login(email, "senha123");
    }

    @Test
    @DisplayName("respostas de erro e sucesso levam cabeçalhos de segurança")
    void securityHeaders() {
        Response r = api.get("/api/events", null);
        assertEquals("nosniff", r.raw().headers().firstValue("X-Content-Type-Options").orElse(null));
        assertEquals("no-store", r.raw().headers().firstValue("Cache-Control").orElse(null));
    }

    @Test
    @DisplayName("sair da sessão invalida o token")
    void logoutRevokesToken() {
        String email = TestApp.uniqueEmail("saida");
        api.post("/api/auth/register", null, Map.of("name", "Sai", "email", email, "password", "senha123"));
        String token = api.login(email, "senha123");
        assertEquals(200, api.get("/api/auth/me", token).status());
        assertEquals(204, api.post("/api/auth/logout", token, null).status());
        assertEquals(401, api.get("/api/auth/me", token).status());
    }

    @Test
    @DisplayName("troca de senha pela API exige a senha atual")
    void passwordChange() {
        String email = TestApp.uniqueEmail("troca");
        JsonNode user = api.post("/api/auth/register", null, Map.of("name", "Troca", "email", email, "password", "senha123")).json();
        String token = user.path("token").asText();
        long id = user.path("user").path("id").asLong();
        assertEquals(400, api.put("/api/users/" + id + "/password", token, Map.of("currentPassword", "errada", "newPassword", "novasenha")).status(),
                "senha atual errada é erro do pedido, não sessão inválida");
        assertEquals(200, api.get("/api/auth/me", token).status(), "a sessão continua válida");
        assertEquals(400, api.put("/api/users/" + id + "/password", token, Map.of("currentPassword", "senha123", "newPassword", "123")).status());
        assertEquals(200, api.put("/api/users/" + id + "/password", token, Map.of("currentPassword", "senha123", "newPassword", "novasenha")).status());
        assertEquals(401, api.post("/api/auth/login", null, Map.of("email", email, "password", "senha123")).status());
        assertEquals(200, api.post("/api/auth/login", null, Map.of("email", email, "password", "novasenha")).status());
    }
}
