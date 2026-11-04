package com.eventos.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eventos.domain.exceptions.ValidationException;
import com.eventos.domain.model.AttendanceStatus;
import com.eventos.domain.model.EvaluationEligibility;
import com.eventos.domain.model.FeedbackSurvey;
import com.eventos.domain.model.QuestionSummary;
import com.eventos.domain.model.QuestionType;
import com.eventos.domain.model.SurveyAnswer;
import com.eventos.domain.model.SurveyQuestion;
import com.eventos.domain.model.SurveyResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Questionários: validação e consolidação polimórficas por tipo (ROO-05)")
class SurveyDomainTest {
    private final SurveyQuestion rating = new SurveyQuestion(1L, "Nota geral?", QuestionType.RATING_SCALE, List.of(), true);
    private final SurveyQuestion choice = new SurveyQuestion(2L, "Gostou?", QuestionType.SINGLE_CHOICE, List.of("Sim", "Não"), true);
    private final SurveyQuestion text = new SurveyQuestion(3L, "Comentários", QuestionType.TEXT, List.of(), false);

    private FeedbackSurvey survey(boolean anonymous) {
        return new FeedbackSurvey(10L, 5L, "Avaliação", List.of(rating, choice, text), true, anonymous);
    }

    @Test
    @DisplayName("escala numérica aceita 1 a 5 e recusa o resto")
    void ratingValidation() {
        rating.validateAnswer("1");
        rating.validateAnswer(" 5 ");
        assertThrows(ValidationException.class, () -> rating.validateAnswer("0"));
        assertThrows(ValidationException.class, () -> rating.validateAnswer("6"));
        assertThrows(ValidationException.class, () -> rating.validateAnswer("ótimo"));
        assertThrows(ValidationException.class, () -> rating.validateAnswer("3.5"));
    }

    @Test
    @DisplayName("escolha única só aceita uma das opções definidas")
    void choiceValidation() {
        choice.validateAnswer("Sim");
        assertThrows(ValidationException.class, () -> choice.validateAnswer("Talvez"));
    }

    @Test
    @DisplayName("pergunta obrigatória recusa resposta vazia; opcional aceita")
    void requiredness() {
        assertThrows(ValidationException.class, () -> rating.validateAnswer(null));
        assertThrows(ValidationException.class, () -> choice.validateAnswer("  "));
        text.validateAnswer(null);
        text.validateAnswer("");
    }

    @Test
    @DisplayName("a definição da pergunta é validada pelo tipo: escolha única exige duas opções")
    void definitionValidation() {
        assertThrows(ValidationException.class, () -> new SurveyQuestion(null, "Q", QuestionType.SINGLE_CHOICE, List.of("Só uma"), true));
        assertThrows(ValidationException.class, () -> new SurveyQuestion(null, "Q", QuestionType.SINGLE_CHOICE, List.of("A", " A", ""), true));
        assertThrows(ValidationException.class, () -> new SurveyQuestion(null, " ", QuestionType.TEXT, List.of(), true));
        assertThrows(ValidationException.class, () -> new SurveyQuestion(null, "Q", null, List.of(), true));
        assertEquals(List.of("A", "B"), new SurveyQuestion(null, "Q", QuestionType.SINGLE_CHOICE, List.of(" A ", "B", ""), true).getOptions());
    }

    @Test
    @DisplayName("o questionário precisa de título e de ao menos uma pergunta")
    void surveyInvariants() {
        assertThrows(ValidationException.class, () -> new FeedbackSurvey(null, 5L, "Título", List.of(), true));
        assertThrows(ValidationException.class, () -> new FeedbackSurvey(null, 5L, " ", List.of(rating), true));
        assertThrows(ValidationException.class, () -> new FeedbackSurvey(null, null, "Título", List.of(rating), true));
    }

    @Test
    @DisplayName("respostas a perguntas inexistentes são recusadas")
    void unknownQuestionIsRejected() {
        FeedbackSurvey s = survey(false);
        s.validateAnswers(Map.of(1L, "4", 2L, "Sim"));
        assertThrows(ValidationException.class, () -> s.validateAnswers(Map.of(1L, "4", 2L, "Sim", 99L, "x")));
        assertThrows(ValidationException.class, () -> s.validateAnswers(Map.of(1L, "4")), "falta pergunta obrigatória");
    }

    @Test
    @DisplayName("consolida média e distribuição da escala, contagem das escolhas e comentários")
    void summaries() {
        QuestionSummary scale = rating.summarize(List.of(new SurveyAnswer("A", "5"), new SurveyAnswer("B", "3"), new SurveyAnswer("C", null)));
        assertEquals(2, scale.getAnsweredCount());
        assertEquals(4.0, scale.getAverage(), 0.0001);
        assertEquals(1, scale.getDistribution().get("5"));
        assertEquals(0, scale.getDistribution().get("1"));

        QuestionSummary choices = choice.summarize(List.of(new SurveyAnswer("A", "Sim"), new SurveyAnswer("B", "Sim"), new SurveyAnswer("C", "Não")));
        assertEquals(2, choices.getDistribution().get("Sim"));
        assertEquals(1, choices.getDistribution().get("Não"));

        QuestionSummary comments = text.summarize(List.of(new SurveyAnswer("Anônimo", "Muito bom"), new SurveyAnswer("X", " ")));
        assertEquals(List.of("[Anônimo] Muito bom"), comments.getComments());
    }

    @Test
    @DisplayName("a política de anonimato do questionário define como o respondente aparece (RN-14)")
    void anonymityPolicy() {
        SurveyResponse anonymous = new SurveyResponse(1L, 10L, 5L, 77L, true, Map.of(3L, "ok"), LocalDateTime.now());
        SurveyResponse identified = new SurveyResponse(2L, 10L, 5L, 77L, false, Map.of(3L, "ok"), LocalDateTime.now());
        assertEquals("Anônimo", anonymous.respondentLabel());
        assertEquals("Participante #77", identified.respondentLabel());

        assertTrue(survey(true).identificationNotice().contains("anônimas"));
        assertTrue(survey(false).identificationNotice().contains("identificadas"));

        QuestionSummary comments = survey(true).summarize(List.of(anonymous)).get(text);
        assertEquals(List.of("[Anônimo] ok"), comments.getComments());
    }

    @Test
    @DisplayName("elegibilidade para avaliar: inscrito, presença validada, uma resposta, questionário ativo (RN-13)")
    void eligibility() {
        FeedbackSurvey s = survey(false);
        assertTrue(s.checkEligibility(true, AttendanceStatus.PRESENT, false).eligible());

        EvaluationEligibility notRegistered = s.checkEligibility(false, AttendanceStatus.PRESENT, false);
        assertFalse(notRegistered.eligible());
        assertTrue(notRegistered.reason().contains("inscritos"));

        EvaluationEligibility absent = s.checkEligibility(true, AttendanceStatus.ABSENT, false);
        assertFalse(absent.eligible());
        assertTrue(absent.reason().contains("presença"));

        assertFalse(s.checkEligibility(true, AttendanceStatus.PARTIAL, false).eligible());
        assertTrue(s.checkEligibility(true, AttendanceStatus.PRESENT, true).reason().contains("já enviou"));

        s.deactivate();
        assertTrue(s.checkEligibility(true, AttendanceStatus.PRESENT, false).reason().contains("encerrado"));
    }
}
