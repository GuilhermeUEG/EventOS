package com.eventos.adapters.input.rest;

import com.eventos.application.ports.input.CertificateUseCase;
import com.eventos.application.ports.input.EventUseCase;
import com.eventos.application.ports.input.ReportUseCase;
import com.eventos.domain.model.Certificate;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

/**
 * Relatórios operacionais (RF-29 a RF-31) e certificados (RF-32, RF-33). Relatórios exigem ser
 * responsável pelo evento ou administrador; certificados exigem ser o titular ou ter perfil de gestão.
 */
class ReportController {
    private final ReportUseCase reportUseCase;
    private final CertificateUseCase certificateUseCase;
    private final EventUseCase eventUseCase;
    private final ApiSupport support;

    ReportController(ReportUseCase reportUseCase, CertificateUseCase certificateUseCase,
                     EventUseCase eventUseCase, ApiSupport support) {
        this.reportUseCase = reportUseCase;
        this.certificateUseCase = certificateUseCase;
        this.eventUseCase = eventUseCase;
        this.support = support;
    }

    void register(Javalin app) {
        app.get("/api/reports/enrolled/{eventId}", ctx -> ctx.json(reportUseCase.getEnrolledReport(managedEventId(ctx))));
        app.get("/api/reports/enrolled/{eventId}/csv", ctx -> download(ctx, "text/csv; charset=UTF-8", "attachment",
                "relatorio_inscritos_", ".csv", reportUseCase.exportEnrolledCsv(managedEventId(ctx))));
        app.get("/api/reports/enrolled/{eventId}/pdf", ctx -> download(ctx, "application/pdf", "inline",
                "relatorio_inscritos_", ".pdf", reportUseCase.exportEnrolledPdf(managedEventId(ctx))));
        app.get("/api/reports/attendance/{eventId}", ctx -> ctx.json(reportUseCase.getAttendanceReport(managedEventId(ctx))));
        app.get("/api/reports/attendance/{eventId}/csv", ctx -> download(ctx, "text/csv; charset=UTF-8", "attachment",
                "relatorio_frequencia_", ".csv", reportUseCase.exportAttendanceCsv(managedEventId(ctx))));
        app.get("/api/reports/attendance/{eventId}/pdf", ctx -> download(ctx, "application/pdf", "inline",
                "relatorio_frequencia_", ".pdf", reportUseCase.exportAttendancePdf(managedEventId(ctx))));

        app.get("/api/activities/{activityId}/speakers/{speakerId}/declaration", this::speakerDeclaration);

        app.get("/api/certificates/eligibility/{eventId}/{userId}", this::eligibility);
        app.post("/api/certificates/issue/{eventId}/{userId}", this::issue);
        app.post("/api/certificates/{id}/send", this::sendByEmail);
        app.get("/api/certificates/{id}/deliveries", this::deliveries);
        app.get("/api/certificates/user/{userId}", this::userCertificates);
        app.get("/api/certificates/verify/{code}", this::verify);
        app.get("/api/certificates/download/{id}", this::downloadCertificate);
    }

    private Long managedEventId(Context ctx) {
        Long eventId = support.pathId(ctx, "eventId");
        support.requireEventManager(ctx, eventId);
        return eventId;
    }

    private void download(Context ctx, String contentType, String disposition, String prefix, String extension, byte[] content) {
        ctx.contentType(contentType);
        ctx.header("Content-Disposition", disposition + "; filename=" + prefix + ctx.pathParam("eventId") + extension);
        ctx.result(content);
    }

    /** Declaração para palestrante/apresentador (RF-35); emitida pelo responsável pelo evento. */
    private void speakerDeclaration(Context ctx) {
        Long activityId = support.pathId(ctx, "activityId");
        Long speakerId = support.pathId(ctx, "speakerId");
        support.requireEventManager(ctx, eventUseCase.getActivity(activityId).getEventId());
        ctx.contentType("application/pdf");
        ctx.header("Content-Disposition", "inline; filename=declaracao_" + activityId + "_" + speakerId + ".pdf");
        ctx.result(certificateUseCase.exportSpeakerDeclarationPdf(activityId, speakerId));
    }

    private void eligibility(Context ctx) {
        Long userId = support.pathId(ctx, "userId");
        support.authorization().requireSelfOrStaff(ctx, userId);
        ctx.json(certificateUseCase.checkEligibility(support.pathId(ctx, "eventId"), userId));
    }

    private void issue(Context ctx) {
        Long userId = support.pathId(ctx, "userId");
        support.authorization().requireSelfOrStaff(ctx, userId);
        Certificate cert = certificateUseCase.issueCertificateIfEligible(support.pathId(ctx, "eventId"), userId);
        ctx.status(HttpStatus.CREATED).json(ApiMapper.certificate(cert, true));
    }

    /**
     * Envia o PDF ao e-mail cadastrado do titular (RF-34). Responde 200 mesmo quando o envio falha: a falha é um
     * resultado registrado ({@code status = FAILED}, com o motivo), e o download continua disponível.
     */
    private void sendByEmail(Context ctx) {
        Long id = support.pathId(ctx, "id");
        support.authorization().requireSelfOrStaff(ctx, certificateUseCase.getCertificateById(id).getUserId());
        ctx.json(ApiMapper.delivery(certificateUseCase.sendCertificateByEmail(id)));
    }

    private void deliveries(Context ctx) {
        Long id = support.pathId(ctx, "id");
        support.authorization().requireSelfOrStaff(ctx, certificateUseCase.getCertificateById(id).getUserId());
        ctx.json(certificateUseCase.getDeliveries(id).stream().map(ApiMapper::delivery).toList());
    }

    private void userCertificates(Context ctx) {
        Long userId = support.pathId(ctx, "userId");
        support.authorization().requireSelfOrStaff(ctx, userId);
        ctx.json(certificateUseCase.getUserCertificates(userId).stream().map(c -> ApiMapper.certificate(c, true)).toList());
    }

    /** Verificação pública de autenticidade: não expõe identificadores internos do titular. */
    private void verify(Context ctx) {
        ctx.json(ApiMapper.certificate(certificateUseCase.getCertificateByVerificationCode(ctx.pathParam("code")), false));
    }

    private void downloadCertificate(Context ctx) {
        Long id = support.pathId(ctx, "id");
        Certificate certificate = certificateUseCase.getCertificateById(id);
        support.authorization().requireSelfOrStaff(ctx, certificate.getUserId());
        ctx.contentType("application/pdf");
        ctx.header("Content-Disposition", "inline; filename=certificado_" + id + ".pdf");
        ctx.result(certificateUseCase.exportCertificatePdf(id));
    }
}
