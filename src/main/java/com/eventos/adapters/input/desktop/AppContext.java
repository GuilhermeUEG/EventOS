package com.eventos.adapters.input.desktop;

import com.eventos.adapters.input.desktop.DesktopModels.UserView;
import java.awt.Cursor;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

/**
 * Estado compartilhado das telas: cliente da API, usuário autenticado e execução de chamadas fora da
 * thread de eventos (EDT), com cursor de espera, mensagem na barra de status e erros da API em diálogo.
 */
final class AppContext {
    private final EventOsApiClient client;
    private final UserView user;
    private final JFrame frame;
    private final JLabel statusLabel = new JLabel(" ");
    private Runnable refreshAll = () -> { };
    private int pending;

    AppContext(EventOsApiClient client, UserView user, JFrame frame) {
        this.client = client;
        this.user = user;
        this.frame = frame;
    }

    EventOsApiClient client() {
        return client;
    }

    UserView user() {
        return user;
    }

    JFrame frame() {
        return frame;
    }

    JLabel statusLabel() {
        return statusLabel;
    }

    void onRefreshAll(Runnable refreshAll) {
        this.refreshAll = refreshAll;
    }

    void refreshAll() {
        refreshAll.run();
    }

    /** Executa {@code work} em segundo plano e entrega o resultado à EDT; falhas viram diálogo de erro. */
    <T> void run(String message, Callable<T> work, Consumer<T> onSuccess) {
        beginBusy(message);
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return work.call();
            }

            @Override
            protected void done() {
                try {
                    T result = get();
                    endBusy("Pronto.");
                    onSuccess.accept(result);
                } catch (ExecutionException e) {
                    endBusy("Falha: " + message);
                    showError(e.getCause() != null ? e.getCause() : e);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    endBusy("Operação interrompida.");
                }
            }
        }.execute();
    }

    void showError(Throwable error) {
        String text = error.getMessage() != null && !error.getMessage().isBlank()
                ? error.getMessage() : "Ocorreu um erro inesperado.";
        JOptionPane.showMessageDialog(frame, text, "Não foi possível concluir", JOptionPane.ERROR_MESSAGE);
    }

    void info(String message) {
        JOptionPane.showMessageDialog(frame, message, "EventOS", JOptionPane.INFORMATION_MESSAGE);
    }

    void warn(String message) {
        JOptionPane.showMessageDialog(frame, message, "Atenção", JOptionPane.WARNING_MESSAGE);
    }

    boolean confirm(String message) {
        return JOptionPane.showConfirmDialog(frame, message, "Confirmação", JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }

    private void beginBusy(String message) {
        pending++;
        statusLabel.setText(message + "...");
        frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    }

    private void endBusy(String message) {
        pending = Math.max(0, pending - 1);
        statusLabel.setText(message);
        if (pending == 0) {
            SwingUtilities.invokeLater(() -> frame.setCursor(Cursor.getDefaultCursor()));
        }
    }
}
