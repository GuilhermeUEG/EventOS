package com.eventos.adapters.output.persistence;

import com.eventos.application.ports.output.EventRepository;
import com.eventos.domain.model.ActivityType;
import com.eventos.domain.model.Event;
import com.eventos.domain.model.EventStatus;
import com.eventos.domain.model.Period;
import com.eventos.domain.policies.CertificateEligibilityPolicy;
import com.eventos.domain.policies.MandatoryActivitiesPolicy;
import com.eventos.domain.policies.MinimumAttendancePercentagePolicy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcEventRepository implements EventRepository {

    @Override
    public Event save(Event event) {
        String policyType = "MIN_PERCENTAGE";
        double policyParam = 75.0;
        if (event.getCertificateEligibilityPolicy() instanceof MandatoryActivitiesPolicy p) {
            policyType = "MANDATORY_COUNT";
            policyParam = p.getMinMandatoryCount();
        } else if (event.getCertificateEligibilityPolicy() instanceof MinimumAttendancePercentagePolicy p) {
            policyType = "MIN_PERCENTAGE";
            policyParam = p.getMinPercentage();
        }

        if (event.getId() == null) {
            String sql = "INSERT INTO events (title, description, start_date, end_date, status, organizer_id, max_capacity, cert_policy_type, cert_policy_param) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, event.getTitle());
                stmt.setString(2, event.getDescription());
                stmt.setTimestamp(3, Timestamp.valueOf(event.getPeriod().getStart()));
                stmt.setTimestamp(4, Timestamp.valueOf(event.getPeriod().getEnd()));
                stmt.setString(5, event.getStatus().name());
                if (event.getOrganizerId() != null) stmt.setLong(6, event.getOrganizerId()); else stmt.setNull(6, java.sql.Types.BIGINT);
                stmt.setInt(7, event.getMaxCapacity());
                stmt.setString(8, policyType);
                stmt.setDouble(9, policyParam);
                stmt.executeUpdate();
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        return new Event(rs.getLong(1), event.getTitle(), event.getDescription(),
                                event.getPeriod(), event.getStatus(), event.getOrganizerId(),
                                event.getActivities(), event.getMaxCapacity(), event.getCertificateEligibilityPolicy());
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("Erro ao inserir evento: " + e.getMessage(), e);
            }
        } else {
            String sql = "UPDATE events SET title = ?, description = ?, start_date = ?, end_date = ?, status = ?, max_capacity = ?, cert_policy_type = ?, cert_policy_param = ? WHERE id = ?";
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, event.getTitle());
                stmt.setString(2, event.getDescription());
                stmt.setTimestamp(3, Timestamp.valueOf(event.getPeriod().getStart()));
                stmt.setTimestamp(4, Timestamp.valueOf(event.getPeriod().getEnd()));
                stmt.setString(5, event.getStatus().name());
                stmt.setInt(6, event.getMaxCapacity());
                stmt.setString(7, policyType);
                stmt.setDouble(8, policyParam);
                stmt.setLong(9, event.getId());
                stmt.executeUpdate();
                return event;
            } catch (SQLException e) {
                throw new RuntimeException("Erro ao atualizar evento: " + e.getMessage(), e);
            }
        }
        return event;
    }

    @Override
    public Optional<Event> findById(Long id) {
        String sql = "SELECT * FROM events WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar evento por ID: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public List<Event> findAll() {
        List<Event> list = new ArrayList<>();
        String sql = "SELECT * FROM events ORDER BY start_date DESC";
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar eventos: " + e.getMessage(), e);
        }
        return list;
    }

    @Override
    public List<Event> findByFilter(String search, String track, ActivityType type, EventStatus status) {
        StringBuilder sql = new StringBuilder("SELECT DISTINCT e.* FROM events e ");
        if ((track != null && !track.trim().isEmpty()) || type != null) {
            sql.append("JOIN activities a ON a.event_id = e.id ");
        }
        sql.append("WHERE 1=1 ");

        List<Object> params = new ArrayList<>();
        if (search != null && !search.trim().isEmpty()) {
            sql.append("AND (LOWER(e.title) LIKE ? OR LOWER(e.description) LIKE ?) ");
            String wild = "%" + search.trim().toLowerCase() + "%";
            params.add(wild);
            params.add(wild);
        }
        if (status != null) {
            sql.append("AND e.status = ? ");
            params.add(status.name());
        }
        if (track != null && !track.trim().isEmpty()) {
            sql.append("AND LOWER(a.space_or_track) = ? ");
            params.add(track.trim().toLowerCase());
        }
        if (type != null) {
            sql.append("AND a.type = ? ");
            params.add(type.name());
        }
        sql.append("ORDER BY e.start_date DESC");

        List<Event> list = new ArrayList<>();
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao filtrar eventos: " + e.getMessage(), e);
        }
        return list;
    }

    @Override
    public void delete(Long id) {
        String sql = "DELETE FROM events WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao excluir evento: " + e.getMessage(), e);
        }
    }

    private Event mapRow(ResultSet rs) throws SQLException {
        String polType = rs.getString("cert_policy_type");
        double polParam = rs.getDouble("cert_policy_param");
        CertificateEligibilityPolicy policy = "MANDATORY_COUNT".equalsIgnoreCase(polType)
                ? new MandatoryActivitiesPolicy((int) polParam)
                : new MinimumAttendancePercentagePolicy(polParam > 0 ? polParam : 75.0);

        return new Event(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("description"),
                new Period(rs.getTimestamp("start_date").toLocalDateTime(), rs.getTimestamp("end_date").toLocalDateTime()),
                EventStatus.valueOf(rs.getString("status")),
                rs.getObject("organizer_id") != null ? rs.getLong("organizer_id") : null,
                new ArrayList<>(),
                rs.getInt("max_capacity"),
                policy
        );
    }
}
