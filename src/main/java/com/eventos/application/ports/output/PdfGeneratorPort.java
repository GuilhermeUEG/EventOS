package com.eventos.application.ports.output;

import com.eventos.application.dtos.AttendanceReportDto;
import com.eventos.application.dtos.EnrolledReportDto;
import com.eventos.domain.model.Certificate;

public interface PdfGeneratorPort {
    byte[] generateCertificatePdf(Certificate certificate);
    byte[] generateEnrolledReportPdf(EnrolledReportDto report);
    byte[] generateAttendanceReportPdf(AttendanceReportDto report);
}
