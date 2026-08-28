package com.eventos.adapters.output.persistence;

import com.eventos.application.ports.output.EventRepository;
import com.eventos.domain.Event;
import com.eventos.domain.Activity;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class EventPersistenceAdapter implements EventRepository {

    @Override
    public Event save(Event event) {
        String insertSql = "INSERT INTO events (title, description, status, start_date, end_date) VALUES (?, ?, ?, ?, ?)";
        String updateSql = "UPDATE events SET title = ?, description = ?, status = ?, start_date = ?, end_date = ? WHERE id = ?";
        
        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false); // Transação
            
            try {
                if (event.getId() == null) {
                    // INSERT do Evento
                    try (PreparedStatement ps = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                        ps.setString(1, event.getTitle());
                        ps.setString(2, event.getDescription());
                        ps.setString(3, event.getStatus());
                        ps.setTimestamp(4, Timestamp.valueOf(event.getStartDate()));
                        ps.setTimestamp(5, Timestamp.valueOf(event.getEndDate()));
                        ps.executeUpdate();
                        
                        try (ResultSet rs = ps.getGeneratedKeys()) {
                            if (rs.next()) {
                                event.setId(rs.getLong(1));
                            }
                        }
                    }
                } else {
                    // UPDATE do Evento
                    try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                        ps.setString(1, event.getTitle());
                        ps.setString(2, event.getDescription());
                        ps.setString(3, event.getStatus());
                        ps.setTimestamp(4, Timestamp.valueOf(event.getStartDate()));
                        ps.setTimestamp(5, Timestamp.valueOf(event.getEndDate()));
                        ps.setLong(6, event.getId());
                        ps.executeUpdate();
                    }
                }
                
                // Salvar Atividades: Deletar existentes do evento e reinserir
                String deleteActivitiesSql = "DELETE FROM activities WHERE event_id = ?";
                try (PreparedStatement ps = conn.prepareStatement(deleteActivitiesSql)) {
                    ps.setLong(1, event.getId());
                    ps.executeUpdate();
                }
                
                String insertActivitySql = "INSERT INTO activities (event_id, title, description, type, start_time, end_time, location, capacity, enrolled_count) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
                for (Activity activity : event.getActivities()) {
                    try (PreparedStatement ps = conn.prepareStatement(insertActivitySql, Statement.RETURN_GENERATED_KEYS)) {
                        ps.setLong(1, event.getId());
                        ps.setString(2, activity.getTitle());
                        ps.setString(3, activity.getDescription());
                        ps.setString(4, activity.getType());
                        ps.setTimestamp(5, Timestamp.valueOf(activity.getStartTime()));
                        ps.setTimestamp(6, Timestamp.valueOf(activity.getEndTime()));
                        ps.setString(7, activity.getLocation());
                        ps.setInt(8, activity.getCapacity());
                        ps.setInt(9, activity.getEnrolledCount());
                        ps.executeUpdate();
                        
                        try (ResultSet rs = ps.getGeneratedKeys()) {
                            if (rs.next()) {
                                activity.setId(rs.getLong(1));
                            }
                        }
                    }
                }
                
                conn.commit();
                return event;
            } catch (Exception e) {
                conn.rollback();
                throw new RuntimeException("Erro ao persistir evento e atividades: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro de conexão com o banco de dados: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<Event> findById(Long id) {
        String selectEvent = "SELECT * FROM events WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(selectEvent)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Event event = mapEvent(rs);
                    loadActivities(event, conn);
                    return Optional.of(event);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar evento por ID: " + e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public List<Event> findAll() {
        List<Event> events = new ArrayList<>();
        String selectAll = "SELECT * FROM events";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(selectAll);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Event event = mapEvent(rs);
                loadActivities(event, conn);
                events.add(event);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar todos os eventos: " + e.getMessage(), e);
        }
        return events;
    }

    @Override
    public void deleteById(Long id) {
        String deleteEvent = "DELETE FROM events WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(deleteEvent)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao deletar evento: " + e.getMessage(), e);
        }
    }

    private Event mapEvent(ResultSet rs) throws SQLException {
        Event event = new Event();
        event.setId(rs.getLong("id"));
        event.setTitle(rs.getString("title"));
        event.setDescription(rs.getString("description"));
        event.setPeriod(rs.getTimestamp("start_date").toLocalDateTime(),
                         rs.getTimestamp("end_date").toLocalDateTime());
        
        String status = rs.getString("status");
        if ("PUBLICADO".equalsIgnoreCase(status)) {
            event.publish();
        } else if ("ENCERRADO".equalsIgnoreCase(status)) {
            event.publish();
            event.close();
        }
        return event;
    }

    private void loadActivities(Event event, Connection conn) throws SQLException {
        String selectActivities = "SELECT * FROM activities WHERE event_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(selectActivities)) {
            ps.setLong(1, event.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Activity act = new Activity(
                            rs.getLong("id"),
                            rs.getString("title"),
                            rs.getString("description"),
                            rs.getString("type"),
                            rs.getTimestamp("start_time").toLocalDateTime(),
                            rs.getTimestamp("end_time").toLocalDateTime(),
                            rs.getString("location"),
                            rs.getInt("capacity")
                    );
                    act.setEnrolledCount(rs.getInt("enrolled_count"));
                    event.addActivity(act);
                }
            }
        }
    }
}
