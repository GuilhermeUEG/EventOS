package com.eventos.domain.model;

import com.eventos.domain.exceptions.ValidationException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Pergunta de questionário de avaliação (RF-24, RF-25, ROO-01). A validação e a consolidação
 * específicas de cada tipo são delegadas ao {@link QuestionType}.
 */
public class SurveyQuestion implements Serializable {
    private final Long id;
    private final String questionText;
    private final QuestionType type;
    private final List<String> options; // para SINGLE_CHOICE
    private final boolean required;

    public SurveyQuestion(Long id, String questionText, QuestionType type, List<String> options, boolean required) {
        if (questionText == null || questionText.trim().isEmpty()) {
            throw new ValidationException("O enunciado da questão não pode ser vazio.");
        }
        if (type == null) {
            throw new ValidationException("O tipo da questão é obrigatório.");
        }
        List<String> cleanOptions = new ArrayList<>();
        if (options != null) {
            options.stream().filter(o -> o != null && !o.isBlank()).map(String::trim).forEach(cleanOptions::add);
        }
        type.validateDefinition(cleanOptions);
        this.id = id;
        this.questionText = questionText.trim();
        this.type = type;
        this.options = cleanOptions;
        this.required = required;
    }

    public Long getId() {
        return id;
    }

    public String getQuestionText() {
        return questionText;
    }

    public QuestionType getType() {
        return type;
    }

    public List<String> getOptions() {
        return new ArrayList<>(options);
    }

    public boolean isRequired() {
        return required;
    }

    public void validateAnswer(String answer) {
        boolean blank = answer == null || answer.trim().isEmpty();
        if (blank) {
            if (required) {
                throw new ValidationException("A pergunta '" + questionText + "' é de resposta obrigatória.");
            }
            return;
        }
        type.validateAnswer(answer.trim(), options, questionText);
    }

    public QuestionSummary summarize(List<SurveyAnswer> answers) {
        return type.summarize(answers, options);
    }
}
