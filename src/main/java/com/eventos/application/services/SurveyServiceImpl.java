package com.eventos.application.services;

import com.eventos.application.dtos.SurveyResultsDto;
import com.eventos.application.ports.input.SurveyUseCase;
import com.eventos.application.ports.output.ActivityRepository;
import com.eventos.application.ports.output.AttendanceRepository;
import com.eventos.application.ports.output.EventRepository;
import com.eventos.application.ports.output.RegistrationRepository;
import com.eventos.application.ports.output.SurveyRepository;
import com.eventos.domain.exceptions.BusinessRuleException;
import com.eventos.domain.exceptions.EntityNotFoundException;
import com.eventos.domain.model.Activity;
import com.eventos.domain.model.AttendanceStatus;
import com.eventos.domain.model.EvaluationEligibility;
import com.eventos.domain.model.Event;
import com.eventos.domain.model.FeedbackSurvey;
import com.eventos.domain.model.QuestionSummary;
import com.eventos.domain.model.SurveyQuestion;
import com.eventos.domain.model.SurveyResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SurveyServiceImpl implements SurveyUseCase {
    private final SurveyRepository surveyRepository;
    private final ActivityRepository activityRepository;
    private final RegistrationRepository registrationRepository;
    private final AttendanceRepository attendanceRepository;
    private final EventRepository eventRepository;

    public SurveyServiceImpl(SurveyRepository surveyRepository,
                             ActivityRepository activityRepository,
                             RegistrationRepository registrationRepository,
                             AttendanceRepository attendanceRepository,
                             EventRepository eventRepository) {
        this.surveyRepository = surveyRepository;
        this.activityRepository = activityRepository;
        this.registrationRepository = registrationRepository;
        this.attendanceRepository = attendanceRepository;
        this.eventRepository = eventRepository;
    }

    @Override
    public synchronized FeedbackSurvey createSurvey(FeedbackSurvey survey) {
        findActivity(survey.getActivityId());
        if (surveyRepository.findSurveyByActivityId(survey.getActivityId()).isPresent()) {
            throw new BusinessRuleException("Esta atividade já possui um questionário de avaliação.");
        }
        return surveyRepository.saveSurvey(survey);
    }

    @Override
    public FeedbackSurvey getSurveyByActivity(Long activityId) {
        return surveyRepository.findSurveyByActivityId(activityId)
                .orElseThrow(() -> new EntityNotFoundException("Nenhum questionário de avaliação configurado para esta atividade."));
    }

    @Override
    public EvaluationEligibility checkEvaluationEligibility(Long userId, Long activityId) {
        Activity activity = activityRepository.findById(activityId).orElse(null);
        if (activity == null) {
            return EvaluationEligibility.denied("Atividade não encontrada.");
        }
        FeedbackSurvey survey = surveyRepository.findSurveyByActivityId(activityId).orElse(null);
        if (survey == null) {
            return EvaluationEligibility.denied("Esta atividade ainda não possui questionário de avaliação.");
        }
        Event event = eventRepository.findById(activity.getEventId()).orElse(null);
        boolean registered = event != null && registrationRepository.findByEventAndUser(activity.getEventId(), userId)
                .map(reg -> event.admits(reg, activity))
                .orElse(false);
        AttendanceStatus attendance = activity.evaluateAttendance(attendanceRepository.findByActivityAndUser(activityId, userId));
        boolean alreadyResponded = surveyRepository.findResponseByActivityAndUser(activityId, userId).isPresent();
        return survey.checkEligibility(registered, attendance, alreadyResponded);
    }

    @Override
    public synchronized SurveyResponse submitResponse(Long activityId, Long userId, Map<Long, String> answers) {
        findActivity(activityId);
        FeedbackSurvey survey = surveyRepository.findSurveyByActivityId(activityId)
                .orElseThrow(() -> new EntityNotFoundException("Nenhum questionário de avaliação configurado para esta atividade."));
        EvaluationEligibility eligibility = checkEvaluationEligibility(userId, activityId);
        if (!eligibility.eligible()) {
            throw new BusinessRuleException("Avaliação bloqueada: " + eligibility.reason());
        }
        survey.validateAnswers(answers);
        return surveyRepository.saveResponse(new SurveyResponse(null, survey.getId(), activityId, userId,
                survey.isAnonymousResponses(), answers, LocalDateTime.now()));
    }

    @Override
    public SurveyResultsDto getConsolidatedResults(Long activityId) {
        Activity activity = findActivity(activityId);
        FeedbackSurvey survey = surveyRepository.findSurveyByActivityId(activityId).orElse(null);
        if (survey == null) {
            return new SurveyResultsDto(activityId, activity.getTitle(), "Sem avaliação", 0, 0.0,
                    false, "", new ArrayList<>(), new ArrayList<>());
        }
        List<SurveyResponse> responses = surveyRepository.findResponsesByActivityId(activityId);
        Map<SurveyQuestion, QuestionSummary> summaries = survey.summarize(responses);

        List<SurveyResultsDto.QuestionMetricDto> metrics = new ArrayList<>();
        List<String> comments = new ArrayList<>();
        double averagesSum = 0.0;
        int averagesCount = 0;
        for (Map.Entry<SurveyQuestion, QuestionSummary> entry : summaries.entrySet()) {
            SurveyQuestion q = entry.getKey();
            QuestionSummary s = entry.getValue();
            metrics.add(new SurveyResultsDto.QuestionMetricDto(q.getId(), q.getQuestionText(), q.getType().name(),
                    s.getAnsweredCount(), s.getAverage(), s.getDistribution().isEmpty() ? null : s.getDistribution()));
            comments.addAll(s.getComments());
            if (s.getAverage() > 0) {
                averagesSum += s.getAverage();
                averagesCount++;
            }
        }
        double overall = averagesCount > 0 ? averagesSum / averagesCount : 0.0;
        return new SurveyResultsDto(activityId, activity.getTitle(), survey.getTitle(), responses.size(), overall,
                survey.isAnonymousResponses(), survey.identificationNotice(), metrics, comments);
    }

    private Activity findActivity(Long activityId) {
        return activityRepository.findById(activityId)
                .orElseThrow(() -> new EntityNotFoundException("Atividade não encontrada."));
    }
}
