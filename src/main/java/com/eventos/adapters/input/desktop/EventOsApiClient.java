package com.eventos.adapters.input.desktop;

import com.eventos.application.dtos.AttendanceReportDto;
import com.eventos.application.dtos.AttendanceStatusDto;
import com.eventos.application.dtos.EnrolledReportDto;
import com.eventos.application.dtos.SurveyResultsDto;
import com.eventos.domain.model.Activity;
import com.eventos.domain.model.ActivityLocation;
import com.eventos.domain.model.ActivityType;
import com.eventos.domain.model.AttendanceType;
import com.eventos.domain.model.Event;
import com.eventos.domain.model.EventStatus;
import com.eventos.domain.model.FeedbackSurvey;
import com.eventos.domain.model.Period;
import com.eventos.domain.model.Speaker;
import com.eventos.domain.model.SurveyQuestion;
import com.eventos.domain.policies.AttendancePolicyFactory;
import com.eventos.domain.policies.MandatoryActivitiesPolicy;
import com.eventos.domain.policies.MinimumAttendancePercentagePolicy;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adaptador HTTP utilizado pelo desktop. A interface Swing não acessa casos de uso
 * ou repositórios diretamente: ela consome a mesma API REST do site público.
 */
public class EventOsApiClient {
    private final String baseUrl;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
    private String token;

    public EventOsApiClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public void login(String email, String password) {
        JsonNode response = sendJson("POST", "/api/auth/login",
                Map.of("email", email, "password", password));
        token = response.path("token").asText();
    }

    public List<Event> listEvents() {
        JsonNode response = send("GET", "/api/events", null);
        List<Event> events = new ArrayList<>();
        response.forEach(node -> events.add(mapEvent(node)));
        return events;
    }

    public Event createEvent(Event event) {
        return mapEvent(sendJson("POST", "/api/events", eventPayload(event)));
    }

    public Event publishEvent(Long id) {
        return mapEvent(send("POST", "/api/events/" + id + "/publish", "{}"));
    }

    public Event cancelEvent(Long id) {
        return mapEvent(send("POST", "/api/events/" + id + "/cancel", "{}"));
    }

    public Event finishEvent(Long id) {
        return mapEvent(send("POST", "/api/events/" + id + "/finish", "{}"));
    }

    public Activity addActivity(Long eventId, Activity activity) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", activity.getTitle());
        payload.put("description", activity.getDescription());
        payload.put("start", activity.getPeriod().getStart().toString());
        payload.put("end", activity.getPeriod().getEnd().toString());
        payload.put("room", activity.getLocation().getRoom());
        payload.put("track", activity.getLocation().getSpaceOrTrack());
        payload.put("capacity", activity.getMaxCapacity());
        payload.put("type", activity.getType().name());
        payload.put("attendancePolicy", activity.getAttendancePolicy().getPolicyKey());
        payload.put("requiresRegistration", activity.isRequiresRegistration());
        payload.put("speakers", activity.getSpeakers().stream().map(speaker -> Map.of(
                "name", speaker.getName(),
                "role", speaker.getRoleInActivity(),
                "bio", speaker.getBio(),
                "photoUrl", speaker.getPhotoUrl() == null ? "" : speaker.getPhotoUrl()
        )).toList());
        return mapActivity(sendJson("POST", "/api/events/" + eventId + "/activities", payload));
    }

    public List<Activity> listActivities(Long eventId) {
        JsonNode response = send("GET", "/api/events/" + eventId + "/activities", null);
        List<Activity> activities = new ArrayList<>();
        response.forEach(node -> activities.add(mapActivity(node)));
        return activities;
    }

    public Activity getActivity(Long activityId) {
        return mapActivity(send("GET", "/api/activities/" + activityId, null));
    }

    public String generateQrToken(Long activityId) {
        return send("GET", "/api/attendance/qr/generate/" + activityId, null)
                .path("qrToken").asText();
    }

    public void recordManualAttendance(Long activityId, Long userId, AttendanceType type, String notes) {
        sendJson("POST", "/api/attendance/manual", Map.of(
                "activityId", activityId,
                "userId", userId,
                "type", type.name(),
                "notes", notes
        ));
    }

    public List<AttendanceStatusDto> attendanceOverview(Long activityId) {
        JsonNode node = send("GET", "/api/attendance/activity/" + activityId + "/overview", null);
        return convert(node, new TypeReference<List<AttendanceStatusDto>>() {});
    }

    public void createSurvey(FeedbackSurvey survey) {
        List<Map<String, Object>> questions = survey.getQuestions().stream().map(this::questionPayload).toList();
        sendJson("POST", "/api/surveys", Map.of(
                "activityId", survey.getActivityId(),
                "title", survey.getTitle(),
                "questions", questions
        ));
    }

    public SurveyResultsDto surveyResults(Long activityId) {
        return convert(send("GET", "/api/surveys/activity/" + activityId + "/results", null),
                new TypeReference<SurveyResultsDto>() {});
    }

    public EnrolledReportDto enrolledReport(Long eventId) {
        return convert(send("GET", "/api/reports/enrolled/" + eventId, null),
                new TypeReference<EnrolledReportDto>() {});
    }

    public AttendanceReportDto attendanceReport(Long eventId) {
        return convert(send("GET", "/api/reports/attendance/" + eventId, null),
                new TypeReference<AttendanceReportDto>() {});
    }

    public byte[] enrolledCsv(Long eventId) {
        return sendBytes("/api/reports/enrolled/" + eventId + "/csv");
    }

    public byte[] enrolledPdf(Long eventId) {
        return sendBytes("/api/reports/enrolled/" + eventId + "/pdf");
    }

    public byte[] attendancePdf(Long eventId) {
        return sendBytes("/api/reports/attendance/" + eventId + "/pdf");
    }

    private Map<String, Object> eventPayload(Event event) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", event.getTitle());
        payload.put("description", event.getDescription());
        payload.put("start", event.getPeriod().getStart().toString());
        payload.put("end", event.getPeriod().getEnd().toString());
        payload.put("maxCapacity", event.getMaxCapacity());
        payload.put("activitySelectionEnabled", event.isActivitySelectionEnabled());
        payload.put("activitySelectionRequired", event.isActivitySelectionRequired());
        payload.put("registrationDeadline", event.getRegistrationDeadline().toString());
        payload.put("timeZone", event.getTimeZone());
        payload.put("certPolicyType", event.getCertificateEligibilityPolicy() instanceof MandatoryActivitiesPolicy
                ? "MANDATORY_COUNT" : "MIN_PERCENTAGE");
        payload.put("certPolicyParam", event.getCertificateEligibilityPolicy() instanceof MinimumAttendancePercentagePolicy p
                ? p.getMinPercentage() : 1);
        return payload;
    }

    private Map<String, Object> questionPayload(SurveyQuestion question) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("questionText", question.getQuestionText());
        payload.put("type", question.getType().name());
        payload.put("options", question.getOptions());
        payload.put("required", question.isRequired());
        return payload;
    }

    private Event mapEvent(JsonNode node) {
        JsonNode period = node.path("period");
        Event event = new Event(
                node.path("id").asLong(),
                node.path("title").asText(),
                node.path("description").asText(""),
                new Period(dateTime(period.path("start")), dateTime(period.path("end"))),
                EventStatus.valueOf(node.path("status").asText()),
                node.path("organizerId").isNull() ? null : node.path("organizerId").asLong(),
                new ArrayList<>(),
                node.path("maxCapacity").asInt(500),
                new MinimumAttendancePercentagePolicy(75));
        event.configureRegistration(
                node.path("activitySelectionEnabled").asBoolean(true),
                node.path("activitySelectionRequired").asBoolean(false),
                dateTime(node.path("registrationDeadline")),
                node.path("timeZone").asText("America/Sao_Paulo"));
        return event;
    }

    private Activity mapActivity(JsonNode node) {
        JsonNode period = node.path("period");
        JsonNode location = node.path("location");
        List<Speaker> speakers = new ArrayList<>();
        node.path("speakers").forEach(s -> speakers.add(new Speaker(
                s.path("id").isNull() ? null : s.path("id").asLong(),
                s.path("name").asText(),
                s.path("roleInActivity").asText(),
                s.path("bio").asText(""),
                s.path("photoUrl").isNull() ? null : s.path("photoUrl").asText())));
        String policyKey = node.path("attendancePolicy").path("policyKey").asText("SINGLE_CHECKIN");
        return new Activity(
                node.path("id").asLong(),
                node.path("eventId").asLong(),
                node.path("title").asText(),
                node.path("description").asText(""),
                new Period(dateTime(period.path("start")), dateTime(period.path("end"))),
                new ActivityLocation(location.path("room").asText(),
                        location.path("spaceOrTrack").asText("Geral"),
                        location.path("capacity").asInt(node.path("maxCapacity").asInt(50))),
                ActivityType.valueOf(node.path("type").asText()),
                node.path("maxCapacity").asInt(50),
                node.path("currentEnrollments").asInt(0),
                speakers,
                AttendancePolicyFactory.create(policyKey),
                node.path("requiresRegistration").asBoolean(true));
    }

    private LocalDateTime dateTime(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return LocalDateTime.now();
        if (node.isArray()) {
            return LocalDateTime.of(node.get(0).asInt(), node.get(1).asInt(), node.get(2).asInt(),
                    node.size() > 3 ? node.get(3).asInt() : 0,
                    node.size() > 4 ? node.get(4).asInt() : 0,
                    node.size() > 5 ? node.get(5).asInt() : 0);
        }
        return LocalDateTime.parse(node.asText());
    }

    private JsonNode sendJson(String method, String path, Object body) {
        try {
            return send(method, path, json.writeValueAsString(body));
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao serializar requisição.", e);
        }
    }

    private JsonNode send(String method, String path, String body) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .header("Accept", "application/json");
            if (token != null) request.header("Authorization", "Bearer " + token);
            if (body == null) {
                request.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                request.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(body));
            }
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                JsonNode error = json.readTree(response.body());
                throw new IllegalStateException(error.path("error").asText("Erro HTTP " + response.statusCode()));
            }
            return response.body().isBlank() ? json.createObjectNode() : json.readTree(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Requisição interrompida.", e);
        } catch (Exception e) {
            if (e instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("Falha de comunicação com a API: " + e.getMessage(), e);
        }
    }

    private byte[] sendBytes(String path) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .header("Authorization", "Bearer " + token).GET().build();
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Falha no download: HTTP " + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Download interrompido.", e);
        } catch (Exception e) {
            if (e instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("Falha no download: " + e.getMessage(), e);
        }
    }

    private <T> T convert(JsonNode node, TypeReference<T> type) {
        try {
            return json.convertValue(node, type);
        } catch (Exception e) {
            throw new IllegalStateException("Resposta inválida da API.", e);
        }
    }
}
