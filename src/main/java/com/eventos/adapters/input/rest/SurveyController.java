package com.eventos.adapters.input.rest;

import com.eventos.adapters.input.rest.ApiDtos.EvaluationEligibilityResponse;
import com.eventos.application.auth.AuthenticatedSession;
import com.eventos.application.ports.input.EventUseCase;
import com.eventos.application.ports.input.SurveyUseCase;
import com.eventos.domain.exceptions.ValidationException;
import com.eventos.domain.model.EvaluationEligibility;
import com.eventos.domain.model.FeedbackSurvey;
import com.eventos.domain.model.QuestionType;
import com.eventos.domain.model.SurveyQuestion;
import com.eventos.domain.model.SurveyResponse;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Questionários e avaliações (RF-24 a RF-28). O organizador define o questionário e sua política de
 * anonimato; o participante só responde se estiver inscrito e com presença validada.
 */
class SurveyController {
    private final SurveyUseCase surveyUseCase;
    private final EventUseCase eventUseCase;
    private final ApiSupport support;

    SurveyController(SurveyUseCase surveyUseCase, EventUseCase eventUseCase, ApiSupport support) {
        this.surveyUseCase = surveyUseCase;
        this.eventUseCase = eventUseCase;
        this.support = support;
    }

    void register(Javalin app) {
        app.post("/api/surveys", this::createSurvey);
        app.get("/api/surveys/activity/{activityId}", this::getSurvey);
        app.get("/api/surveys/activity/{activityId}/can-evaluate/{userId}", this::canEvaluate);
        app.post("/api/surveys/submit", this::submit);
        app.get("/api/surveys/activity/{activityId}/results", this::getResults);
    }

    private void createSurvey(Context ctx) {
        ApiRequests.CreateSurvey body = support.body(ctx, ApiRequests.CreateSurvey.class);
        Long activityId = support.required(body.activityId(), "activityId");
        support.requireEventManager(ctx, eventUseCase.getActivity(activityId).getEventId());
        if (body.questions() == null || body.questions().isEmpty()) {
            throw new ValidationException("O questionário precisa ter ao menos uma pergunta.");
        }
        List<SurveyQuestion> questions = new ArrayList<>();
        for (ApiRequests.Question q : body.questions()) {
            QuestionType type = support.parseEnum(QuestionType.class, support.required(q.type(), "type"), "type");
            questions.add(new SurveyQuestion(null, q.questionText(), type, q.options(), q.required() == null || q.required()));
        }
        FeedbackSurvey survey = new FeedbackSurvey(null, activityId, body.title(), questions, true,
                body.anonymous() != null && body.anonymous());
        ctx.status(HttpStatus.CREATED).json(ApiMapper.survey(surveyUseCase.createSurvey(survey)));
    }

    private void getSurvey(Context ctx) {
        support.authorization().requireAuthenticated(ctx);
        ctx.json(ApiMapper.survey(surveyUseCase.getSurveyByActivity(support.pathId(ctx, "activityId"))));
    }

    private void canEvaluate(Context ctx) {
        Long activityId = support.pathId(ctx, "activityId");
        Long userId = support.pathId(ctx, "userId");
        support.authorization().requireSelfOrStaff(ctx, userId);
        EvaluationEligibility eligibility = surveyUseCase.checkEvaluationEligibility(userId, activityId);
        ctx.json(new EvaluationEligibilityResponse(activityId, userId, eligibility.eligible(), eligibility.reason()));
    }

    private void submit(Context ctx) {
        AuthenticatedSession session = support.authorization().requireAuthenticated(ctx);
        ApiRequests.SubmitSurvey body = support.body(ctx, ApiRequests.SubmitSurvey.class);
        Map<Long, String> answers = new HashMap<>();
        if (body.answers() != null) {
            body.answers().forEach((key, value) -> answers.put(parseQuestionId(key), value));
        }
        SurveyResponse saved = surveyUseCase.submitResponse(support.required(body.activityId(), "activityId"),
                session.userId(), answers);
        ctx.status(HttpStatus.CREATED).json(ApiMapper.submitted(saved));
    }

    private void getResults(Context ctx) {
        Long activityId = support.pathId(ctx, "activityId");
        support.requireEventManager(ctx, eventUseCase.getActivity(activityId).getEventId());
        ctx.json(surveyUseCase.getConsolidatedResults(activityId));
    }

    private Long parseQuestionId(String key) {
        try {
            return Long.parseLong(key);
        } catch (NumberFormatException e) {
            throw new ValidationException("Identificador de pergunta inválido: " + key);
        }
    }
}
