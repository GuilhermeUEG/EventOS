package com.eventos.adapters.input.desktop;

import java.awt.Image;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;

/**
 * Identidade visual do painel desktop: o mesmo ícone do site (arquivo servido em {@code /public/favicon.png}),
 * usado na janela e na barra de tarefas. Se o arquivo não estiver disponível, a janela segue sem ícone.
 */
final class DesktopBranding {
    private static final String ICON_RESOURCE = "/public/favicon.png";
    private static Image icon;
    private static boolean loaded;

    private DesktopBranding() {
    }

    static synchronized Image icon() {
        if (!loaded) {
            loaded = true;
            try (InputStream in = DesktopBranding.class.getResourceAsStream(ICON_RESOURCE)) {
                if (in != null) icon = ImageIO.read(in);
            } catch (IOException ignored) {
                // sem ícone: não é um erro para o usuário
            }
        }
        return icon;
    }
}
