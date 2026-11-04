package com.eventos.adapters.input.rest;

import com.eventos.application.ports.input.AttendanceUseCase;
import com.eventos.application.ports.input.AuthUseCase;
import com.eventos.application.ports.input.CertificateUseCase;
import com.eventos.application.ports.input.EventUseCase;
import com.eventos.application.ports.input.RegistrationUseCase;
import com.eventos.application.ports.input.ReportUseCase;
import com.eventos.application.ports.input.SurveyUseCase;
import com.eventos.application.ports.output.SessionStore;
import io.javalin.Javalin;

/**
 * Adaptador de entrada REST (Javalin). Apenas compõe os controllers por área, o tratamento de erros e os
 * cabeçalhos de segurança; toda a regra de negócio está nos casos de uso (RNF-01, RNF-02, ROO-09).
 */
public class RestApiController {

    public RestApiController(AuthUseCase authUseCase,
                             EventUseCase eventUseCase,
                             RegistrationUseCase registrationUseCase,
                             AttendanceUseCase attendanceUseCase,
                             SurveyUseCase surveyUseCase,
                             CertificateUseCase certificateUseCase,
                             ReportUseCase reportUseCase,
                             SessionStore sessionStore,
                             Javalin app) {
        ApiSupport support = new ApiSupport(new AuthorizationHandler(sessionStore), eventUseCase);

        ApiExceptionMapper.register(app);
        app.after("/api/*", ctx -> {
            ctx.header("X-Content-Type-Options", "nosniff");
            ctx.header("Cache-Control", "no-store");
        });

        new AuthController(authUseCase, sessionStore, support).register(app);
        new EventController(eventUseCase, support).register(app);
        new HomeController(eventUseCase).register(app);
        new RegistrationController(registrationUseCase, support).register(app);
        new AttendanceController(attendanceUseCase, eventUseCase, support).register(app);
        new SurveyController(surveyUseCase, eventUseCase, support).register(app);
        new ReportController(reportUseCase, certificateUseCase, eventUseCase, support).register(app);
    }
}
