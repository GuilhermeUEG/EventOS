package com.eventos.application.ports.input;

import com.eventos.application.dtos.SurveyResultsDto;
import com.eventos.domain.model.EvaluationEligibility;
import com.eventos.domain.model.FeedbackSurvey;
import com.eventos.domain.model.SurveyResponse;
import java.util.Map;

public interface SurveyUseCase {
    FeedbackSurvey createSurvey(FeedbackSurvey survey);

    FeedbackSurvey getSurveyByActivity(Long activityId);

    /** Elegibilidade para avaliar, sempre com o motivo quando negada (RF-26, RN-13). */
    EvaluationEligibility checkEvaluationEligibility(Long userId, Long activityId);

    default boolean canUserEvaluate(Long userId, Long activityId) {
        return checkEvaluationEligibility(userId, activityId).eligible();
    }

    /** Envia a avaliação; a política de anonimato vem do questionário, não do cliente (RN-14). */
    SurveyResponse submitResponse(Long activityId, Long userId, Map<Long, String> answers);

    SurveyResultsDto getConsolidatedResults(Long activityId);
}
