package com.eventos.adapters.input.rest;

import com.eventos.adapters.input.rest.ApiDtos.ActivityResponse;
import com.eventos.adapters.input.rest.ApiDtos.AttendancePolicyResponse;
import com.eventos.adapters.input.rest.ApiDtos.AttendanceRecordResponse;
import com.eventos.adapters.input.rest.ApiDtos.AttendanceResultResponse;
import com.eventos.adapters.input.rest.ApiDtos.CertificatePolicyResponse;
import com.eventos.adapters.input.rest.ApiDtos.CertificateResponse;
import com.eventos.adapters.input.rest.ApiDtos.EventResponse;
import com.eventos.adapters.input.rest.ApiDtos.LocationResponse;
import com.eventos.adapters.input.rest.ApiDtos.PeriodResponse;
import com.eventos.adapters.input.rest.ApiDtos.QrCodeResponse;
import com.eventos.adapters.input.rest.ApiDtos.QuestionResponse;
import com.eventos.adapters.input.rest.ApiDtos.RegistrationResponse;
import com.eventos.adapters.input.rest.ApiDtos.SpeakerResponse;
import com.eventos.adapters.input.rest.ApiDtos.SubmittedEvaluation;
import com.eventos.adapters.input.rest.ApiDtos.SurveyView;
import com.eventos.adapters.input.rest.ApiDtos.UserResponse;
import com.eventos.application.dtos.AttendanceResult;
import com.eventos.application.dtos.QrCodeDto;
import com.eventos.domain.model.Activity;
import com.eventos.domain.model.AttendanceRecord;
import com.eventos.domain.model.Certificate;
import com.eventos.domain.model.Event;
import com.eventos.domain.model.FeedbackSurvey;
import com.eventos.domain.model.Period;
import com.eventos.domain.model.Registration;
import com.eventos.domain.model.SurveyQuestion;
import com.eventos.domain.model.SurveyResponse;
import com.eventos.domain.model.User;
import com.eventos.domain.policies.AttendancePolicy;
import com.eventos.domain.policies.CertificateEligibilityPolicy;
import com.eventos.domain.policies.CertificatePolicySpec;
import java.util.Base64;
import java.util.List;

/**
 * Converte objetos de domínio e de aplicação nos DTOs do contrato JSON.
 */
final class ApiMapper {
    private ApiMapper() {
    }

    static UserResponse user(User u) {
        return new UserResponse(u.getId(), u.getName(), u.getEmail().getValue(), u.getRole().name(),
                u.getRole().getDisplayName(), u.getCreatedAt());
    }

    static PeriodResponse period(Period p) {
        return new PeriodResponse(p.getStart(), p.getEnd(), p.getDurationHours());
    }

    static ActivityResponse activity(Activity a) {
        AttendancePolicy policy = a.getAttendancePolicy();
        return new ActivityResponse(a.getId(), a.getEventId(), a.getTitle(), a.getDescription(), period(a.getPeriod()),
                new LocationResponse(a.getLocation().getRoom(), a.getLocation().getSpaceOrTrack(), a.getLocation().getCapacity()),
                a.getType().name(), a.getType().getDescription(), a.getMaxCapacity(), a.getCurrentEnrollments(),
                a.getAvailableSlots(),
                a.getSpeakers().stream()
                        .map(s -> new SpeakerResponse(s.getId(), s.getName(), s.getRoleInActivity(), s.getBio(), s.getPhotoUrl()))
                        .toList(),
                new AttendancePolicyResponse(policy.getPolicyKey(), policy.getPolicyName(), policy.getPolicyDescription()),
                a.isRequiresRegistration());
    }

    static List<ActivityResponse> activities(List<Activity> list) {
        return list.stream().map(ApiMapper::activity).toList();
    }

    static CertificatePolicyResponse certificatePolicy(CertificateEligibilityPolicy policy) {
        CertificatePolicySpec spec = policy.spec();
        return new CertificatePolicyResponse(policy.getPolicyKey(), policy.getPolicyDescription(),
                spec.percentage(), spec.minActivities(), spec.activityIds());
    }

    static EventResponse event(Event e) {
        return new EventResponse(e.getId(), e.getTitle(), e.getDescription(), period(e.getPeriod()),
                e.getStatus().name(), e.getStatus().getDescription(), e.getOrganizerId(), e.getMaxCapacity(),
                certificatePolicy(e.getCertificateEligibilityPolicy()), e.isActivitySelectionEnabled(),
                e.isActivitySelectionRequired(), e.getRegistrationDeadline(), e.getTimeZone(), e.isEnrollmentOpen(),
                activities(e.getActivities()));
    }

    static List<EventResponse> events(List<Event> list) {
        return list.stream().map(ApiMapper::event).toList();
    }

    static RegistrationResponse registration(Registration r) {
        return new RegistrationResponse(r.getId(), r.getEventId(), r.getUserId(), r.getSelectedActivityIds(),
                r.getRegistrationDate(), r.getStatus().name(), r.getStatus().getDescription(), r.isConfirmed());
    }

    static AttendanceRecordResponse attendanceRecord(AttendanceRecord r) {
        return new AttendanceRecordResponse(r.getId(), r.getActivityId(), r.getUserId(), r.getTimestamp(),
                r.getType().name(), r.getType().getDescription(), r.getRecordedBy(), r.getNotes());
    }

    static AttendanceResultResponse attendanceResult(AttendanceResult r) {
        return new AttendanceResultResponse(attendanceRecord(r.record()), r.alreadyRecorded(),
                r.status().name(), r.status().getDescription());
    }

    static QrCodeResponse qrCode(QrCodeDto dto) {
        return new QrCodeResponse(dto.activityId(), dto.token(),
                "data:image/png;base64," + Base64.getEncoder().encodeToString(dto.pngImage()), dto.validForSeconds());
    }

    static SurveyView survey(FeedbackSurvey s) {
        return new SurveyView(s.getId(), s.getActivityId(), s.getTitle(), s.isActive(), s.isAnonymousResponses(),
                s.identificationNotice(), s.getQuestions().stream().map(ApiMapper::question).toList());
    }

    static QuestionResponse question(SurveyQuestion q) {
        return new QuestionResponse(q.getId(), q.getQuestionText(), q.getType().name(), q.getType().getDescription(),
                q.getOptions(), q.isRequired());
    }

    static SubmittedEvaluation submitted(SurveyResponse r) {
        return new SubmittedEvaluation(r.getId(), r.getSurveyId(), r.getActivityId(), r.getSubmittedAt(), r.isAnonymous());
    }

    static ApiDtos.DeliveryResponse delivery(com.eventos.domain.model.CertificateDelivery d) {
        return new ApiDtos.DeliveryResponse(d.getId(), d.getCertificateId(), d.getRecipient(), d.getStatus().name(),
                d.getStatus().getDescription(), d.getDetail(), d.getAttemptedAt());
    }

    /** A verificação pública de autenticidade não expõe identificadores internos do titular. */
    static CertificateResponse certificate(Certificate c, boolean includeOwnerIds) {
        return new CertificateResponse(c.getId(), includeOwnerIds ? c.getEventId() : null,
                includeOwnerIds ? c.getUserId() : null, c.getParticipantName(), c.getEventTitle(),
                c.getCompletionHours(), c.getIssueDate(), c.getVerificationCode());
    }
}
