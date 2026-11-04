package com.eventos.adapters.output.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Conexão com o banco relacional H2 e criação/evolução reproduzível do esquema (RNF-04, D-03).
 *
 * <p>A URL JDBC vem da propriedade {@code eventos.db.url} ou da variável {@code EVENTOS_DB_URL};
 * por padrão usa o arquivo {@code ./eventosdb}. Os testes apontam para um banco em memória.</p>
 */
public class DatabaseManager {
    public static final String URL_PROPERTY = "eventos.db.url";
    public static final String URL_ENV = "EVENTOS_DB_URL";
    private static final String DEFAULT_URL = "jdbc:h2:./eventosdb;DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE";
    private static final String USER = "sa";
    private static final String PASSWORD = "";
    private static final Logger log = LoggerFactory.getLogger(DatabaseManager.class);

    private DatabaseManager() {
    }

    public static String jdbcUrl() {
        String fromProperty = System.getProperty(URL_PROPERTY);
        if (fromProperty != null && !fromProperty.isBlank()) return fromProperty;
        String fromEnv = System.getenv(URL_ENV);
        return fromEnv != null && !fromEnv.isBlank() ? fromEnv : DEFAULT_URL;
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl(), USER, PASSWORD);
    }

    /** Cria o esquema do zero ou aplica as migrações pendentes. Pode ser chamado várias vezes. */
    public static void initializeDatabase() {
        try (Connection conn = getConnection()) {
            new SchemaMigrator().migrate(conn);
            log.info("Banco de dados relacional H2 pronto ({}).", jdbcUrl());
        } catch (SQLException e) {
            log.error("Erro ao inicializar o esquema do banco de dados: {}", e.getMessage());
            throw new IllegalStateException("Não foi possível inicializar o banco de dados.", e);
        }
    }
}
