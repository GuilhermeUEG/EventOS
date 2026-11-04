package com.eventos.adapters.output.persistence;

import com.eventos.application.ports.output.CertificateDeliveryRepository;
import com.eventos.domain.model.CertificateDelivery;
import com.eventos.domain.model.DeliveryStatus;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class JdbcCertificateDeliveryRepository implements CertificateDeliveryRepository {
    private static final int MAX_DETAIL = 1000;

    @Override
    public CertificateDelivery save(CertificateDelivery d) {
        String sql = "INSERT INTO certificate_deliveries (certificate_id, recipient, status, detail, attempted_at) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setLong(1, d.getCertificateId());
            stmt.setString(2, d.getRecipient());
            stmt.setString(3, d.getStatus().name());
            stmt.setString(4, d.getDetail().length() > MAX_DETAIL ? d.getDetail().substring(0, MAX_DETAIL) : d.getDetail());
            stmt.setTimestamp(5, Timestamp.valueOf(d.getAttemptedAt()));
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return new CertificateDelivery(rs.getLong(1), d.getCertificateId(), d.getRecipient(),
                            d.getStatus(), d.getDetail(), d.getAttemptedAt());
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao registrar o envio do certificado: " + e.getMessage(), e);
        }
        return d;
    }

    @Override
    public List<CertificateDelivery> findByCertificateId(Long certificateId) {
        List<CertificateDelivery> list = new ArrayList<>();
        String sql = "SELECT * FROM certificate_deliveries WHERE certificate_id = ? ORDER BY attempted_at DESC, id DESC";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, certificateId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new CertificateDelivery(rs.getLong("id"), rs.getLong("certificate_id"), rs.getString("recipient"),
                            DeliveryStatus.valueOf(rs.getString("status")), rs.getString("detail"),
                            rs.getTimestamp("attempted_at").toLocalDateTime()));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao listar envios do certificado: " + e.getMessage(), e);
        }
        return list;
    }
}
