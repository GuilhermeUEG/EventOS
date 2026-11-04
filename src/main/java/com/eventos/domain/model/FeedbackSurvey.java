package com.eventos.domain.model;

import com.eventos.domain.exceptions.ValidationException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Questionário de avaliação de uma atividade (RF-24, RF-26, RF-27, RN-13, RN-14, ROO-01).
 * Conhece a própria política de identificação das respostas e quem pode respondê-lo.
 */
public class FeedbackSurvey implements Serializable {
    private final Long id;
    private final Long activityId;
    private final String title;
    private final List<SurveyQuestion> questions;
    private boolean active;
    private final boolean anonymousResponses;

    public FeedbackSurvey(Long id, Long activityId, String title, List<SurveyQuestion> questions,
                          boolean active, boolean anonymousResponses) {
        if (activityId == null) throw new ValidationException("ID da atividade é obrigatório para o questionário.");
        if (title == null || title.trim().isEmpty()) throw new ValidationException("Título do questionário é obrigatório.");
        if (questions == null || questions.isEmpty()) {
            throw new ValidationException("O questionário precisa ter ao menos uma pergunta.");
        }
        this.id = id;
        this.activityId = activityId;
        this.title = title.trim();
        this.questions = new ArrayList<>(questions);
        this.active = active;
        this.anonymousResponses = anonymousResponses;
    }

    public FeedbackSurvey(Long id, Long activityId, String title, List<SurveyQuestion> questions, boolean active) {
        this(id, activityId, title, questions, active, false);
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

    public boolean isAnonymousResponses() {
        return anonymousResponses;
    }

    public void deactivate() {
        this.active = false;
    }

    /** Texto exibido ao participante antes do envio (RN-14). */
    public String identificationNotice() {
        return anonymousResponses
                ? "As respostas deste questionário são anônimas: o organizador não verá quem respondeu."
                : "As respostas deste questionário são identificadas: o organizador poderá ver quem respondeu.";
    }

    /** Valida todas as respostas contra as perguntas; recusa respostas a perguntas inexistentes. */
    public void validateAnswers(Map<Long, String> answers) {
        Map<Long, String> given = answers == null ? Map.of() : answers;
        Set<Long> known = new java.util.HashSet<>();
        for (SurveyQuestion q : questions) {
            known.add(q.getId());
            q.validateAnswer(given.get(q.getId()));
        }
        for (Long questionId : given.keySet()) {
            if (!known.contains(questionId)) {
                throw new ValidationException("A resposta enviada se refere a uma pergunta inexistente neste questionário.");
            }
        }
    }

    public EvaluationEligibility checkEligibility(boolean registeredForActivity, AttendanceStatus attendance,
                                                  boolean alreadyResponded) {
        if (!active) {
            return EvaluationEligibility.denied("O questionário desta atividade está encerrado.");
        }
        if (!registeredForActivity) {
            return EvaluationEligibility.denied("Somente participantes inscritos nesta atividade podem avaliá-la.");
        }
        if (attendance != AttendanceStatus.PRESENT) {
            return EvaluationEligibility.denied("A avaliação só é liberada após a validação da sua presença nesta atividade.");
        }
        if (alreadyResponded) {
            return EvaluationEligibility.denied("Você já enviou sua avaliação desta atividade.");
        }
        return EvaluationEligibility.allowed();
    }

    /** Consolida as respostas pergunta a pergunta, preservando a ordem do questionário. */
    public Map<SurveyQuestion, QuestionSummary> summarize(List<SurveyResponse> responses) {
        Map<SurveyQuestion, QuestionSummary> result = new LinkedHashMap<>();
        for (SurveyQuestion q : questions) {
            List<SurveyAnswer> answers = responses.stream()
                    .map(r -> new SurveyAnswer(r.respondentLabel(), r.answerFor(q.getId())))
                    .toList();
            result.put(q, q.summarize(answers));
        }
        return result;
    }
}
