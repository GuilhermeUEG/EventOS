package com.eventos.application.ports.input;

import com.eventos.domain.model.Certificate;
import java.util.List;

public interface CertificateUseCase {
    Certificate issueCertificateIfEligible(Long eventId, Long userId);
    Certificate getCertificateById(Long id);
    Certificate getCertificateByVerificationCode(String code);
    List<Certificate> getUserCertificates(Long userId);
    byte[] exportCertificatePdf(Long certificateId);
}
