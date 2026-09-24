package com.eventos.application.services;

import com.eventos.application.dtos.SurveyResultsDto;
import com.eventos.application.ports.input.SurveyUseCase;
import com.eventos.application.ports.output.ActivityRepository;
import com.eventos.application.ports.output.AttendanceRepository;
import com.eventos.application.ports.output.RegistrationRepository;
import com.eventos.application.ports.output.SurveyRepository;
import com.eventos.application.ports.output.UserRepository;
import com.eventos.domain.exceptions.BusinessRuleException;
import com.eventos.domain.exceptions.EntityNotFoundException;
import com.eventos.domain.model.Activity;
import com.eventos.domain.model.AttendanceRecord;
import com.eventos.domain.model.AttendanceStatus;
import com.eventos.domain.model.FeedbackSurvey;
import com.eventos.domain.model.QuestionType;
import com.eventos.domain.model.Registration;
import com.eventos.domain.model.SurveyQuestion;
import com.eventos.domain.model.SurveyResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SurveyServiceImpl implements SurveyUseCase {
    private final SurveyRepository surveyRepository;
    private final ActivityRepository activityRepository;
    private final RegistrationRepository registrationRepository;
    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;

    public SurveyServiceImpl(SurveyRepository surveyRepository,
                             ActivityRepository activityRepository,
                             RegistrationRepository registrationRepository,
                             AttendanceRepository attendanceRepository,
                             UserRepository userRepository) {
        this.surveyRepository = surveyRepository;
        this.activityRepository = activityRepository;
        this.registrationRepository = registrationRepository;
        this.attendanceRepository = attendanceRepository;
        this.userRepository = userRepository;
    }

    @Override
    public FeedbackSurvey createSurvey(FeedbackSurvey survey) {
        Activity act = activityRepository.findById(survey.getActivityId())
                .orElseThrow(() -> new EntityNotFoundException("Atividade não encontrada."));
        return surveyRepository.saveSurvey(survey);
    }

    @Override
    public FeedbackSurvey getSurveyByActivity(Long activityId) {
        return surveyRepository.findSurveyByActivityId(activityId)
                .orElseThrow(() -> new EntityNotFoundException("Nenhum questionário de avaliação configurado para esta atividade."));
    }

    @Override
    public boolean canUserEvaluate(Long userId, Long activityId) {
        Activity activity = activityRepository.findById(activityId).orElse(null);
        if (activity == null) return false;

        // 1. Inscrito no evento?
        Optional<Registration> reg = registrationRepository.findByEventAndUser(activity.getEventId(), userId);
        if (reg.isEmpty() || !reg.get().isConfirmed()) return false;

        // 2. Presença validada? (RF-26, RN-13)
        List<AttendanceRecord> records = attendanceRepository.findByActivityAndUser(activityId, userId);
        AttendanceStatus status = activity.evaluateAttendance(records);
        if (status != AttendanceStatus.PRESENT) return false;

        // 3. Já respondeu? (RF-27, RN-14)
        Optional<SurveyResponse> resp = surveyRepository.findResponseByActivityAndUser(activityId, userId);
        return resp.isEmpty();
    }

    @Override
    public SurveyResponse submitResponse(SurveyResponse response) {
        Activity activity = activityRepository.findById(response.getActivityId())
                .orElseThrow(() -> new EntityNotFoundException("Atividade não encontrada."));
        FeedbackSurvey survey = surveyRepository.findSurveyByActivityId(response.getActivityId())
                .orElseThrow(() -> new EntityNotFoundException("Questionário não encontrado."));

        // Regra Central: Apenas participante inscrito e com presença validada pode avaliar (RF-26, RN-13)
        List<AttendanceRecord> records = attendanceRepository.findByActivityAndUser(response.getActivityId(), response.getUserId());
        AttendanceStatus status = activity.evaluateAttendance(records);
        if (status != AttendanceStatus.PRESENT) {
            throw new BusinessRuleException("Avaliação bloqueada: Você só pode avaliar esta atividade após ter sua presença validada (RF-26).");
        }

        // Regra: Uma única resposta por participante (RF-27, RN-14)
        Optional<SurveyResponse> existing = surveyRepository.findResponseByActivityAndUser(response.getActivityId(), response.getUserId());
        if (existing.isPresent()) {
            throw new BusinessRuleException("Você já enviou uma resposta para a avaliação desta atividade.");
        }

        // Validação polimórfica das respostas
        for (SurveyQuestion q : survey.getQuestions()) {
            String ans = response.getAnswersByQuestionId().get(q.getId());
            q.validateAnswer(ans);
        }

        return surveyRepository.saveResponse(response);
    }

    @Override
    public SurveyResultsDto getConsolidatedResults(Long activityId) {
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new EntityNotFoundException("Atividade não encontrada."));
        FeedbackSurvey survey = surveyRepository.findSurveyByActivityId(activityId)
                .orElse(null);

        List<SurveyResponse> responses = surveyRepository.findResponsesByActivityId(activityId);
        int total = responses.size();
        if (survey == null || total == 0) {
            return new SurveyResultsDto(activityId, activity.getTitle(),
                    survey != null ? survey.getTitle() : "Sem avaliação", 0, 0.0, new ArrayList<>(), new ArrayList<>());
        }

        List<SurveyResultsDto.QuestionMetricDto> metrics = new ArrayList<>();
        List<String> comments = new ArrayList<>();
        double overallScoreSum = 0.0;
        int ratingQuestionsCount = 0;

        for (SurveyQuestion q : survey.getQuestions()) {
            if (q.getType() == QuestionType.RATING_SCALE) {
                double sum = 0;
                int count = 0;
                for (SurveyResponse r : responses) {
                    String ans = r.getAnswersByQuestionId().get(q.getId());
                    if (ans != null && !ans.trim().isEmpty()) {
                        try {
                            sum += Double.parseDouble(ans.trim());
                            count++;
                        } catch (Exception ignored) {}
                    }
                }
                double avg = count > 0 ? (sum / count) : 0.0;
                metrics.add(new SurveyResultsDto.QuestionMetricDto(q.getId(), q.getQuestionText(), q.getType().name(), avg, null));
                overallScoreSum += avg;
                ratingQuestionsCount++;
            } else if (q.getType() == QuestionType.SINGLE_CHOICE) {
                Map<String, Integer> dist = new HashMap<>();
                for (String opt : q.getOptions()) dist.put(opt, 0);
                for (SurveyResponse r : responses) {
                    String ans = r.getAnswersByQuestionId().get(q.getId());
                    if (ans != null && dist.containsKey(ans)) {
                        dist.put(ans, dist.get(ans) + 1);
                    }
                }
                metrics.add(new SurveyResultsDto.QuestionMetricDto(q.getId(), q.getQuestionText(), q.getType().name(), 0.0, dist));
            } else if (q.getType() == QuestionType.TEXT) {
                for (SurveyResponse r : responses) {
                    String ans = r.getAnswersByQuestionId().get(q.getId());
                    if (ans != null && !ans.trim().isEmpty()) {
                        String prefix = r.isAnonymous() ? "[Anônimo] " : "[Participante #" + r.getUserId() + "] ";
                        comments.add(prefix + ans);
                    }
                }
                metrics.add(new SurveyResultsDto.QuestionMetricDto(q.getId(), q.getQuestionText(), q.getType().name(), 0.0, null));
            }
        }

        double finalAverage = ratingQuestionsCount > 0 ? (overallScoreSum / ratingQuestionsCount) : 0.0;
        return new SurveyResultsDto(activityId, activity.getTitle(), survey.getTitle(), total, finalAverage, metrics, comments);
    }
}
