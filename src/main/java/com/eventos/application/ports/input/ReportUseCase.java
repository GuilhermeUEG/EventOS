package com.eventos.application.ports.input;

import com.eventos.application.dtos.AttendanceReportDto;
import com.eventos.application.dtos.EnrolledReportDto;

public interface ReportUseCase {
    EnrolledReportDto getEnrolledReport(Long eventId);
    AttendanceReportDto getAttendanceReport(Long eventId);
    byte[] exportEnrolledCsv(Long eventId);
    byte[] exportEnrolledPdf(Long eventId);
    byte[] exportAttendancePdf(Long eventId);
}
