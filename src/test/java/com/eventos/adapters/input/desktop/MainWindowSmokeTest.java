package com.eventos.adapters.input.desktop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.eventos.adapters.input.desktop.DesktopModels.UserView;
import com.eventos.domain.model.UserRole;
import com.eventos.support.TestApp;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Teste de fumaça da janela real: constrói todas as abas contra uma sessão autenticada para pegar erros
 * de montagem da interface. É ignorado quando não há ambiente gráfico (por exemplo, em CI sem display).
 */
@DisplayName("Janela principal do desktop: montagem das abas por perfil")
class MainWindowSmokeTest {

    private JTabbedPane findTabs(Container container) {
        for (Component child : container.getComponents()) {
            if (child instanceof JTabbedPane tabs) return tabs;
            if (child instanceof Container nested) {
                JTabbedPane found = findTabs(nested);
                if (found != null) return found;
            }
        }
        return null;
    }

    private int buildWindowAndCountTabs(TestApp app, UserRole role) throws Exception {
        var user = app.createUser("Janela " + role.name(), role);
        EventOsApiClient client = new EventOsApiClient(app.baseUrl);
        UserView session = client.login(user.getEmail().getValue(), TestApp.DEFAULT_PASSWORD);
        AtomicInteger tabCount = new AtomicInteger(-1);
        SwingUtilities.invokeAndWait(() -> {
            MainWindow window = new MainWindow(client, session, () -> { });
            window.pack();
            JTabbedPane tabs = findTabs(window.getContentPane());
            tabCount.set(tabs == null ? 0 : tabs.getTabCount());
            window.dispose();
        });
        return tabCount.get();
    }

    @Test
    @DisplayName("administrador vê 6 abas (inclui Usuários); organizador vê 5")
    void tabsDependOnRole() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "sem ambiente gráfico");
        try (TestApp app = TestApp.start()) {
            assertEquals(6, buildWindowAndCountTabs(app, UserRole.ADMIN));
            assertEquals(5, buildWindowAndCountTabs(app, UserRole.ORGANIZER));
        }
    }
}
