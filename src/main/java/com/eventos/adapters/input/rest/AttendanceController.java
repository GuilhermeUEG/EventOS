package com.eventos.adapters.input.rest;

import com.eventos.application.auth.AuthenticatedSession;
import com.eventos.application.dtos.AttendanceResult;
import com.eventos.application.ports.input.AttendanceUseCase;
import com.eventos.application.ports.input.EventUseCase;
import com.eventos.domain.model.AttendanceType;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

/**
 * Frequência por QR Code e lançamento manual (RF-19 a RF-23). Geração do QR Code, lançamento manual,
 * revogação e consultas exigem ser o responsável pelo evento ou administrador; a leitura do QR Code usa
 * o participante da sessão.
 */
class AttendanceController {
    private final AttendanceUseCase attendanceUseCase;
    private final EventUseCase eventUseCase;
    private final ApiSupport support;

    AttendanceController(AttendanceUseCase attendanceUseCase, EventUseCase eventUseCase, ApiSupport support) {
        this.attendanceUseCase = attendanceUseCase;
        this.eventUseCase = eventUseCase;
        this.support = support;
    }

    void register(Javalin app) {
        app.get("/api/attendance/qr/generate/{activityId}", this::generateQrCode);
        app.post("/api/attendance/qr/scan", this::scanQrCode);
        app.post("/api/attendance/manual", this::recordManual);
        app.post("/api/attendance/revoke", this::revoke);
        app.get("/api/attendance/activity/{activityId}", this::getRecords);
        app.get("/api/attendance/activity/{activityId}/overview", this::getOverview);
    }

    private void generateQrCode(Context ctx) {
        Long activityId = support.pathId(ctx, "activityId");
        requireManagerOfActivity(ctx, activityId);
        ctx.json(ApiMapper.qrCode(attendanceUseCase.generateQrCode(activityId)));
    }

    private void scanQrCode(Context ctx) {
        AuthenticatedSession session = support.authorization().requireAuthenticated(ctx);
        ApiRequests.QrScan body = support.body(ctx, ApiRequests.QrScan.class);
        AttendanceResult result = attendanceUseCase.recordQrAttendance(support.required(body.qrToken(), "qrToken"), session.userId());
        ctx.status(result.alreadyRecorded() ? HttpStatus.OK : HttpStatus.CREATED).json(ApiMapper.attendanceResult(result));
    }

    private void recordManual(Context ctx) {
        ApiRequests.ManualAttendance body = support.body(ctx, ApiRequests.ManualAttendance.class);
        Long activityId = support.required(body.activityId(), "activityId");
        AuthenticatedSession session = requireManagerOfActivity(ctx, activityId);
        AttendanceType type = support.parseEnum(AttendanceType.class, body.type(), "type");
        ctx.status(HttpStatus.CREATED).json(ApiMapper.attendanceRecord(attendanceUseCase.recordManualAttendance(
                activityId, support.required(body.userId(), "userId"), session.userId(), type, body.notes())));
    }

    private void revoke(Context ctx) {
        ApiRequests.RevokeAttendance body = support.body(ctx, ApiRequests.RevokeAttendance.class);
        Long activityId = support.required(body.activityId(), "activityId");
        AuthenticatedSession session = requireManagerOfActivity(ctx, activityId);
        ctx.status(HttpStatus.CREATED).json(ApiMapper.attendanceRecord(attendanceUseCase.revokeAttendance(
                activityId, support.required(body.userId(), "userId"), session.userId(), body.notes())));
    }

    private void getRecords(Context ctx) {
        Long activityId = support.pathId(ctx, "activityId");
        requireManagerOfActivity(ctx, activityId);
        ctx.json(attendanceUseCase.getActivityRecords(activityId).stream().map(ApiMapper::attendanceRecord).toList());
    }

    private void getOverview(Context ctx) {
        Long activityId = support.pathId(ctx, "activityId");
        requireManagerOfActivity(ctx, activityId);
        ctx.json(attendanceUseCase.getActivityAttendanceOverview(activityId));
    }

    private AuthenticatedSession requireManagerOfActivity(Context ctx, Long activityId) {
        return support.requireEventManager(ctx, eventUseCase.getActivity(activityId).getEventId());
    }
}
