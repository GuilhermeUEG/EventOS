package com.eventos.adapters.output.persistence;

import com.eventos.application.ports.output.EventRepository;
import com.eventos.domain.exceptions.ValidationException;
import com.eventos.domain.model.ActivityType;
import com.eventos.domain.model.Event;
import com.eventos.domain.model.EventStatus;
import com.eventos.domain.model.Period;
import com.eventos.domain.policies.CertificateEligibilityPolicy;
import com.eventos.domain.policies.CertificatePolicyFactory;
import com.eventos.domain.policies.CertificatePolicySpec;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JdbcEventRepository implements EventRepository {
    private static final Logger log = LoggerFactory.getLogger(JdbcEventRepository.class);

    @Override
    public Event save(Event event) {
        CertificatePolicySpec spec = event.getCertificateEligibilityPolicy().spec();
        double policyParam = spec.percentage() != null ? spec.percentage()
                : spec.minActivities() != null ? spec.minActivities() : 0.0;
        String policyActivities = spec.activityIds().stream().map(String::valueOf).collect(Collectors.joining(","));

        if (event.getId() == null) {
            String sql = """
                    INSERT INTO events
                    (title, description, start_date, end_date, status, organizer_id, max_capacity,
                     cert_policy_type, cert_policy_param, cert_policy_activities, activity_selection_enabled,
                     activity_selection_required, registration_deadline, time_zone)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, event.getTitle());
                stmt.setString(2, event.getDescription());
                stmt.setTimestamp(3, Timestamp.valueOf(event.getPeriod().getStart()));
                stmt.setTimestamp(4, Timestamp.valueOf(event.getPeriod().getEnd()));
                stmt.setString(5, event.getStatus().name());
                if (event.getOrganizerId() != null) stmt.setLong(6, event.getOrganizerId());
                else stmt.setNull(6, java.sql.Types.BIGINT);
                stmt.setInt(7, event.getMaxCapacity());
                stmt.setString(8, spec.type());
                stmt.setDouble(9, policyParam);
                stmt.setString(10, policyActivities);
                stmt.setBoolean(11, event.isActivitySelectionEnabled());
                stmt.setBoolean(12, event.isActivitySelectionRequired());
                if (event.getRegistrationDeadline() != null) {
                    stmt.setTimestamp(13, Timestamp.valueOf(event.getRegistrationDeadline()));
                } else {
                    stmt.setNull(13, java.sql.Types.TIMESTAMP);
                }
                stmt.setString(14, event.getTimeZone());
                stmt.executeUpdate();
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        Event saved = new Event(rs.getLong(1), event.getTitle(), event.getDescription(),
                                event.getPeriod(), event.getStatus(), event.getOrganizerId(),
                                event.getActivities(), event.getMaxCapacity(), event.getCertificateEligibilityPolicy());
                        saved.configureRegistration(event.isActivitySelectionEnabled(),
                                event.isActivitySelectionRequired(), event.getRegistrationDeadline(), event.getTimeZone());
                        return saved;
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Erro ao inserir evento: " + e.getMessage(), e);
            }
            return event;
        }

        String sql = """
                UPDATE events SET title = ?, description = ?, start_date = ?, end_date = ?,
                status = ?, max_capacity = ?, cert_policy_type = ?, cert_policy_param = ?,
                cert_policy_activities = ?, activity_selection_enabled = ?, activity_selection_required = ?,
                registration_deadline = ?, time_zone = ? WHERE id = ?
                """;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, event.getTitle());
            stmt.setString(2, event.getDescription());
            stmt.setTimestamp(3, Timestamp.valueOf(event.getPeriod().getStart()));
            stmt.setTimestamp(4, Timestamp.valueOf(event.getPeriod().getEnd()));
            stmt.setString(5, event.getStatus().name());
            stmt.setInt(6, event.getMaxCapacity());
            stmt.setString(7, spec.type());
            stmt.setDouble(8, policyParam);
            stmt.setString(9, policyActivities);
            stmt.setBoolean(10, event.isActivitySelectionEnabled());
            stmt.setBoolean(11, event.isActivitySelectionRequired());
            if (event.getRegistrationDeadline() != null) {
                stmt.setTimestamp(12, Timestamp.valueOf(event.getRegistrationDeadline()));
            } else {
                stmt.setNull(12, java.sql.Types.TIMESTAMP);
            }
            stmt.setString(13, event.getTimeZone());
            stmt.setLong(14, event.getId());
            stmt.executeUpdate();
            return event;
        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao atualizar evento: " + e.getMessage(), e);
        }
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
            throw new IllegalStateException("Erro ao buscar evento por ID: " + e.getMessage(), e);
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
            throw new IllegalStateException("Erro ao listar eventos: " + e.getMessage(), e);
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
            throw new IllegalStateException("Erro ao filtrar eventos: " + e.getMessage(), e);
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
            throw new IllegalStateException("Erro ao excluir evento: " + e.getMessage(), e);
        }
    }

    private Event mapRow(ResultSet rs) throws SQLException {
        Event event = new Event(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("description"),
                new Period(rs.getTimestamp("start_date").toLocalDateTime(), rs.getTimestamp("end_date").toLocalDateTime()),
                EventStatus.valueOf(rs.getString("status")),
                rs.getObject("organizer_id") != null ? rs.getLong("organizer_id") : null,
                new ArrayList<>(),
                rs.getInt("max_capacity"),
                readPolicy(rs)
        );
        Timestamp deadline = rs.getTimestamp("registration_deadline");
        event.configureRegistration(
                rs.getBoolean("activity_selection_enabled"),
                rs.getBoolean("activity_selection_required"),
                deadline != null ? deadline.toLocalDateTime() : event.getPeriod().getStart(),
                rs.getString("time_zone") != null ? rs.getString("time_zone") : Event.DEFAULT_TIME_ZONE);
        return event;
    }

    private CertificateEligibilityPolicy readPolicy(ResultSet rs) throws SQLException {
        String type = rs.getString("cert_policy_type");
        double param = rs.getDouble("cert_policy_param");
        String activitiesCsv = rs.getString("cert_policy_activities");
        Set<Long> activityIds = new HashSet<>();
        if (activitiesCsv != null && !activitiesCsv.isBlank()) {
            for (String id : activitiesCsv.split(",")) activityIds.add(Long.parseLong(id.trim()));
        }
        try {
            return CertificatePolicyFactory.create(new CertificatePolicySpec(
                    type == null ? "MIN_PERCENTAGE" : type, param > 0 ? param : null, param >= 1 ? (int) param : null, activityIds));
        } catch (ValidationException e) {
            log.warn("Política de certificado inválida no evento {} ({}); usando a política padrão.",
                    rs.getLong("id"), e.getMessage());
            return CertificatePolicyFactory.defaultPolicy();
        }
    }
}
