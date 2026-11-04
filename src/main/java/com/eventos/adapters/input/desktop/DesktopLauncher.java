package com.eventos.adapters.input.desktop;

import com.eventos.adapters.input.desktop.DesktopModels.UserView;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Ponto de entrada da interface desktop: cliente Swing da mesma API REST usada pelo site.
 * Mostra o login e, após autenticar um organizador ou administrador, a janela principal.
 */
public final class DesktopLauncher {
    private DesktopLauncher() {
    }

    public static void launch(String baseUrl) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // mantém o visual padrão do Swing
            }
            showLogin(baseUrl);
        });
    }

    private static void showLogin(String baseUrl) {
        EventOsApiClient client = new EventOsApiClient(baseUrl);
        UserView user = LoginDialog.prompt(client);
        if (user == null) {
            System.exit(0);
            return;
        }
        MainWindow window = new MainWindow(client, user, () -> showLogin(baseUrl));
        window.setVisible(true);
        window.refreshAll();
    }
}
