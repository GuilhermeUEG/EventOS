package com.eventos.domain.model;

import com.eventos.domain.exceptions.ValidationException;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Resposta submetida a um questionário de avaliação (RF-26, RF-27, RN-14, RN-15).
 */
public class SurveyResponse implements Serializable {
    private final Long id;
    private final Long surveyId;
    private final Long activityId;
    private final Long userId;
    private final boolean anonymous; // RN-15
    private final Map<Long, String> answersByQuestionId; // questionId -> answer text
    private final LocalDateTime submittedAt;

    public SurveyResponse(Long id, Long surveyId, Long activityId, Long userId,
                          boolean anonymous, Map<Long, String> answersByQuestionId, LocalDateTime submittedAt) {
        if (surveyId == null) throw new ValidationException("ID do questionário é obrigatório.");
        if (activityId == null) throw new ValidationException("ID da atividade é obrigatório.");
        if (userId == null) throw new ValidationException("ID do usuário é obrigatório.");
        this.id = id;
        this.surveyId = surveyId;
        this.activityId = activityId;
        this.userId = userId;
        this.anonymous = anonymous;
        this.answersByQuestionId = answersByQuestionId != null ? new HashMap<>(answersByQuestionId) : new HashMap<>();
        this.submittedAt = submittedAt != null ? submittedAt : LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getSurveyId() {
        return surveyId;
    }

    public Long getActivityId() {
        return activityId;
    }

    public Long getUserId() {
        return userId;
    }

    public boolean isAnonymous() {
        return anonymous;
    }

    public Map<Long, String> getAnswersByQuestionId() {
        return Collections.unmodifiableMap(answersByQuestionId);
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }
}
