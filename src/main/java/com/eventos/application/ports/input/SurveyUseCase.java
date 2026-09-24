package com.eventos.application.ports.input;

import com.eventos.application.dtos.SurveyResultsDto;
import com.eventos.domain.model.FeedbackSurvey;
import com.eventos.domain.model.SurveyResponse;

public interface SurveyUseCase {
    FeedbackSurvey createSurvey(FeedbackSurvey survey);
    FeedbackSurvey getSurveyByActivity(Long activityId);
    boolean canUserEvaluate(Long userId, Long activityId);
    SurveyResponse submitResponse(SurveyResponse response);
    SurveyResultsDto getConsolidatedResults(Long activityId);
}
