package com.eventos.application.ports.output;

import com.eventos.domain.model.CertificateDelivery;
import java.util.List;

public interface CertificateDeliveryRepository {
    CertificateDelivery save(CertificateDelivery delivery);

    /** Tentativas de envio de um certificado, da mais recente para a mais antiga. */
    List<CertificateDelivery> findByCertificateId(Long certificateId);
}
