package com.eventos.adapters.input.desktop;

import com.eventos.adapters.input.desktop.DesktopModels.UserView;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingWorker;

/**
 * Tela de entrada do desktop: autentica pela API (e-mail e senha) e permite criar uma conta de
 * participante. O painel é restrito a organizadores e administradores; o servidor decide o perfil.
 */
final class LoginDialog extends JDialog {
    private final EventOsApiClient client;
    private final JTextField email = new JTextField(26);
    private final JPasswordField password = new JPasswordField(26);
    private final JLabel status = new JLabel(" ");
    private final JButton login = new JButton("Entrar");
    private final JButton register = new JButton("Criar conta…");
    private final JButton cancel = new JButton("Sair");
    private UserView result;

    private LoginDialog(EventOsApiClient client) {
        super((java.awt.Frame) null, "EventOS — Entrar", true);
        this.client = client;

        UiSupport.Form form = new UiSupport.Form();
        form.addWide(new JLabel("Painel do organizador — informe suas credenciais."));
        form.add("E-mail:", email);
        form.add("Senha:", password);

        JPanel buttons = UiSupport.toolbar(login, register, cancel);
        JPanel content = new JPanel(new BorderLayout(6, 6));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(form, BorderLayout.NORTH);
        content.add(status, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);

        login.addActionListener(e -> doLogin());
        password.addActionListener(e -> doLogin());
        register.addActionListener(e -> doRegister());
        cancel.addActionListener(e -> dispose());
        getRootPane().setDefaultButton(login);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        if (DesktopBranding.icon() != null) setIconImage(DesktopBranding.icon());
        pack();
        setLocationRelativeTo(null);
    }

    /** Mostra o diálogo; devolve o usuário autenticado ou {@code null} se o usuário optar por sair. */
    static UserView prompt(EventOsApiClient client) {
        LoginDialog dialog = new LoginDialog(client);
        dialog.setVisible(true);
        return dialog.result;
    }

    private void doLogin() {
        String emailText = email.getText().trim();
        String passwordText = new String(password.getPassword());
        if (emailText.isEmpty() || passwordText.isEmpty()) {
            status.setText("Informe e-mail e senha.");
            return;
        }
        background("Entrando...", () -> client.login(emailText, passwordText), user -> {
            if (user.isParticipant()) {
                JOptionPane.showMessageDialog(this,
                        "Este painel é destinado a organizadores e administradores.\n"
                                + "Participantes acessam inscrição, agenda e certificados pelo site público.",
                        "Acesso restrito", JOptionPane.INFORMATION_MESSAGE);
                background("Encerrando sessão...", () -> {
                    client.logout();
                    return null;
                }, ignored -> status.setText("Entre com uma conta de organizador ou administrador."));
                return;
            }
            result = user;
            dispose();
        });
    }

    private void doRegister() {
        JTextField name = new JTextField(24);
        JTextField newEmail = new JTextField(24);
        JPasswordField newPassword = new JPasswordField(24);
        UiSupport.Form form = new UiSupport.Form();
        form.add("Nome:", name);
        form.add("E-mail:", newEmail);
        form.add("Senha (mín. 6 caracteres):", newPassword);
        if (JOptionPane.showConfirmDialog(this, form, "Criar conta de participante", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        String pwd = new String(newPassword.getPassword());
        background("Criando conta...", () -> client.register(name.getText().trim(), newEmail.getText().trim(), pwd), user -> {
            email.setText(user.email());
            password.setText("");
            JOptionPane.showMessageDialog(this,
                    "Conta de participante criada. Ela serve para o site público; o painel desktop exige\n"
                            + "um perfil de organizador ou administrador, concedido por um administrador.",
                    "Conta criada", JOptionPane.INFORMATION_MESSAGE);
        });
    }

    private <T> void background(String message, Callable<T> work, Consumer<T> onSuccess) {
        setBusy(true, message);
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return work.call();
            }

            @Override
            protected void done() {
                setBusy(false, " ");
                try {
                    onSuccess.accept(get());
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    status.setText(cause.getMessage());
                    JOptionPane.showMessageDialog(LoginDialog.this, cause.getMessage(), "Não foi possível concluir",
                            JOptionPane.ERROR_MESSAGE);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }.execute();
    }

    private void setBusy(boolean busy, String message) {
        status.setText(message);
        login.setEnabled(!busy);
        register.setEnabled(!busy);
        setCursor(Cursor.getPredefinedCursor(busy ? Cursor.WAIT_CURSOR : Cursor.DEFAULT_CURSOR));
    }
}
