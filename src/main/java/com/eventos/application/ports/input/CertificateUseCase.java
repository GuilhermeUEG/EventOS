package com.eventos.application.ports.input;

import com.eventos.application.dtos.CertificateEligibilityDto;
import com.eventos.domain.model.Certificate;
import com.eventos.domain.model.CertificateDelivery;
import java.util.List;

public interface CertificateUseCase {
    CertificateEligibilityDto checkEligibility(Long eventId, Long userId);

    Certificate issueCertificateIfEligible(Long eventId, Long userId);

    Certificate getCertificateById(Long id);

    Certificate getCertificateByVerificationCode(String code);

    List<Certificate> getUserCertificates(Long userId);

    byte[] exportCertificatePdf(Long certificateId);

    /**
     * Envia o certificado em PDF ao e-mail cadastrado do titular e registra a tentativa (RF-34). Falha de envio
     * <b>não</b> lança exceção: vira um registro FAILED com o motivo, e o download segue disponível.
     * Reenvio dentro de {@code CertificateDelivery.DUPLICATE_WINDOW} devolve o envio anterior em vez de duplicar.
     */
    CertificateDelivery sendCertificateByEmail(Long certificateId);

    /** Tentativas de envio de um certificado, da mais recente para a mais antiga. */
    List<CertificateDelivery> getDeliveries(Long certificateId);

    /** Declaração em PDF para palestrante/apresentador, com a atividade e o papel exercido (RF-35). */
    byte[] exportSpeakerDeclarationPdf(Long activityId, Long speakerId);
}
