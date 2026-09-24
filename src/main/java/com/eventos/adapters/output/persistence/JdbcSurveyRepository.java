package com.eventos.adapters.output.persistence;

import com.eventos.application.ports.output.SurveyRepository;
import com.eventos.domain.model.FeedbackSurvey;
import com.eventos.domain.model.QuestionType;
import com.eventos.domain.model.SurveyQuestion;
import com.eventos.domain.model.SurveyResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class JdbcSurveyRepository implements SurveyRepository {
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public FeedbackSurvey saveSurvey(FeedbackSurvey survey) {
        if (survey.getId() == null) {
            String sql = "INSERT INTO feedback_surveys (activity_id, title, active) VALUES (?, ?, ?)";
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setLong(1, survey.getActivityId());
                stmt.setString(2, survey.getTitle());
                stmt.setBoolean(3, survey.isActive());
                stmt.executeUpdate();
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        long surveyId = rs.getLong(1);
                        saveQuestions(conn, surveyId, survey.getQuestions());
                        return findSurveyByActivityId(survey.getActivityId()).orElse(survey);
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("Erro ao criar questionário: " + e.getMessage(), e);
            }
        }
        return survey;
    }

    @Override
    public Optional<FeedbackSurvey> findSurveyByActivityId(Long activityId) {
        String sql = "SELECT * FROM feedback_surveys WHERE activity_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, activityId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    long id = rs.getLong("id");
                    List<SurveyQuestion> questions = findQuestions(conn, id);
                    return Optional.of(new FeedbackSurvey(id, activityId, rs.getString("title"), questions, rs.getBoolean("active")));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar questionário: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public SurveyResponse saveResponse(SurveyResponse response) {
        String sql = "INSERT INTO survey_responses (survey_id, activity_id, user_id, anonymous, answers_json, submitted_at) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setLong(1, response.getSurveyId());
            stmt.setLong(2, response.getActivityId());
            stmt.setLong(3, response.getUserId());
            stmt.setBoolean(4, response.isAnonymous());
            stmt.setString(5, mapper.writeValueAsString(response.getAnswersByQuestionId()));
            stmt.setTimestamp(6, Timestamp.valueOf(response.getSubmittedAt()));
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return new SurveyResponse(rs.getLong(1), response.getSurveyId(), response.getActivityId(),
                            response.getUserId(), response.isAnonymous(), response.getAnswersByQuestionId(), response.getSubmittedAt());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro ao salvar resposta da avaliação: " + e.getMessage(), e);
        }
        return response;
    }

    @Override
    public Optional<SurveyResponse> findResponseByActivityAndUser(Long activityId, Long userId) {
        String sql = "SELECT * FROM survey_responses WHERE activity_id = ? AND user_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, activityId);
            stmt.setLong(2, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapResponseRow(rs));
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro ao verificar resposta de avaliação: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public List<SurveyResponse> findResponsesByActivityId(Long activityId) {
        List<SurveyResponse> list = new ArrayList<>();
        String sql = "SELECT * FROM survey_responses WHERE activity_id = ? ORDER BY submitted_at ASC";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, activityId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) list.add(mapResponseRow(rs));
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro ao listar respostas da atividade: " + e.getMessage(), e);
        }
        return list;
    }

    private void saveQuestions(Connection conn, Long surveyId, List<SurveyQuestion> questions) throws SQLException {
        if (questions == null || questions.isEmpty()) return;
        String sql = "INSERT INTO survey_questions (survey_id, question_text, question_type, options_csv, required) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (SurveyQuestion q : questions) {
                stmt.setLong(1, surveyId);
                stmt.setString(2, q.getQuestionText());
                stmt.setString(3, q.getType().name());
                stmt.setString(4, String.join(";;", q.getOptions()));
                stmt.setBoolean(5, q.isRequired());
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    private List<SurveyQuestion> findQuestions(Connection conn, Long surveyId) throws SQLException {
        List<SurveyQuestion> list = new ArrayList<>();
        String sql = "SELECT * FROM survey_questions WHERE survey_id = ? ORDER BY id ASC";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, surveyId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String opts = rs.getString("options_csv");
                    List<String> optList = (opts != null && !opts.trim().isEmpty())
                            ? Arrays.asList(opts.split(";;"))
                            : new ArrayList<>();
                    list.add(new SurveyQuestion(
                            rs.getLong("id"),
                            rs.getString("question_text"),
                            QuestionType.valueOf(rs.getString("question_type")),
                            optList,
                            rs.getBoolean("required")
                    ));
                }
            }
        }
        return list;
    }

    private SurveyResponse mapResponseRow(ResultSet rs) throws Exception {
        String json = rs.getString("answers_json");
        Map<Long, String> answers = mapper.readValue(json, new TypeReference<HashMap<Long, String>>() {});
        return new SurveyResponse(
                rs.getLong("id"),
                rs.getLong("survey_id"),
                rs.getLong("activity_id"),
                rs.getLong("user_id"),
                rs.getBoolean("anonymous"),
                answers,
                rs.getTimestamp("submitted_at").toLocalDateTime()
        );
    }
}
