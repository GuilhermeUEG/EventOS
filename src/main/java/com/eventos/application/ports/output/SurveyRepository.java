package com.eventos.application.ports.output;

import com.eventos.domain.model.FeedbackSurvey;
import com.eventos.domain.model.SurveyResponse;
import java.util.List;
import java.util.Optional;

public interface SurveyRepository {
    FeedbackSurvey saveSurvey(FeedbackSurvey survey);
    Optional<FeedbackSurvey> findSurveyByActivityId(Long activityId);
    SurveyResponse saveResponse(SurveyResponse response);
    Optional<SurveyResponse> findResponseByActivityAndUser(Long activityId, Long userId);
    List<SurveyResponse> findResponsesByActivityId(Long activityId);
}
