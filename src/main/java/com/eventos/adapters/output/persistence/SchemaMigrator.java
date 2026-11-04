package com.eventos.adapters.output.persistence;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Migrador de esquema mínimo, sem dependências externas (D-03). Executa, em ordem, os scripts
 * {@code db/migration/V<n>__*.sql} listados em {@code MANIFEST.txt} e registra cada versão aplicada
 * na tabela {@code schema_version}, de modo que o banco pode ser criado do zero ou evoluído com segurança.
 */
public class SchemaMigrator {
    private static final Logger log = LoggerFactory.getLogger(SchemaMigrator.class);
    private static final String BASE = "/db/migration/";
    private static final Pattern VERSION = Pattern.compile("^V(\\d+)__.*\\.sql$");

    public void migrate(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS schema_version (
                        version INT PRIMARY KEY,
                        script VARCHAR(255) NOT NULL,
                        applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
        }
        Set<Integer> applied = appliedVersions(connection);
        for (String script : readManifest()) {
            Matcher m = VERSION.matcher(script);
            if (!m.matches()) {
                throw new IllegalStateException("Nome de script de migração inválido: " + script);
            }
            int version = Integer.parseInt(m.group(1));
            if (applied.contains(version)) continue;
            log.info("Aplicando migração {}", script);
            for (String sql : statements(readResource(BASE + script))) {
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute(sql);
                }
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO schema_version (version, script) VALUES (?, ?)")) {
                insert.setInt(1, version);
                insert.setString(2, script);
                insert.executeUpdate();
            }
        }
    }

    private Set<Integer> appliedVersions(Connection connection) throws SQLException {
        Set<Integer> versions = new HashSet<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT version FROM schema_version")) {
            while (rs.next()) versions.add(rs.getInt(1));
        }
        return versions;
    }

    private List<String> readManifest() {
        List<String> scripts = new ArrayList<>();
        for (String line : readResource(BASE + "MANIFEST.txt").split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) scripts.add(trimmed);
        }
        return scripts;
    }

    /** Divide o script em comandos, ignorando linhas de comentário. */
    static List<String> statements(String script) {
        StringBuilder withoutComments = new StringBuilder();
        for (String line : script.split("\\R")) {
            if (!line.trim().startsWith("--")) withoutComments.append(line).append('\n');
        }
        List<String> result = new ArrayList<>();
        for (String part : withoutComments.toString().split(";")) {
            if (!part.isBlank()) result.add(part.trim());
        }
        return result;
    }

    private String readResource(String path) {
        try (InputStream in = SchemaMigrator.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Recurso de migração não encontrado: " + path);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line).append('\n');
                return sb.toString();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler " + path, e);
        }
    }
}
