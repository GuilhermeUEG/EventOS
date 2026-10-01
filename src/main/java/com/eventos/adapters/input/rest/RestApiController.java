package com.eventos.adapters.input.rest;

import com.eventos.application.auth.AuthenticatedSession;
import com.eventos.application.dtos.AttendanceReportDto;
import com.eventos.application.dtos.EnrolledReportDto;
import com.eventos.application.dtos.SurveyResultsDto;
import com.eventos.application.ports.input.AttendanceUseCase;
import com.eventos.application.ports.input.AuthUseCase;
import com.eventos.application.ports.input.CertificateUseCase;
import com.eventos.application.ports.input.EventUseCase;
import com.eventos.application.ports.input.RegistrationUseCase;
import com.eventos.application.ports.input.ReportUseCase;
import com.eventos.application.ports.input.SurveyUseCase;
import com.eventos.application.ports.output.SessionStore;
import com.eventos.domain.exceptions.DomainException;
import com.eventos.domain.exceptions.EntityNotFoundException;
import com.eventos.domain.exceptions.UnauthorizedException;
import com.eventos.domain.model.Activity;
import com.eventos.domain.model.ActivityLocation;
import com.eventos.domain.model.ActivityType;
import com.eventos.domain.model.AttendanceRecord;
import com.eventos.domain.model.AttendanceType;
import com.eventos.domain.model.Certificate;
import com.eventos.domain.model.Event;
import com.eventos.domain.model.EventStatus;
import com.eventos.domain.model.FeedbackSurvey;
import com.eventos.domain.model.Period;
import com.eventos.domain.model.QuestionType;
import com.eventos.domain.model.Registration;
import com.eventos.domain.model.Speaker;
import com.eventos.domain.model.SurveyQuestion;
import com.eventos.domain.model.SurveyResponse;
import com.eventos.domain.model.User;
import com.eventos.domain.model.UserRole;
import com.eventos.domain.policies.AttendancePolicyFactory;
import com.eventos.domain.policies.CertificateEligibilityPolicy;
import com.eventos.domain.policies.MandatoryActivitiesPolicy;
import com.eventos.domain.policies.MinimumAttendancePercentagePolicy;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Adaptador de Entrada REST (Javalin) que expõe os casos de uso para o site público e clientes externos (RNF-02, ROO-09).
 */
public class RestApiController {
    private final AuthUseCase authUseCase;
    private final EventUseCase eventUseCase;
    private final RegistrationUseCase registrationUseCase;
    private final AttendanceUseCase attendanceUseCase;
    private final SurveyUseCase surveyUseCase;
    private final CertificateUseCase certificateUseCase;
    private final ReportUseCase reportUseCase;
    private final SessionStore sessionStore;
    private final AuthorizationHandler authorization;

    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ISO_DATE_TIME;

    public RestApiController(AuthUseCase authUseCase,
                             EventUseCase eventUseCase,
                             RegistrationUseCase registrationUseCase,
                             AttendanceUseCase attendanceUseCase,
                             SurveyUseCase surveyUseCase,
                             CertificateUseCase certificateUseCase,
                             ReportUseCase reportUseCase,
                             SessionStore sessionStore,
                             Javalin app) {
        this.authUseCase = authUseCase;
        this.eventUseCase = eventUseCase;
        this.registrationUseCase = registrationUseCase;
        this.attendanceUseCase = attendanceUseCase;
        this.surveyUseCase = surveyUseCase;
        this.certificateUseCase = certificateUseCase;
        this.reportUseCase = reportUseCase;
        this.sessionStore = sessionStore;
        this.authorization = new AuthorizationHandler(sessionStore);

        setupRoutes(app);
    }

    private void setupRoutes(Javalin app) {
        // Exception Mapping
        app.exception(DomainException.class, (e, ctx) -> {
            ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", e.getMessage()));
        });
        app.exception(EntityNotFoundException.class, (e, ctx) -> {
            ctx.status(HttpStatus.NOT_FOUND).json(Map.of("error", e.getMessage()));
        });
        app.exception(UnauthorizedException.class, (e, ctx) -> {
            ctx.status(HttpStatus.UNAUTHORIZED).json(Map.of("error", e.getMessage()));
        });
        app.exception(Exception.class, (e, ctx) -> {
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(Map.of("error", "Erro interno: " + e.getMessage()));
        });

        // 1. Auth & User
        app.post("/api/auth/register", this::register);
        app.post("/api/auth/login", this::login);
        app.post("/api/auth/logout", this::logout);
        app.get("/api/users", this::listUsers);
        app.get("/api/users/{id}", this::getUser);
        app.put("/api/users/{id}", this::updateUser);

        // 2. Events & Activities
        app.get("/api/events", this::listEvents);
        app.get("/api/events/{id}", this::getEvent);
        app.post("/api/events", this::createEvent);
        app.put("/api/events/{id}", this::updateEvent);
        app.post("/api/events/{id}/publish", this::publishEvent);
        app.post("/api/events/{id}/cancel", this::cancelEvent);
        app.post("/api/events/{id}/finish", this::finishEvent);
        app.get("/api/events/{id}/activities", this::listActivities);
        app.post("/api/events/{id}/activities", this::addActivity);
        app.get("/api/activities/{id}", this::getActivity);

        // 3. Registrations & Agenda
        app.post("/api/registrations", this::registerForEvent);
        app.post("/api/registrations/{eventId}/activities/{activityId}", this::addActivityToRegistration);
        app.delete("/api/registrations/{eventId}/activities/{activityId}", this::removeActivityFromRegistration);
        app.post("/api/registrations/{eventId}/cancel", this::cancelRegistration);
        app.get("/api/registrations/event/{eventId}", this::getEventRegistrations);
        app.get("/api/registrations/user/{userId}", this::getUserRegistrations);
        app.get("/api/registrations/agenda/{userId}/{eventId}", this::getParticipantAgenda);

        // 4. Attendance (QR & Manual)
        app.get("/api/attendance/qr/generate/{activityId}", this::generateQrToken);
        app.post("/api/attendance/qr/scan", this::recordQrAttendance);
        app.post("/api/attendance/manual", this::recordManualAttendance);
        app.get("/api/attendance/activity/{activityId}", this::getActivityAttendanceRecords);
        app.get("/api/attendance/activity/{activityId}/overview", this::getActivityAttendanceOverview);

        // 5. Surveys & Feedback
        app.post("/api/surveys", this::createSurvey);
        app.get("/api/surveys/activity/{activityId}", this::getSurvey);
        app.get("/api/surveys/activity/{activityId}/can-evaluate/{userId}", this::canEvaluate);
        app.post("/api/surveys/submit", this::submitSurvey);
        app.get("/api/surveys/activity/{activityId}/results", this::getSurveyResults);

        // 6. Reports & Certificates
        app.get("/api/reports/enrolled/{eventId}", this::getEnrolledReport);
        app.get("/api/reports/enrolled/{eventId}/csv", this::getEnrolledReportCsv);
        app.get("/api/reports/enrolled/{eventId}/pdf", this::getEnrolledReportPdf);
        app.get("/api/reports/attendance/{eventId}", this::getAttendanceReport);
        app.get("/api/reports/attendance/{eventId}/pdf", this::getAttendanceReportPdf);
        app.post("/api/certificates/issue/{eventId}/{userId}", this::issueCertificate);
        app.get("/api/certificates/user/{userId}", this::getUserCertificates);
        app.get("/api/certificates/verify/{code}", this::verifyCertificate);
        app.get("/api/certificates/download/{id}", this::downloadCertificatePdf);
    }

    // --- 1. Auth Handlers ---
    private void register(Context ctx) {
        Map<String, String> body = ctx.bodyAsClass(Map.class);
        String name = body.get("name");
        String email = body.get("email");
        String password = body.get("password");
        User user = authUseCase.register(name, email, password, UserRole.PARTICIPANT);
        AuthenticatedSession session = sessionStore.create(user);
        ctx.status(HttpStatus.CREATED).json(authResponse(user, session));
    }

    private void login(Context ctx) {
        Map<String, String> body = ctx.bodyAsClass(Map.class);
        User user = authUseCase.login(body.get("email"), body.get("password"));
        AuthenticatedSession session = sessionStore.create(user);
        ctx.json(authResponse(user, session));
    }

    private void logout(Context ctx) {
        authorization.requireAuthenticated(ctx);
        sessionStore.revoke(authorization.extractBearerToken(ctx));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void listUsers(Context ctx) {
        authorization.requireRole(ctx, UserRole.ADMIN, UserRole.ORGANIZER);
        List<User> list = authUseCase.listUsers();
        ctx.json(list.stream().map(this::sanitizeUser).toList());
    }

    private void getUser(Context ctx) {
        Long id = Long.parseLong(ctx.pathParam("id"));
        authorization.requireSelfOrStaff(ctx, id);
        User user = authUseCase.getUserById(id);
        ctx.json(sanitizeUser(user));
    }

    private void updateUser(Context ctx) {
        Long id = Long.parseLong(ctx.pathParam("id"));
        authorization.requireSelfOrStaff(ctx, id);
        Map<String, String> body = ctx.bodyAsClass(Map.class);
        User updated = authUseCase.updateProfile(id, body.get("name"));
        ctx.json(sanitizeUser(updated));
    }

    // --- 2. Event Handlers ---
    private void listEvents(Context ctx) {
        String search = ctx.queryParam("search");
        String track = ctx.queryParam("track");
        String typeStr = ctx.queryParam("type");
        String statusStr = ctx.queryParam("status");

        ActivityType type = (typeStr != null && !typeStr.isEmpty()) ? ActivityType.valueOf(typeStr.toUpperCase()) : null;
        boolean staff = authorization.findSession(ctx)
                .map(s -> s.hasAnyRole(UserRole.ADMIN, UserRole.ORGANIZER))
                .orElse(false);
        EventStatus status = (statusStr != null && !statusStr.isEmpty())
                ? EventStatus.valueOf(statusStr.toUpperCase())
                : (staff ? null : EventStatus.PUBLISHED);

        List<Event> events = eventUseCase.filterEvents(search, track, type, status);
        ctx.json(events);
    }

    private void getEvent(Context ctx) {
        Long id = Long.parseLong(ctx.pathParam("id"));
        Event event = eventUseCase.getEvent(id);
        if (event.getStatus() != EventStatus.PUBLISHED && event.getStatus() != EventStatus.IN_PROGRESS) {
            authorization.requireRole(ctx, UserRole.ADMIN, UserRole.ORGANIZER);
        }
        ctx.json(event);
    }

    private void createEvent(Context ctx) {
        AuthenticatedSession session = authorization.requireRole(ctx, UserRole.ADMIN, UserRole.ORGANIZER);
        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        String title = (String) body.get("title");
        String description = (String) body.get("description");
        LocalDateTime start = parseDateTime((String) body.get("start"));
        LocalDateTime end = parseDateTime((String) body.get("end"));
        int capacity = body.containsKey("maxCapacity") ? ((Number) body.get("maxCapacity")).intValue() : 500;
        Long orgId = session.userId();

        String polType = (String) body.getOrDefault("certPolicyType", "MIN_PERCENTAGE");
        double polParam = body.containsKey("certPolicyParam") ? ((Number) body.get("certPolicyParam")).doubleValue() : 75.0;
        CertificateEligibilityPolicy policy = "MANDATORY_COUNT".equalsIgnoreCase(polType)
                ? new MandatoryActivitiesPolicy((int) polParam)
                : new MinimumAttendancePercentagePolicy(polParam);

        Event event = new Event(null, title, description, new Period(start, end), EventStatus.DRAFT, orgId, new ArrayList<>(), capacity, policy);
        event.configureRegistration(
                booleanValue(body, "activitySelectionEnabled", true),
                booleanValue(body, "activitySelectionRequired", false),
                body.get("registrationDeadline") instanceof String deadline && !deadline.isBlank()
                        ? parseDateTime(deadline) : start,
                stringValue(body, "timeZone", "America/Sao_Paulo"));
        Event saved = eventUseCase.createEvent(event);
        ctx.status(HttpStatus.CREATED).json(saved);
    }

    private void updateEvent(Context ctx) {
        Long id = Long.parseLong(ctx.pathParam("id"));
        requireEventManager(ctx, id);
        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        Event existing = eventUseCase.getEvent(id);

        String title = (String) body.getOrDefault("title", existing.getTitle());
        String description = (String) body.getOrDefault("description", existing.getDescription());
        LocalDateTime start = body.containsKey("start") ? parseDateTime((String) body.get("start")) : existing.getPeriod().getStart();
        LocalDateTime end = body.containsKey("end") ? parseDateTime((String) body.get("end")) : existing.getPeriod().getEnd();
        int capacity = body.containsKey("maxCapacity") ? ((Number) body.get("maxCapacity")).intValue() : existing.getMaxCapacity();

        existing.updateDetails(title, description, new Period(start, end), capacity);
        existing.configureRegistration(
                booleanValue(body, "activitySelectionEnabled", existing.isActivitySelectionEnabled()),
                booleanValue(body, "activitySelectionRequired", existing.isActivitySelectionRequired()),
                body.get("registrationDeadline") instanceof String deadline && !deadline.isBlank()
                        ? parseDateTime(deadline) : existing.getRegistrationDeadline(),
                stringValue(body, "timeZone", existing.getTimeZone()));
        Event updated = eventUseCase.updateEvent(existing);
        ctx.json(updated);
    }

    private void publishEvent(Context ctx) {
        Long id = Long.parseLong(ctx.pathParam("id"));
        requireEventManager(ctx, id);
        Event published = eventUseCase.publishEvent(id);
        ctx.json(published);
    }

    private void cancelEvent(Context ctx) {
        Long id = Long.parseLong(ctx.pathParam("id"));
        requireEventManager(ctx, id);
        Event cancelled = eventUseCase.cancelEvent(id);
        ctx.json(cancelled);
    }

    private void finishEvent(Context ctx) {
        Long id = Long.parseLong(ctx.pathParam("id"));
        requireEventManager(ctx, id);
        ctx.json(eventUseCase.finishEvent(id));
    }

    private void listActivities(Context ctx) {
        Long eventId = Long.parseLong(ctx.pathParam("id"));
        Event event = eventUseCase.getEvent(eventId);
        if (event.getStatus() != EventStatus.PUBLISHED && event.getStatus() != EventStatus.IN_PROGRESS) {
            requireEventManager(ctx, eventId);
        }
        List<Activity> list = eventUseCase.getEventActivities(eventId);
        ctx.json(list);
    }

    private void addActivity(Context ctx) {
        Long eventId = Long.parseLong(ctx.pathParam("id"));
        requireEventManager(ctx, eventId);
        Map<String, Object> body = ctx.bodyAsClass(Map.class);

        String title = (String) body.get("title");
        String description = (String) body.get("description");
        LocalDateTime start = parseDateTime((String) body.get("start"));
        LocalDateTime end = parseDateTime((String) body.get("end"));
        String room = (String) body.get("room");
        String track = (String) body.getOrDefault("track", "Geral");
        int capacity = body.containsKey("capacity") ? ((Number) body.get("capacity")).intValue() : 50;
        String typeStr = (String) body.getOrDefault("type", "LECTURE");
        ActivityType type = ActivityType.valueOf(typeStr.toUpperCase());
        String polStr = (String) body.getOrDefault("attendancePolicy", "SINGLE_CHECKIN");
        boolean requiresReg = !body.containsKey("requiresRegistration") || (Boolean) body.get("requiresRegistration");

        List<Speaker> speakers = new ArrayList<>();
        if (body.containsKey("speakers") && body.get("speakers") instanceof List<?> rawSpeakers) {
            for (Object obj : rawSpeakers) {
                if (obj instanceof Map) {
                    Map map = (Map) obj;
                    String sname = (String) map.get("name");
                    String srole = map.containsKey("role") ? (String) map.get("role") : "Palestrante";
                    String sbio = map.containsKey("bio") ? (String) map.get("bio") : "";
                    String sphoto = (String) map.get("photoUrl");
                    speakers.add(new Speaker(null, sname, srole, sbio, sphoto));
                }
            }
        }

        Activity act = new Activity(null, eventId, title, description, new Period(start, end),
                new ActivityLocation(room, track, capacity), type, capacity, 0, speakers,
                AttendancePolicyFactory.create(polStr), requiresReg);

        Activity saved = eventUseCase.addActivityToEvent(eventId, act);
        ctx.status(HttpStatus.CREATED).json(saved);
    }

    private void getActivity(Context ctx) {
        Long id = Long.parseLong(ctx.pathParam("id"));
        Activity activity = eventUseCase.getActivity(id);
        Event event = eventUseCase.getEvent(activity.getEventId());
        if (event.getStatus() != EventStatus.PUBLISHED && event.getStatus() != EventStatus.IN_PROGRESS) {
            requireEventManager(ctx, event.getId());
        }
        ctx.json(activity);
    }

    // --- 3. Registration Handlers ---
    private void registerForEvent(Context ctx) {
        AuthenticatedSession session = authorization.requireAuthenticated(ctx);
        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        Long eventId = ((Number) body.get("eventId")).longValue();
        Long userId = session.userId();

        Set<Long> acts = new HashSet<>();
        if (body.containsKey("activityIds") && body.get("activityIds") instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Number n) acts.add(n.longValue());
            }
        }

        Registration reg = registrationUseCase.registerForEvent(eventId, userId, acts);
        ctx.status(HttpStatus.CREATED).json(reg);
    }

    private void addActivityToRegistration(Context ctx) {
        Long eventId = Long.parseLong(ctx.pathParam("eventId"));
        Long activityId = Long.parseLong(ctx.pathParam("activityId"));
        Long userId = authorization.requireAuthenticated(ctx).userId();

        Registration reg = registrationUseCase.addActivityToRegistration(eventId, userId, activityId);
        ctx.json(reg);
    }

    private void removeActivityFromRegistration(Context ctx) {
        Long eventId = Long.parseLong(ctx.pathParam("eventId"));
        Long activityId = Long.parseLong(ctx.pathParam("activityId"));
        Long userId = authorization.requireAuthenticated(ctx).userId();

        Registration reg = registrationUseCase.removeActivityFromRegistration(eventId, userId, activityId);
        ctx.json(reg);
    }

    private void cancelRegistration(Context ctx) {
        Long eventId = Long.parseLong(ctx.pathParam("eventId"));
        Long userId = authorization.requireAuthenticated(ctx).userId();

        registrationUseCase.cancelRegistration(eventId, userId);
        ctx.json(Map.of("message", "Inscrição cancelada com sucesso."));
    }

    private void getEventRegistrations(Context ctx) {
        Long eventId = Long.parseLong(ctx.pathParam("eventId"));
        requireEventManager(ctx, eventId);
        ctx.json(registrationUseCase.getEventRegistrations(eventId));
    }

    private void getUserRegistrations(Context ctx) {
        Long userId = Long.parseLong(ctx.pathParam("userId"));
        authorization.requireSelfOrStaff(ctx, userId);
        ctx.json(registrationUseCase.getUserRegistrations(userId));
    }

    private void getParticipantAgenda(Context ctx) {
        Long userId = Long.parseLong(ctx.pathParam("userId"));
        authorization.requireSelfOrStaff(ctx, userId);
        Long eventId = Long.parseLong(ctx.pathParam("eventId"));
        ctx.json(registrationUseCase.getParticipantAgenda(userId, eventId));
    }

    // --- 4. Attendance Handlers ---
    private void generateQrToken(Context ctx) {
        Long activityId = Long.parseLong(ctx.pathParam("activityId"));
        requireEventManager(ctx, eventUseCase.getActivity(activityId).getEventId());
        String token = attendanceUseCase.generateQrToken(activityId);
        ctx.json(Map.of("activityId", activityId, "qrToken", token));
    }

    private void recordQrAttendance(Context ctx) {
        AuthenticatedSession session = authorization.requireAuthenticated(ctx);
        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        String qrToken = (String) body.get("qrToken");
        Long userId = session.userId();

        AttendanceRecord record = attendanceUseCase.recordQrAttendance(qrToken, userId);
        ctx.status(HttpStatus.CREATED).json(record);
    }

    private void recordManualAttendance(Context ctx) {
        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        Long activityId = ((Number) body.get("activityId")).longValue();
        AuthenticatedSession session = requireEventManager(ctx, eventUseCase.getActivity(activityId).getEventId());
        Long userId = ((Number) body.get("userId")).longValue();
        Long organizerId = session.userId();
        String typeStr = (String) body.getOrDefault("type", "MANUAL_ENTRY");
        AttendanceType type = AttendanceType.valueOf(typeStr.toUpperCase());
        String notes = body.get("notes") instanceof String text
                ? text : (String) body.get("justification");

        AttendanceRecord record = attendanceUseCase.recordManualAttendance(activityId, userId, organizerId, type, notes);
        ctx.status(HttpStatus.CREATED).json(record);
    }

    private void getActivityAttendanceRecords(Context ctx) {
        Long activityId = Long.parseLong(ctx.pathParam("activityId"));
        requireEventManager(ctx, eventUseCase.getActivity(activityId).getEventId());
        ctx.json(attendanceUseCase.getActivityRecords(activityId));
    }

    private void getActivityAttendanceOverview(Context ctx) {
        Long activityId = Long.parseLong(ctx.pathParam("activityId"));
        requireEventManager(ctx, eventUseCase.getActivity(activityId).getEventId());
        ctx.json(attendanceUseCase.getActivityAttendanceOverview(activityId));
    }

    // --- 5. Survey Handlers ---
    private void createSurvey(Context ctx) {
        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        Long activityId = ((Number) body.get("activityId")).longValue();
        requireEventManager(ctx, eventUseCase.getActivity(activityId).getEventId());
        String title = (String) body.get("title");
        List<SurveyQuestion> questions = new ArrayList<>();

        if (body.containsKey("questions") && body.get("questions") instanceof List<?> rawQuestions) {
            for (Object obj : rawQuestions) {
                if (obj instanceof Map) {
                    Map map = (Map) obj;
                    String qtext = (String) map.get("questionText");
                    String qtypeStr = (String) map.get("type");
                    QuestionType qtype = QuestionType.valueOf(qtypeStr.toUpperCase());
                    List<String> options = map.containsKey("options") ? (List<String>) map.get("options") : new ArrayList<>();
                    boolean required = !map.containsKey("required") || Boolean.TRUE.equals(map.get("required"));
                    questions.add(new SurveyQuestion(null, qtext, qtype, options, required));
                }
            }
        }

        FeedbackSurvey survey = new FeedbackSurvey(null, activityId, title, questions, true);
        FeedbackSurvey saved = surveyUseCase.createSurvey(survey);
        ctx.status(HttpStatus.CREATED).json(saved);
    }

    private void getSurvey(Context ctx) {
        Long activityId = Long.parseLong(ctx.pathParam("activityId"));
        ctx.json(surveyUseCase.getSurveyByActivity(activityId));
    }

    private void canEvaluate(Context ctx) {
        Long activityId = Long.parseLong(ctx.pathParam("activityId"));
        Long userId = Long.parseLong(ctx.pathParam("userId"));
        authorization.requireSelfOrStaff(ctx, userId);
        boolean can = surveyUseCase.canUserEvaluate(userId, activityId);
        ctx.json(Map.of("activityId", activityId, "userId", userId, "canEvaluate", can));
    }

    private void submitSurvey(Context ctx) {
        AuthenticatedSession session = authorization.requireAuthenticated(ctx);
        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        Long surveyId = ((Number) body.get("surveyId")).longValue();
        Long activityId = ((Number) body.get("activityId")).longValue();
        Long userId = session.userId();
        boolean anonymous = body.containsKey("anonymous") && (Boolean) body.get("anonymous");

        Map<Long, String> answers = new HashMap<>();
        if (body.containsKey("answers") && body.get("answers") instanceof Map<?, ?> rawAnswers) {
            for (Map.Entry<?, ?> entry : rawAnswers.entrySet()) {
                answers.put(Long.parseLong(entry.getKey().toString()), entry.getValue().toString());
            }
        }

        SurveyResponse resp = new SurveyResponse(null, surveyId, activityId, userId, anonymous, answers, LocalDateTime.now());
        SurveyResponse saved = surveyUseCase.submitResponse(resp);
        ctx.status(HttpStatus.CREATED).json(saved);
    }

    private void getSurveyResults(Context ctx) {
        Long activityId = Long.parseLong(ctx.pathParam("activityId"));
        requireEventManager(ctx, eventUseCase.getActivity(activityId).getEventId());
        SurveyResultsDto results = surveyUseCase.getConsolidatedResults(activityId);
        ctx.json(results);
    }

    // --- 6. Report & Certificate Handlers ---
    private void getEnrolledReport(Context ctx) {
        Long eventId = Long.parseLong(ctx.pathParam("eventId"));
        requireEventManager(ctx, eventId);
        EnrolledReportDto report = reportUseCase.getEnrolledReport(eventId);
        ctx.json(report);
    }

    private void getEnrolledReportCsv(Context ctx) {
        Long eventId = Long.parseLong(ctx.pathParam("eventId"));
        requireEventManager(ctx, eventId);
        byte[] csv = reportUseCase.exportEnrolledCsv(eventId);
        ctx.contentType("text/csv; charset=UTF-8");
        ctx.header("Content-Disposition", "attachment; filename=relatorio_inscritos_" + eventId + ".csv");
        ctx.result(csv);
    }

    private void getEnrolledReportPdf(Context ctx) {
        Long eventId = Long.parseLong(ctx.pathParam("eventId"));
        requireEventManager(ctx, eventId);
        byte[] pdf = reportUseCase.exportEnrolledPdf(eventId);
        ctx.contentType("application/pdf");
        ctx.header("Content-Disposition", "inline; filename=relatorio_inscritos_" + eventId + ".pdf");
        ctx.result(pdf);
    }

    private void getAttendanceReport(Context ctx) {
        Long eventId = Long.parseLong(ctx.pathParam("eventId"));
        requireEventManager(ctx, eventId);
        AttendanceReportDto report = reportUseCase.getAttendanceReport(eventId);
        ctx.json(report);
    }

    private void getAttendanceReportPdf(Context ctx) {
        Long eventId = Long.parseLong(ctx.pathParam("eventId"));
        requireEventManager(ctx, eventId);
        byte[] pdf = reportUseCase.exportAttendancePdf(eventId);
        ctx.contentType("application/pdf");
        ctx.header("Content-Disposition", "inline; filename=relatorio_frequencia_" + eventId + ".pdf");
        ctx.result(pdf);
    }

    private void issueCertificate(Context ctx) {
        Long eventId = Long.parseLong(ctx.pathParam("eventId"));
        Long userId = Long.parseLong(ctx.pathParam("userId"));
        authorization.requireSelfOrStaff(ctx, userId);
        Certificate cert = certificateUseCase.issueCertificateIfEligible(eventId, userId);
        ctx.status(HttpStatus.CREATED).json(cert);
    }

    private void getUserCertificates(Context ctx) {
        Long userId = Long.parseLong(ctx.pathParam("userId"));
        authorization.requireSelfOrStaff(ctx, userId);
        ctx.json(certificateUseCase.getUserCertificates(userId));
    }

    private void verifyCertificate(Context ctx) {
        String code = ctx.pathParam("code");
        Certificate cert = certificateUseCase.getCertificateByVerificationCode(code);
        ctx.json(cert);
    }

    private void downloadCertificatePdf(Context ctx) {
        Long id = Long.parseLong(ctx.pathParam("id"));
        Certificate certificate = certificateUseCase.getCertificateById(id);
        authorization.requireSelfOrStaff(ctx, certificate.getUserId());
        byte[] pdf = certificateUseCase.exportCertificatePdf(id);
        ctx.contentType("application/pdf");
        ctx.header("Content-Disposition", "inline; filename=certificado_" + id + ".pdf");
        ctx.result(pdf);
    }

    // Helper utilities
    private Map<String, Object> sanitizeUser(User u) {
        return Map.of(
                "id", u.getId(),
                "name", u.getName(),
                "email", u.getEmail().getValue(),
                "role", u.getRole().name(),
                "roleName", u.getRole().getDisplayName(),
                "createdAt", u.getCreatedAt().toString()
        );
    }

    private Map<String, Object> authResponse(User user, AuthenticatedSession session) {
        return Map.of(
                "token", session.token(),
                "expiresAt", session.expiresAt().toString(),
                "user", sanitizeUser(user)
        );
    }

    private AuthenticatedSession requireEventManager(Context ctx, Long eventId) {
        AuthenticatedSession session = authorization.requireRole(ctx, UserRole.ADMIN, UserRole.ORGANIZER);
        if (session.role() == UserRole.ORGANIZER) {
            Event event = eventUseCase.getEvent(eventId);
            if (event.getOrganizerId() == null || !event.getOrganizerId().equals(session.userId())) {
                throw new UnauthorizedException("Somente o organizador responsável ou um administrador pode alterar este evento.");
            }
        }
        return session;
    }

    private LocalDateTime parseDateTime(String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new com.eventos.domain.exceptions.ValidationException("Data e horário são obrigatórios.");
        }
        text = text.trim();
        try {
            if (text.contains("T")) {
                return LocalDateTime.parse(text, ISO_FMT);
            }
            if (text.contains("/")) {
                return LocalDateTime.parse(text, DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
            }
            return LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        } catch (Exception e) {
            throw new com.eventos.domain.exceptions.ValidationException(
                    "Data/hora inválida. Use ISO-8601, dd/MM/yyyy HH:mm ou yyyy-MM-dd HH:mm.");
        }
    }

    private boolean booleanValue(Map<String, Object> body, String key, boolean defaultValue) {
        Object value = body.get(key);
        return value instanceof Boolean bool ? bool : defaultValue;
    }

    private String stringValue(Map<String, Object> body, String key, String defaultValue) {
        Object value = body.get(key);
        return value instanceof String text && !text.isBlank() ? text : defaultValue;
    }
}
