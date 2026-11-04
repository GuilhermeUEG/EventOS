package com.eventos.domain.model;

import com.eventos.domain.exceptions.ValidationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tipos de pergunta de um questionário. Cada constante sabe validar a própria definição, validar uma
 * resposta e consolidar respostas (ROO-05): acrescentar um tipo novo não exige alterar serviços nem
 * criar cadeias de if/switch por tipo.
 */
public enum QuestionType {
    TEXT("Resposta Textual") {
        @Override
        public void validateDefinition(List<String> options) {
            // Pergunta textual não tem opções.
        }

        @Override
        public void validateAnswer(String answer, List<String> options, String questionText) {
            if (answer.length() > 2000) {
                throw new ValidationException("A resposta da pergunta '" + questionText + "' excede 2000 caracteres.");
            }
        }

        @Override
        public QuestionSummary summarize(List<SurveyAnswer> answers, List<String> options) {
            List<String> comments = answers.stream()
                    .filter(a -> a.value() != null && !a.value().isBlank())
                    .map(a -> "[" + a.respondent() + "] " + a.value().trim())
                    .toList();
            return QuestionSummary.ofComments(comments);
        }
    },
    SINGLE_CHOICE("Escolha Única") {
        @Override
        public void validateDefinition(List<String> options) {
            long distinct = options.stream().filter(o -> o != null && !o.isBlank()).map(String::trim).distinct().count();
            if (distinct < 2) {
                throw new ValidationException("Pergunta de escolha única exige ao menos duas opções distintas.");
            }
        }

        @Override
        public void validateAnswer(String answer, List<String> options, String questionText) {
            if (!options.contains(answer.trim())) {
                throw new ValidationException("Opção selecionada inválida para a pergunta '" + questionText + "'.");
            }
        }

        @Override
        public QuestionSummary summarize(List<SurveyAnswer> answers, List<String> options) {
            Map<String, Integer> distribution = new LinkedHashMap<>();
            options.forEach(o -> distribution.put(o, 0));
            int answered = 0;
            for (SurveyAnswer a : answers) {
                if (a.value() != null && distribution.containsKey(a.value())) {
                    distribution.merge(a.value(), 1, Integer::sum);
                    answered++;
                }
            }
            return QuestionSummary.ofChoices(answered, distribution);
        }
    },
    RATING_SCALE("Escala Numérica (1 a 5)") {
        @Override
        public void validateDefinition(List<String> options) {
            // A escala é fixa de 1 a 5; opções informadas são ignoradas.
        }

        @Override
        public void validateAnswer(String answer, List<String> options, String questionText) {
            int rating;
            try {
                rating = Integer.parseInt(answer.trim());
            } catch (NumberFormatException e) {
                throw new ValidationException("Resposta para escala numérica deve ser um número inteiro de 1 a 5.");
            }
            if (rating < 1 || rating > 5) {
                throw new ValidationException("Nota de avaliação deve estar entre 1 e 5.");
            }
        }

        @Override
        public QuestionSummary summarize(List<SurveyAnswer> answers, List<String> options) {
            Map<String, Integer> distribution = new LinkedHashMap<>();
            for (int i = 1; i <= 5; i++) distribution.put(String.valueOf(i), 0);
            double sum = 0;
            int count = 0;
            for (SurveyAnswer a : answers) {
                if (a.value() == null || !distribution.containsKey(a.value().trim())) continue;
                distribution.merge(a.value().trim(), 1, Integer::sum);
                sum += Integer.parseInt(a.value().trim());
                count++;
            }
            return QuestionSummary.ofScale(count, count == 0 ? 0.0 : sum / count, distribution);
        }
    };

    private final String description;

    QuestionType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public abstract void validateDefinition(List<String> options);

    /** Chamado apenas com resposta não vazia; a obrigatoriedade é verificada pela pergunta. */
    public abstract void validateAnswer(String answer, List<String> options, String questionText);

    public abstract QuestionSummary summarize(List<SurveyAnswer> answers, List<String> options);
}
