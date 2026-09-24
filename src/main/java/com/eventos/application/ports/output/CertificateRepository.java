package com.eventos.application.ports.output;

import com.eventos.domain.model.Certificate;
import java.util.List;
import java.util.Optional;

public interface CertificateRepository {
    Certificate save(Certificate certificate);
    Optional<Certificate> findById(Long id);
    Optional<Certificate> findByEventAndUser(Long eventId, Long userId);
    Optional<Certificate> findByVerificationCode(String code);
    List<Certificate> findByUserId(Long userId);
}
