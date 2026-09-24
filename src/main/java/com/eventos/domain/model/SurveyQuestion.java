package com.eventos.domain.model;

import com.eventos.domain.exceptions.ValidationException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Pergunta de questionário de avaliação com validação polimórfica (RF-24, RF-25, ROO-01).
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
        this.id = id;
        this.questionText = questionText.trim();
        this.type = type;
        this.options = options != null ? new ArrayList<>(options) : new ArrayList<>();
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
        if (required && (answer == null || answer.trim().isEmpty())) {
            throw new ValidationException("A pergunta '" + questionText + "' é de resposta obrigatória.");
        }
        if (answer == null || answer.trim().isEmpty()) {
            return;
        }

        if (type == QuestionType.RATING_SCALE) {
            try {
                int rating = Integer.parseInt(answer.trim());
                if (rating < 1 || rating > 5) {
                    throw new ValidationException("Nota de avaliação deve estar entre 1 e 5.");
                }
            } catch (NumberFormatException e) {
                throw new ValidationException("Resposta para escala numérica deve ser um número inteiro de 1 a 5.");
            }
        } else if (type == QuestionType.SINGLE_CHOICE && !options.isEmpty()) {
            if (!options.contains(answer.trim())) {
                throw new ValidationException("Opção selecionada inválida para a pergunta '" + questionText + "'.");
            }
        }
    }
}
