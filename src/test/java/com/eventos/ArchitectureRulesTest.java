package com.eventos;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Regra de dependência da arquitetura hexagonal (RNF-01, ROO-09), verificada sobre os imports reais do
 * código-fonte: dentro não se conhece fora; adaptadores conversam com a aplicação só por portas.
 */
@DisplayName("Arquitetura hexagonal: a regra de dependência vale no código (ROO-09)")
class ArchitectureRulesTest {
    private static final Path ROOT = Path.of("src/main/java/com/eventos");

    private List<String> importsViolating(String packagePath, List<String> forbiddenPrefixes) throws IOException {
        List<String> violations = new ArrayList<>();
        Path base = ROOT.resolve(packagePath);
        try (Stream<Path> files = Files.walk(base)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList())) {
                for (String line : Files.readAllLines(file)) {
                    String trimmed = line.trim();
                    if (!trimmed.startsWith("import ")) continue;
                    String imported = trimmed.substring("import ".length()).replace("static ", "").replace(";", "").trim();
                    for (String forbidden : forbiddenPrefixes) {
                        if (imported.startsWith(forbidden)) {
                            violations.add(ROOT.relativize(file) + " importa " + imported);
                        }
                    }
                }
            }
        }
        return violations;
    }

    private void assertNoViolations(String packagePath, String... forbiddenPrefixes) throws IOException {
        List<String> violations = importsViolating(packagePath, List.of(forbiddenPrefixes));
        assertTrue(violations.isEmpty(), "Dependências proibidas em " + packagePath + ":\n" + String.join("\n", violations));
    }

    @Test
    @DisplayName("o domínio não depende de aplicação, adaptadores, frameworks, JDBC, Swing, JSON, PDF nem log")
    void domainIsIsolated() throws IOException {
        assertNoViolations("domain",
                "com.eventos.application", "com.eventos.adapters", "io.javalin", "java.sql", "javax.sql", "javax.swing",
                "java.awt", "com.fasterxml", "com.lowagie", "com.google", "org.slf4j", "org.h2",
                "jakarta.mail", "jakarta.activation", "org.eclipse.angus");
    }

    @Test
    @DisplayName("a aplicação depende só do domínio e de suas próprias portas, nunca de adaptadores ou tecnologia")
    void applicationDependsOnPortsOnly() throws IOException {
        assertNoViolations("application",
                "com.eventos.adapters", "io.javalin", "java.sql", "javax.sql", "javax.swing", "java.awt",
                "com.fasterxml", "com.lowagie", "com.google", "org.h2",
                "jakarta.mail", "jakarta.activation", "org.eclipse.angus");
    }

    @Test
    @DisplayName("controllers REST usam casos de uso: não tocam persistência, segurança concreta, PDF nem JDBC")
    void restAdapterGoesThroughUseCases() throws IOException {
        assertNoViolations("adapters/input/rest",
                "com.eventos.adapters.output", "com.eventos.adapters.input.desktop", "java.sql", "javax.sql", "javax.swing");
    }

    @Test
    @DisplayName("o desktop é cliente HTTP puro: não importa domínio nem aplicação, nem banco (RNF-02)")
    void desktopIsAnHttpClient() throws IOException {
        assertNoViolations("adapters/input/desktop",
                "com.eventos.domain", "com.eventos.application", "com.eventos.adapters.output",
                "com.eventos.adapters.input.rest", "java.sql", "javax.sql", "org.h2");
    }

    @Test
    @DisplayName("adaptadores de saída não conhecem adaptadores de entrada")
    void outputAdaptersDoNotKnowInputAdapters() throws IOException {
        assertNoViolations("adapters/output", "com.eventos.adapters.input");
    }

    @Test
    @DisplayName("nenhum código usa Spring (proibido pela especificação)")
    void noSpringAnywhere() throws IOException {
        assertNoViolations("", "org.springframework");
    }
}
