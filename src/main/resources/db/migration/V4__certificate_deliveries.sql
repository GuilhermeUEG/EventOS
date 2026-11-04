-- V4: registro das tentativas de envio de certificados por e-mail (RF-34).

CREATE TABLE IF NOT EXISTS certificate_deliveries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    certificate_id BIGINT NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    detail VARCHAR(1000),
    attempted_at TIMESTAMP NOT NULL,
    FOREIGN KEY (certificate_id) REFERENCES certificates(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_deliveries_certificate ON certificate_deliveries(certificate_id);
