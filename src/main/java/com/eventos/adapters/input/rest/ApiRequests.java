package com.eventos.adapters.input.rest;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.List;
import java.util.Map;

/**
 * Contrato JSON de entrada da API. Datas chegam como texto (ISO-8601, dd/MM/yyyy HH:mm ou yyyy-MM-dd HH:mm).
 */
public final class ApiRequests {
    private ApiRequests() {
    }

    public record Register(String name, String email, String password) {
    }

    public record Login(String email, String password) {
    }

    public record UpdateProfile(String name) {
    }

    public record ChangePassword(String currentPassword, String newPassword) {
    }

    public record CreateUser(String name, String email, String password, String role) {
    }

    public record ChangeRole(String role) {
    }

    public record CertificatePolicy(String type, Double percentage, Integer minActivities, List<Long> activityIds) {
    }

    public record CreateEvent(String title, String description, String start, String end, Integer maxCapacity,
                              Boolean activitySelectionEnabled, Boolean activitySelectionRequired,
                              String registrationDeadline, String timeZone, CertificatePolicy certificatePolicy) {
    }

    public record UpdateEvent(String title, String description, String start, String end, Integer maxCapacity,
                              Boolean activitySelectionEnabled, Boolean activitySelectionRequired,
                              String registrationDeadline, String timeZone) {
    }

    public record Speaker(String name, String role, String bio, String photoUrl) {
    }

    public record CreateActivity(String title, String description, String start, String end, String room,
                                 String track, Integer capacity, String type, String attendancePolicy,
                                 Boolean requiresRegistration, List<Speaker> speakers) {
    }

    public record Registration(Long eventId, List<Long> activityIds) {
    }

    public record QrScan(String qrToken) {
    }

    public record ManualAttendance(Long activityId, Long userId, String type,
                                   @JsonAlias("justification") String notes) {
    }

    public record RevokeAttendance(Long activityId, Long userId, @JsonAlias("justification") String notes) {
    }

    public record Question(String questionText, String type, List<String> options, Boolean required) {
    }

    public record CreateSurvey(Long activityId, String title, Boolean anonymous, List<Question> questions) {
    }

    public record SubmitSurvey(Long activityId, Map<String, String> answers) {
    }
}
