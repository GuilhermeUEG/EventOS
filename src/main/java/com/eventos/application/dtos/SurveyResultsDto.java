package com.eventos.application.dtos;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public class SurveyResultsDto implements Serializable {
    private Long activityId;
    private String activityTitle;
    private String surveyTitle;
    private int totalResponses;
    private double averageRating;
    private List<QuestionMetricDto> questionMetrics;
    private List<String> textComments;

    public SurveyResultsDto() {}

    public SurveyResultsDto(Long activityId, String activityTitle, String surveyTitle,
                            int totalResponses, double averageRating,
                            List<QuestionMetricDto> questionMetrics, List<String> textComments) {
        this.activityId = activityId;
        this.activityTitle = activityTitle;
        this.surveyTitle = surveyTitle;
        this.totalResponses = totalResponses;
        this.averageRating = averageRating;
        this.questionMetrics = questionMetrics;
        this.textComments = textComments;
    }

    public Long getActivityId() { return activityId; }
    public String getActivityTitle() { return activityTitle; }
    public String getSurveyTitle() { return surveyTitle; }
    public int getTotalResponses() { return totalResponses; }
    public double getAverageRating() { return averageRating; }
    public List<QuestionMetricDto> getQuestionMetrics() { return questionMetrics; }
    public List<String> getTextComments() { return textComments; }

    public static class QuestionMetricDto implements Serializable {
        private Long questionId;
        private String questionText;
        private String questionType;
        private double averageScore;
        private Map<String, Integer> optionDistribution;

        public QuestionMetricDto() {}

        public QuestionMetricDto(Long questionId, String questionText, String questionType,
                                 double averageScore, Map<String, Integer> optionDistribution) {
            this.questionId = questionId;
            this.questionText = questionText;
            this.questionType = questionType;
            this.averageScore = averageScore;
            this.optionDistribution = optionDistribution;
        }

        public Long getQuestionId() { return questionId; }
        public String getQuestionText() { return questionText; }
        public String getQuestionType() { return questionType; }
        public double getAverageScore() { return averageScore; }
        public Map<String, Integer> getOptionDistribution() { return optionDistribution; }
    }
}
