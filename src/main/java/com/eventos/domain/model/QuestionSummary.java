package com.eventos.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resultado consolidado de uma pergunta: quantidade de respostas, média (escala), distribuição
 * (escala e escolha única) e comentários (texto).
 */
public final class QuestionSummary {
    private final int answeredCount;
    private final double average;
    private final Map<String, Integer> distribution;
    private final List<String> comments;

    private QuestionSummary(int answeredCount, double average, Map<String, Integer> distribution, List<String> comments) {
        this.answeredCount = answeredCount;
        this.average = average;
        this.distribution = Collections.unmodifiableMap(new LinkedHashMap<>(distribution));
        this.comments = Collections.unmodifiableList(new ArrayList<>(comments));
    }

    public static QuestionSummary ofScale(int answeredCount, double average, Map<String, Integer> distribution) {
        return new QuestionSummary(answeredCount, average, distribution, List.of());
    }

    public static QuestionSummary ofChoices(int answeredCount, Map<String, Integer> distribution) {
        return new QuestionSummary(answeredCount, 0.0, distribution, List.of());
    }

    public static QuestionSummary ofComments(List<String> comments) {
        return new QuestionSummary(comments.size(), 0.0, Map.of(), comments);
    }

    public int getAnsweredCount() {
        return answeredCount;
    }

    public double getAverage() {
        return average;
    }

    public Map<String, Integer> getDistribution() {
        return distribution;
    }

    public List<String> getComments() {
        return comments;
    }
}
