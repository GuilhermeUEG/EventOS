package com.eventos.adapters.output.persistence;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    private static final String DB_URL = "jdbc:h2:file:./data/eventos;DB_CLOSE_ON_EXIT=FALSE;AUTO_RECONNECT=TRUE";
    private static final String USER = "sa";
    private static final String PASSWORD = "";

    public static Connection getConnection() throws SQLException {
        // Garantir que a pasta do banco de dados exista
        File dataDir = new File("./data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        return DriverManager.getConnection(DB_URL, USER, PASSWORD);
    }

    public static void initializeDatabase() {
        String createParticipantsTable = "CREATE TABLE IF NOT EXISTS participants (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "name VARCHAR(255) NOT NULL," +
                "email VARCHAR(255) UNIQUE NOT NULL," +
                "password_hash VARCHAR(255) NOT NULL," +
                "role VARCHAR(50) NOT NULL" +
                ");";

        String createEventsTable = "CREATE TABLE IF NOT EXISTS events (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "title VARCHAR(255) NOT NULL," +
                "description VARCHAR(2000)," +
                "status VARCHAR(50) NOT NULL," +
                "start_date TIMESTAMP NOT NULL," +
                "end_date TIMESTAMP NOT NULL" +
                ");";

        String createActivitiesTable = "CREATE TABLE IF NOT EXISTS activities (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "event_id BIGINT NOT NULL," +
                "title VARCHAR(255) NOT NULL," +
                "description VARCHAR(2000)," +
                "type VARCHAR(100) NOT NULL," +
                "start_time TIMESTAMP NOT NULL," +
                "end_time TIMESTAMP NOT NULL," +
                "location VARCHAR(255) NOT NULL," +
                "capacity INT NOT NULL," +
                "enrolled_count INT NOT NULL," +
                "FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE" +
                ");";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createParticipantsTable);
            stmt.execute(createEventsTable);
            stmt.execute(createActivitiesTable);
            System.out.println("Banco de dados H2 inicializado com sucesso e tabelas criadas!");
        } catch (SQLException e) {
            System.err.println("Erro crítico ao inicializar banco de dados: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
