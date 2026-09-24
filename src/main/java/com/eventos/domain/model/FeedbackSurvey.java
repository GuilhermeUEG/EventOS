package com.eventos.domain.model;

import com.eventos.domain.exceptions.ValidationException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Questionário de avaliação de atividade (RF-24, ROO-01).
 */
public class FeedbackSurvey implements Serializable {
    private final Long id;
    private final Long activityId;
    private String title;
    private final List<SurveyQuestion> questions;
    private boolean active;

    public FeedbackSurvey(Long id, Long activityId, String title, List<SurveyQuestion> questions, boolean active) {
        if (activityId == null) throw new ValidationException("ID da atividade é obrigatório para o questionário.");
        if (title == null || title.trim().isEmpty()) throw new ValidationException("Título do questionário é obrigatório.");
        this.id = id;
        this.activityId = activityId;
        this.title = title.trim();
        this.questions = questions != null ? new ArrayList<>(questions) : new ArrayList<>();
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public Long getActivityId() {
        return activityId;
    }

    public String getTitle() {
        return title;
    }

    public List<SurveyQuestion> getQuestions() {
        return Collections.unmodifiableList(questions);
    }

    public boolean isActive() {
        return active;
    }

    public void addQuestion(SurveyQuestion question) {
        if (question == null) throw new ValidationException("Questão não pode ser nula.");
        this.questions.add(question);
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
