package com.eventos.adapters.output.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Criação e migração reproduzível do esquema (RNF-04, D-03)")
class SchemaMigratorTest {

    private int count(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    @DisplayName("divide o script em comandos e ignora comentários")
    void splitsStatements() {
        List<String> statements = SchemaMigrator.statements("-- comentário\nCREATE TABLE a (id INT);\n\n-- outro\nINSERT INTO a VALUES (1);\n");
        assertEquals(List.of("CREATE TABLE a (id INT)", "INSERT INTO a VALUES (1)"), statements);
    }

    @Test
    @DisplayName("cria o esquema do zero e registra as versões; rodar de novo não altera nada")
    void freshDatabaseAndIdempotency() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:h2:mem:fresh_schema;DB_CLOSE_DELAY=-1", "sa", "")) {
            SchemaMigrator migrator = new SchemaMigrator();
            migrator.migrate(c);
            migrator.migrate(c);
            assertEquals(4, count(c, "SELECT COUNT(*) FROM schema_version"));
            for (String table : new String[]{"USERS", "EVENTS", "ACTIVITIES", "SPEAKERS", "REGISTRATIONS", "REGISTRATION_ACTIVITIES",
                    "ATTENDANCE_RECORDS", "FEEDBACK_SURVEYS", "SURVEY_QUESTIONS", "SURVEY_RESPONSES", "CERTIFICATES",
                    "CERTIFICATE_DELIVERIES"}) {
                assertEquals(1, count(c, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_NAME = '" + table + "'"), table);
            }
            assertEquals(1, count(c, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'FEEDBACK_SURVEYS' AND COLUMN_NAME = 'ANONYMOUS_RESPONSES'"));
            assertEquals(1, count(c, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'EVENTS' AND COLUMN_NAME = 'CERT_POLICY_ACTIVITIES'"));
            assertTrue(count(c, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEXES WHERE INDEX_NAME LIKE 'IDX_%'") >= 8);
        }
    }

    @Test
    @DisplayName("adota um banco criado por versão anterior, preservando os dados e acrescentando o que falta")
    void adoptsLegacyDatabase() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:h2:mem:legacy_schema;DB_CLOSE_DELAY=-1", "sa", "");
             Statement s = c.createStatement()) {
            s.execute("""
                    CREATE TABLE users (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255) NOT NULL,
                        email VARCHAR(255) NOT NULL UNIQUE, password_hash VARCHAR(255) NOT NULL,
                        role VARCHAR(50) NOT NULL, created_at TIMESTAMP NOT NULL)""");
            s.execute("""
                    CREATE TABLE events (id BIGINT AUTO_INCREMENT PRIMARY KEY, title VARCHAR(255) NOT NULL, description TEXT,
                        start_date TIMESTAMP NOT NULL, end_date TIMESTAMP NOT NULL, status VARCHAR(50) NOT NULL,
                        organizer_id BIGINT, max_capacity INT NOT NULL DEFAULT 500, cert_policy_type VARCHAR(50),
                        cert_policy_param DOUBLE)""");
            s.execute("INSERT INTO users (name, email, password_hash, role, created_at) VALUES ('Antigo', 'a@a.com', 'h', 'ORGANIZER', CURRENT_TIMESTAMP)");
            s.execute("INSERT INTO events (title, start_date, end_date, status, cert_policy_type, cert_policy_param) "
                    + "VALUES ('Legado', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'PUBLISHED', 'MANDATORY_COUNT', 2)");

            new SchemaMigrator().migrate(c);

            assertEquals(1, count(c, "SELECT COUNT(*) FROM users WHERE email = 'a@a.com'"));
            assertEquals(1, count(c, "SELECT COUNT(*) FROM events WHERE title = 'Legado'"));
            assertEquals(1, count(c, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'EVENTS' AND COLUMN_NAME = 'TIME_ZONE'"));
            assertEquals(1, count(c, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'EVENTS' AND COLUMN_NAME = 'CERT_POLICY_ACTIVITIES'"));
            assertEquals("America/Sao_Paulo", firstString(c, "SELECT time_zone FROM events WHERE title = 'Legado'"));
            assertEquals(4, count(c, "SELECT COUNT(*) FROM schema_version"));
        }
    }

    private String firstString(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }
}
