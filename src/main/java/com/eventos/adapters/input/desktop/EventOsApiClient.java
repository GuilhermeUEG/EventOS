package com.eventos.adapters.input.desktop;

import com.eventos.adapters.input.desktop.DesktopModels.*;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cliente HTTP da API REST usado pelo desktop. Consome exatamente o mesmo contrato do site público e
 * nunca acessa casos de uso, domínio ou banco. Erros da API viram {@link ApiException} com a mensagem do servidor.
 */
public class EventOsApiClient {
    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private volatile String token;
    private volatile UserView currentUser;

    public EventOsApiClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public UserView currentUser() {
        return currentUser;
    }

    // ------------------------------------------------------------------ autenticação e usuários

    public UserView login(String email, String password) {
        JsonNode response = send("POST", "/api/auth/login", Map.of("email", email, "password", password));
        token = response.path("token").textValue();
        currentUser = read(response.path("user"), UserView.class);
        return currentUser;
    }

    /** Cadastro público: sempre cria um participante e não inicia sessão no desktop. */
    public UserView register(String name, String email, String password) {
        JsonNode response = send("POST", "/api/auth/register", Map.of("name", name, "email", email, "password", password));
        return read(response.path("user"), UserView.class);
    }

    public void logout() {
        try {
            if (token != null) send("POST", "/api/auth/logout", null);
        } finally {
            token = null;
            currentUser = null;
        }
    }

    public void changePassword(Long userId, String currentPassword, String newPassword) {
        send("PUT", "/api/users/" + userId + "/password",
                Map.of("currentPassword", currentPassword, "newPassword", newPassword));
    }

    public List<UserView> listUsers() {
        return readList(send("GET", "/api/users", null), UserView.class);
    }

    public UserView createUser(String name, String email, String password, String role) {
        return read(send("POST", "/api/users",
                Map.of("name", name, "email", email, "password", password, "role", role)), UserView.class);
    }

    public UserView changeRole(Long userId, String role) {
        return read(send("PUT", "/api/users/" + userId + "/role", Map.of("role", role)), UserView.class);
    }

    // ------------------------------------------------------------------ eventos e atividades

    public List<EventView> listEvents() {
        return readList(send("GET", "/api/events", null), EventView.class);
    }

    public EventView createEvent(EventForm form) {
        Map<String, Object> payload = eventPayload(form);
        payload.put("certificatePolicy", policyPayload(form.policy()));
        return read(send("POST", "/api/events", payload), EventView.class);
    }

    public EventView updateEvent(Long eventId, EventForm form) {
        return read(send("PUT", "/api/events/" + eventId, eventPayload(form)), EventView.class);
    }

    public EventView publishEvent(Long id) {
        return read(send("POST", "/api/events/" + id + "/publish", Map.of()), EventView.class);
    }

    public EventView cancelEvent(Long id) {
        return read(send("POST", "/api/events/" + id + "/cancel", Map.of()), EventView.class);
    }

    public EventView finishEvent(Long id) {
        return read(send("POST", "/api/events/" + id + "/finish", Map.of()), EventView.class);
    }

    public EventView changeCertificatePolicy(Long eventId, PolicyForm policy) {
        return read(send("PUT", "/api/events/" + eventId + "/certificate-policy", policyPayload(policy)), EventView.class);
    }

    public ActivityView addActivity(Long eventId, ActivityForm form) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", form.title());
        payload.put("description", form.description());
        payload.put("start", form.start());
        payload.put("end", form.end());
        payload.put("room", form.room());
        payload.put("track", form.track());
        payload.put("capacity", form.capacity());
        payload.put("type", form.type());
        payload.put("attendancePolicy", form.attendancePolicy());
        payload.put("requiresRegistration", form.requiresRegistration());
        List<Map<String, Object>> speakers = new ArrayList<>();
        for (SpeakerForm s : form.speakers()) {
            speakers.add(Map.of("name", s.name(), "role", s.role() == null ? "" : s.role(),
                    "bio", s.bio() == null ? "" : s.bio()));
        }
        payload.put("speakers", speakers);
        return read(send("POST", "/api/events/" + eventId + "/activities", payload), ActivityView.class);
    }

    public List<ActivityView> listActivities(Long eventId) {
        return listActivities(eventId, ActivityQuery.none());
    }

    /** Programação do evento com filtros combináveis aplicados pelo servidor. */
    public List<ActivityView> listActivities(Long eventId, ActivityQuery query) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("date", query.date());
        params.put("from", query.from());
        params.put("to", query.to());
        params.put("track", query.track());
        params.put("type", query.type());
        params.put("room", query.room());
        params.put("search", query.search());
        return readList(send("GET", "/api/events/" + eventId + "/activities" + queryString(params), null), ActivityView.class);
    }

    // ------------------------------------------------------------------ frequência

    public QrCodeView generateQrCode(Long activityId) {
        return read(send("GET", "/api/attendance/qr/generate/" + activityId, null), QrCodeView.class);
    }

    public List<AttendanceOverviewRow> attendanceOverview(Long activityId) {
        return readList(send("GET", "/api/attendance/activity/" + activityId + "/overview", null), AttendanceOverviewRow.class);
    }

    public List<AttendanceRecordView> attendanceRecords(Long activityId) {
        return readList(send("GET", "/api/attendance/activity/" + activityId, null), AttendanceRecordView.class);
    }

    public AttendanceRecordView recordManualAttendance(Long activityId, Long userId, String type, String notes) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("activityId", activityId);
        payload.put("userId", userId);
        payload.put("type", type);
        payload.put("notes", notes);
        return read(send("POST", "/api/attendance/manual", payload), AttendanceRecordView.class);
    }

    public AttendanceRecordView revokeAttendance(Long activityId, Long userId, String notes) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("activityId", activityId);
        payload.put("userId", userId);
        payload.put("notes", notes);
        return read(send("POST", "/api/attendance/revoke", payload), AttendanceRecordView.class);
    }

    // ------------------------------------------------------------------ avaliações

    public SurveyView createSurvey(Long activityId, SurveyForm form) {
        List<Map<String, Object>> questions = new ArrayList<>();
        for (QuestionForm q : form.questions()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("questionText", q.text());
            item.put("type", q.type());
            item.put("options", q.options());
            item.put("required", q.required());
            questions.add(item);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("activityId", activityId);
        payload.put("title", form.title());
        payload.put("anonymous", form.anonymous());
        payload.put("questions", questions);
        return read(send("POST", "/api/surveys", payload), SurveyView.class);
    }

    public SurveyResultsView surveyResults(Long activityId) {
        return read(send("GET", "/api/surveys/activity/" + activityId + "/results", null), SurveyResultsView.class);
    }

    // ------------------------------------------------------------------ relatórios e certificados

    public EnrolledReportView enrolledReport(Long eventId) {
        return read(send("GET", "/api/reports/enrolled/" + eventId, null), EnrolledReportView.class);
    }

    public AttendanceReportView attendanceReport(Long eventId) {
        return read(send("GET", "/api/reports/attendance/" + eventId, null), AttendanceReportView.class);
    }

    public byte[] enrolledCsv(Long eventId) {
        return download("/api/reports/enrolled/" + eventId + "/csv");
    }

    public byte[] attendanceCsv(Long eventId) {
        return download("/api/reports/attendance/" + eventId + "/csv");
    }

    public byte[] enrolledPdf(Long eventId) {
        return download("/api/reports/enrolled/" + eventId + "/pdf");
    }

    /** Declaração em PDF para palestrante/apresentador (RF-35). */
    public byte[] speakerDeclarationPdf(Long activityId, Long speakerId) {
        return download("/api/activities/" + activityId + "/speakers/" + speakerId + "/declaration");
    }

    public byte[] attendancePdf(Long eventId) {
        return download("/api/reports/attendance/" + eventId + "/pdf");
    }

    public EligibilityView certificateEligibility(Long eventId, Long userId) {
        return read(send("GET", "/api/certificates/eligibility/" + eventId + "/" + userId, null), EligibilityView.class);
    }

    // ------------------------------------------------------------------ montagem de requisições

    private Map<String, Object> eventPayload(EventForm form) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("title", form.title());
        payload.put("description", form.description());
        payload.put("start", form.start());
        payload.put("end", form.end());
        payload.put("maxCapacity", form.maxCapacity());
        payload.put("activitySelectionEnabled", form.selectionEnabled());
        payload.put("activitySelectionRequired", form.selectionRequired());
        if (form.registrationDeadline() != null && !form.registrationDeadline().isBlank()) {
            payload.put("registrationDeadline", form.registrationDeadline());
        }
        payload.put("timeZone", form.timeZone());
        return payload;
    }

    private Map<String, Object> policyPayload(PolicyForm policy) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", policy.type());
        if (policy.percentage() != null) payload.put("percentage", policy.percentage());
        if (policy.minActivities() != null) payload.put("minActivities", policy.minActivities());
        if (policy.activityIds() != null) payload.put("activityIds", policy.activityIds());
        return payload;
    }

    private String queryString(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        params.forEach((key, value) -> {
            if (value == null || value.isBlank()) return;
            sb.append(sb.length() == 0 ? '?' : '&').append(key).append('=')
                    .append(URLEncoder.encode(value.trim(), StandardCharsets.UTF_8));
        });
        return sb.toString();
    }

    // ------------------------------------------------------------------ transporte

    private JsonNode send(String method, String path, Object body) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/json");
            if (token != null) request.header("Authorization", "Bearer " + token);
            if (body == null) {
                request.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                request.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            }
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ApiException(response.statusCode(), errorMessage(response.statusCode(), response.body()));
            }
            return response.body() == null || response.body().isBlank() ? json.createObjectNode() : json.readTree(response.body());
        } catch (ApiException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException("Requisição interrompida.", e);
        } catch (Exception e) {
            throw new ApiException("Não foi possível se comunicar com o servidor: " + e.getMessage(), e);
        }
    }

    private byte[] download(String path) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(60));
            if (token != null) request.header("Authorization", "Bearer " + token);
            HttpResponse<byte[]> response = http.send(request.GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ApiException(response.statusCode(),
                        errorMessage(response.statusCode(), new String(response.body(), StandardCharsets.UTF_8)));
            }
            return response.body();
        } catch (ApiException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException("Download interrompido.", e);
        } catch (Exception e) {
            throw new ApiException("Falha no download: " + e.getMessage(), e);
        }
    }

    private String errorMessage(int status, String body) {
        try {
            String message = json.readTree(body).path("error").asText();
            if (!message.isBlank()) return message;
        } catch (Exception ignored) {
            // corpo não é JSON: usa a mensagem genérica abaixo
        }
        return "Erro HTTP " + status + " ao processar a requisição.";
    }

    private <T> T read(JsonNode node, Class<T> type) {
        try {
            return json.treeToValue(node, type);
        } catch (Exception e) {
            throw new ApiException("Resposta inválida da API.", e);
        }
    }

    private <T> List<T> readList(JsonNode node, Class<T> type) {
        List<T> result = new ArrayList<>();
        node.forEach(item -> result.add(read(item, type)));
        return result;
    }
}
