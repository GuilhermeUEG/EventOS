package com.eventos.adapters.input.rest;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Contrato JSON de saída da API. Os clientes (site e desktop) dependem destes registros,
 * nunca das entidades de domínio.
 */
public final class ApiDtos {
    private ApiDtos() {
    }

    public record ErrorResponse(String error) {
    }

    public record UserResponse(Long id, String name, String email, String role, String roleName, LocalDateTime createdAt) {
    }

    public record AuthResponse(String token, Instant expiresAt, UserResponse user) {
    }

    public record PeriodResponse(LocalDateTime start, LocalDateTime end, double durationHours) {
    }

    public record LocationResponse(String room, String spaceOrTrack, int capacity) {
    }

    public record SpeakerResponse(Long id, String name, String roleInActivity, String bio, String photoUrl) {
    }

    public record AttendancePolicyResponse(String policyKey, String policyName, String policyDescription) {
    }

    public record ActivityResponse(Long id, Long eventId, String title, String description, PeriodResponse period,
                                   LocationResponse location, String type, String typeName, int maxCapacity,
                                   int currentEnrollments, int availableSlots, List<SpeakerResponse> speakers,
                                   AttendancePolicyResponse attendancePolicy, boolean requiresRegistration) {
    }

    public record HomeEventResponse(Long id, String title, String description, PeriodResponse period, String statusName,
                                    boolean enrollmentOpen, LocalDateTime registrationDeadline, int activityCount,
                                    int maxCapacity, List<String> tracks, boolean upcoming) {
    }

    public record HomeActivityResponse(Long id, Long eventId, String eventTitle, String title, String typeName,
                                       PeriodResponse period, String room, String track, List<String> speakers) {
    }

    public record HomeSpeakerResponse(String name, String role, String bio, String photoUrl, Long eventId,
                                      String eventTitle, String activityTitle) {
    }

    public record HomeResponse(int publicEvents, int upcomingEvents, List<HomeEventResponse> events,
                               List<HomeActivityResponse> activities, List<HomeSpeakerResponse> speakers) {
    }

    public record CertificatePolicyResponse(String policyKey, String policyDescription, Double percentage,
                                            Integer minActivities, Set<Long> activityIds) {
    }

    public record EventResponse(Long id, String title, String description, PeriodResponse period, String status,
                                String statusName, Long organizerId, int maxCapacity,
                                CertificatePolicyResponse certificatePolicy, boolean activitySelectionEnabled,
                                boolean activitySelectionRequired, LocalDateTime registrationDeadline,
                                String timeZone, boolean enrollmentOpen, List<ActivityResponse> activities) {
    }

    public record RegistrationResponse(Long id, Long eventId, Long userId, Set<Long> selectedActivityIds,
                                       LocalDateTime registrationDate, String status, String statusName,
                                       boolean confirmed) {
    }

    public record AttendanceRecordResponse(Long id, Long activityId, Long userId, LocalDateTime timestamp,
                                           String type, String typeName, String recordedBy, String notes) {
    }

    public record AttendanceResultResponse(AttendanceRecordResponse record, boolean alreadyRecorded,
                                           String status, String statusName) {
    }

    public record QrCodeResponse(Long activityId, String qrToken, String qrImage, long validForSeconds) {
    }

    public record QuestionResponse(Long id, String questionText, String type, String typeName,
                                   List<String> options, boolean required) {
    }

    public record SurveyView(Long id, Long activityId, String title, boolean active, boolean anonymous,
                             String identificationNotice, List<QuestionResponse> questions) {
    }

    public record SubmittedEvaluation(Long id, Long surveyId, Long activityId, LocalDateTime submittedAt,
                                      boolean anonymous) {
    }

    public record EvaluationEligibilityResponse(Long activityId, Long userId, boolean canEvaluate, String reason) {
    }

    public record CertificateResponse(Long id, Long eventId, Long userId, String participantName, String eventTitle,
                                      double completionHours, LocalDate issueDate, String verificationCode) {
    }

    public record DeliveryResponse(Long id, Long certificateId, String recipient, String status, String statusName,
                                   String detail, LocalDateTime attemptedAt) {
    }

    public record DemoAccountResponse(String name, String email, String password, String role) {
    }
}
