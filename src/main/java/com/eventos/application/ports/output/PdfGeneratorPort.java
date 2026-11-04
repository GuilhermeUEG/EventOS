package com.eventos.application.ports.output;

import com.eventos.application.dtos.AttendanceReportDto;
import com.eventos.application.dtos.EnrolledReportDto;
import com.eventos.application.dtos.SpeakerDeclarationDto;
import com.eventos.domain.model.Certificate;

public interface PdfGeneratorPort {
    byte[] generateCertificatePdf(Certificate certificate);

    byte[] generateSpeakerDeclarationPdf(SpeakerDeclarationDto declaration);

    byte[] generateEnrolledReportPdf(EnrolledReportDto report);

    byte[] generateAttendanceReportPdf(AttendanceReportDto report);
}
