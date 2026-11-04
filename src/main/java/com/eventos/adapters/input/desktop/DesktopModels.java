package com.eventos.adapters.input.desktop;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Modelos de visão do desktop: espelham o contrato JSON da API e não dependem das classes de
 * domínio nem de aplicação do servidor. Datas chegam como texto ISO-8601.
 */
public final class DesktopModels {
    private DesktopModels() {
    }

    // ------------------------------------------------------------------ respostas

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserView(Long id, String name, String email, String role, String roleName) {
        public boolean isParticipant() {
            return "PARTICIPANT".equals(role);
        }

        public boolean isAdmin() {
            return "ADMIN".equals(role);
        }

        @Override
        public String toString() {
            return name + " <" + email + ">";
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PeriodView(String start, String end, double durationHours) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LocationView(String room, String spaceOrTrack, int capacity) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SpeakerView(Long id, String name, String roleInActivity, String bio, String photoUrl) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AttendancePolicyView(String policyKey, String policyName, String policyDescription) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ActivityView(Long id, Long eventId, String title, String description, PeriodView period,
                               LocationView location, String type, String typeName, int maxCapacity,
                               int currentEnrollments, int availableSlots, List<SpeakerView> speakers,
                               AttendancePolicyView attendancePolicy, boolean requiresRegistration) {
        @Override
        public String toString() {
            return title;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CertificatePolicyView(String policyKey, String policyDescription, Double percentage,
                                        Integer minActivities, List<Long> activityIds) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EventView(Long id, String title, String description, PeriodView period, String status,
                            String statusName, Long organizerId, int maxCapacity,
                            CertificatePolicyView certificatePolicy, boolean activitySelectionEnabled,
                            boolean activitySelectionRequired, String registrationDeadline, String timeZone,
                            boolean enrollmentOpen, List<ActivityView> activities) {
        @Override
        public String toString() {
            return title;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AttendanceOverviewRow(Long userId, String userName, String userEmail, Long activityId,
                                        String activityTitle, String status, String lastRecordTime,
                                        String recordedBy) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AttendanceRecordView(Long id, Long activityId, Long userId, String timestamp, String type,
                                       String typeName, String recordedBy, String notes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record QrCodeView(Long activityId, String qrToken, String qrImage, long validForSeconds) {
        /** Bytes PNG da imagem entregue como data URI (data:image/png;base64,...). */
        public byte[] imageBytes() {
            if (qrImage == null || !qrImage.contains(",")) return new byte[0];
            return Base64.getDecoder().decode(qrImage.substring(qrImage.indexOf(',') + 1));
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SurveyView(Long id, Long activityId, String title, boolean anonymous, String identificationNotice) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record QuestionMetric(Long questionId, String questionText, String questionType, int answeredCount,
                                 double averageScore, Map<String, Integer> optionDistribution) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SurveyResultsView(Long activityId, String activityTitle, String surveyTitle, int totalResponses,
                                    double averageRating, boolean anonymousResponses, String identificationNotice,
                                    List<QuestionMetric> questionMetrics, List<String> textComments) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EnrolledParticipant(Long userId, String name, String email, String status,
                                      String registrationDate, int selectedActivitiesCount) {
        @Override
        public String toString() {
            return name + " <" + email + ">";
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EnrolledReportView(Long eventId, String eventTitle, int totalEnrolled, int totalCancelled,
                                     int eventCapacity, List<EnrolledParticipant> participants) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ActivitySummary(Long activityId, String activityTitle, String room, int totalPresent,
                                  int totalPartial, int totalAbsent) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ParticipantAttendanceRow(Long userId, String userName, String userEmail, Long activityId,
                                           String activityTitle, String status, int recordsCount,
                                           String lastRecordTime, String lastRecordedBy) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AttendanceReportView(Long eventId, String eventTitle, int totalParticipants,
                                       double overallAttendanceRate, List<ActivitySummary> activitySummaries,
                                       List<ParticipantAttendanceRow> rows) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EligibilityView(boolean eligible, String justification, String policyDescription,
                                  int activitiesAttended, int totalActivities, double hoursAttended,
                                  double totalHours, boolean alreadyIssued) {
    }

    // ------------------------------------------------------------------ formulários (requisições)

    /** Política de certificado: type = MIN_PERCENTAGE | MIN_ACTIVITIES | REQUIRED_ACTIVITIES. */
    public record PolicyForm(String type, Double percentage, Integer minActivities, List<Long> activityIds) {
    }

    /** Datas no formato dd/MM/yyyy HH:mm (aceito pela API). */
    public record EventForm(String title, String description, String start, String end, int maxCapacity,
                            String registrationDeadline, String timeZone, boolean selectionEnabled,
                            boolean selectionRequired, PolicyForm policy) {
    }

    public record SpeakerForm(String name, String role, String bio) {
    }

    public record ActivityForm(String title, String description, String start, String end, String room,
                               String track, int capacity, String type, String attendancePolicy,
                               boolean requiresRegistration, List<SpeakerForm> speakers) {
    }

    /** Critérios opcionais (nulos ou vazios não restringem) da consulta à programação. */
    public record ActivityQuery(String date, String from, String to, String track, String type, String room,
                                String search) {
        public static ActivityQuery none() {
            return new ActivityQuery(null, null, null, null, null, null, null);
        }
    }

    public record QuestionForm(String text, String type, List<String> options, boolean required) {
        @Override
        public String toString() {
            return text + "  [" + type + (required ? ", obrigatória" : ", opcional") + "]";
        }
    }

    public record SurveyForm(String title, boolean anonymous, List<QuestionForm> questions) {
    }
}
