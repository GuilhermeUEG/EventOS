package com.eventos.adapters.output.persistence;

import com.eventos.application.ports.output.ActivityRepository;
import com.eventos.domain.model.Activity;
import com.eventos.domain.model.ActivityLocation;
import com.eventos.domain.model.ActivityType;
import com.eventos.domain.model.Period;
import com.eventos.domain.model.Speaker;
import com.eventos.domain.policies.AttendancePolicyFactory;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class JdbcActivityRepository implements ActivityRepository {

    @Override
    public Activity save(Activity activity) {
        if (activity.getId() == null) {
            String sql = "INSERT INTO activities (event_id, title, description, start_date, end_date, room, space_or_track, capacity, current_enrollments, type, attendance_policy, requires_registration) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setLong(1, activity.getEventId());
                bindCommonFields(stmt, activity, 2);
                stmt.executeUpdate();
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        long actId = rs.getLong(1);
                        saveSpeakers(conn, actId, activity.getSpeakers());
                        return findById(actId).orElse(activity);
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Erro ao inserir atividade: " + e.getMessage(), e);
            }
            return activity;
        }

        String sql = "UPDATE activities SET title = ?, description = ?, start_date = ?, end_date = ?, room = ?, space_or_track = ?, capacity = ?, current_enrollments = ?, type = ?, attendance_policy = ?, requires_registration = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            bindCommonFields(stmt, activity, 1);
            stmt.setLong(12, activity.getId());
            stmt.executeUpdate();
            saveSpeakers(conn, activity.getId(), activity.getSpeakers());
            return findById(activity.getId()).orElse(activity);
        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao atualizar atividade: " + e.getMessage(), e);
        }
    }

    /** Preenche, a partir de {@code first}, os 11 campos comuns ao INSERT e ao UPDATE. */
    private void bindCommonFields(PreparedStatement stmt, Activity activity, int first) throws SQLException {
        int i = first;
        stmt.setString(i++, activity.getTitle());
        stmt.setString(i++, activity.getDescription());
        stmt.setTimestamp(i++, Timestamp.valueOf(activity.getPeriod().getStart()));
        stmt.setTimestamp(i++, Timestamp.valueOf(activity.getPeriod().getEnd()));
        stmt.setString(i++, activity.getLocation().getRoom());
        stmt.setString(i++, activity.getLocation().getSpaceOrTrack());
        stmt.setInt(i++, activity.getMaxCapacity());
        stmt.setInt(i++, activity.getCurrentEnrollments());
        stmt.setString(i++, activity.getType().name());
        stmt.setString(i++, activity.getAttendancePolicy().getPolicyKey());
        stmt.setBoolean(i, activity.isRequiresRegistration());
    }

    @Override
    public Optional<Activity> findById(Long id) {
        String sql = "SELECT * FROM activities WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Map<Long, List<Speaker>> speakers = speakersOf(conn, List.of(id));
                    return Optional.of(mapRow(rs, speakers.getOrDefault(id, List.of())));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao buscar atividade: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public List<Activity> findByEventId(Long eventId) {
        String sql = "SELECT * FROM activities WHERE event_id = ? ORDER BY start_date ASC, id ASC";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, eventId);
            return readActivities(conn, stmt);
        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao listar atividades do evento: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Activity> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return new ArrayList<>();
        String placeholders = ids.stream().map(i -> "?").collect(Collectors.joining(","));
        String sql = "SELECT * FROM activities WHERE id IN (" + placeholders + ") ORDER BY start_date ASC, id ASC";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < ids.size(); i++) stmt.setLong(i + 1, ids.get(i));
            return readActivities(conn, stmt);
        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao buscar atividades: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean reserveSlot(Long activityId) {
        String sql = "UPDATE activities SET current_enrollments = current_enrollments + 1 "
                + "WHERE id = ? AND (requires_registration = FALSE OR current_enrollments < capacity)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, activityId);
            return stmt.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao reservar vaga: " + e.getMessage(), e);
        }
    }

    @Override
    public void releaseSlot(Long activityId) {
        String sql = "UPDATE activities SET current_enrollments = current_enrollments - 1 WHERE id = ? AND current_enrollments > 0";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, activityId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Erro ao liberar vaga: " + e.getMessage(), e);
        }
    }

    /** Lê as atividades do statement e carrega os palestrantes de todas elas em uma única consulta. */
    private List<Activity> readActivities(Connection conn, PreparedStatement stmt) throws SQLException {
        List<ResultRow> rows = new ArrayList<>();
        try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) rows.add(new ResultRow(rs.getLong("id"), snapshot(rs)));
        }
        Map<Long, List<Speaker>> speakers = speakersOf(conn, rows.stream().map(ResultRow::id).toList());
        List<Activity> list = new ArrayList<>();
        for (ResultRow row : rows) {
            list.add(row.data().build(speakers.getOrDefault(row.id(), List.of())));
        }
        return list;
    }

    private void saveSpeakers(Connection conn, Long activityId, List<Speaker> speakers) throws SQLException {
        try (PreparedStatement del = conn.prepareStatement("DELETE FROM speakers WHERE activity_id = ?")) {
            del.setLong(1, activityId);
            del.executeUpdate();
        }
        if (speakers == null || speakers.isEmpty()) return;
        String sql = "INSERT INTO speakers (activity_id, name, role_in_activity, bio, photo_url) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (Speaker s : speakers) {
                stmt.setLong(1, activityId);
                stmt.setString(2, s.getName());
                stmt.setString(3, s.getRoleInActivity());
                stmt.setString(4, s.getBio());
                stmt.setString(5, s.getPhotoUrl());
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    private Map<Long, List<Speaker>> speakersOf(Connection conn, List<Long> activityIds) throws SQLException {
        if (activityIds.isEmpty()) return Collections.emptyMap();
        String placeholders = activityIds.stream().map(i -> "?").collect(Collectors.joining(","));
        String sql = "SELECT * FROM speakers WHERE activity_id IN (" + placeholders + ") ORDER BY id ASC";
        Map<Long, List<Speaker>> result = new HashMap<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < activityIds.size(); i++) stmt.setLong(i + 1, activityIds.get(i));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.computeIfAbsent(rs.getLong("activity_id"), k -> new ArrayList<>()).add(new Speaker(
                            rs.getLong("id"),
                            rs.getString("name"),
                            rs.getString("role_in_activity"),
                            rs.getString("bio"),
                            rs.getString("photo_url")));
                }
            }
        }
        return result;
    }

    private Activity mapRow(ResultSet rs, List<Speaker> speakers) throws SQLException {
        return snapshot(rs).build(speakers);
    }

    /** Copia as colunas da linha atual para que o ResultSet possa avançar antes de montar o objeto. */
    private RowData snapshot(ResultSet rs) throws SQLException {
        return new RowData(
                rs.getLong("id"), rs.getLong("event_id"), rs.getString("title"), rs.getString("description"),
                new Period(rs.getTimestamp("start_date").toLocalDateTime(), rs.getTimestamp("end_date").toLocalDateTime()),
                rs.getString("room"), rs.getString("space_or_track"), rs.getInt("capacity"),
                rs.getInt("current_enrollments"), ActivityType.valueOf(rs.getString("type")),
                rs.getString("attendance_policy"), rs.getBoolean("requires_registration"));
    }

    private record ResultRow(long id, RowData data) {
    }

    private record RowData(long id, long eventId, String title, String description, Period period, String room,
                           String track, int capacity, int enrollments, ActivityType type, String policyKey,
                           boolean requiresRegistration) {
        Activity build(List<Speaker> speakers) {
            return new Activity(id, eventId, title, description, period, new ActivityLocation(room, track, capacity),
                    type, capacity, enrollments, speakers, AttendancePolicyFactory.create(policyKey), requiresRegistration);
        }
    }
}
