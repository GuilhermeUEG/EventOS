package com.eventos.adapters.input.desktop;

import com.eventos.adapters.input.desktop.DesktopModels.EventView;
import com.eventos.adapters.input.desktop.DesktopModels.UserView;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTabbedPane;
import javax.swing.border.EmptyBorder;

/** Janela principal do organizador/administrador: uma aba por área funcional, todas consumindo a API. */
final class MainWindow extends JFrame {
    private final AppContext ctx;
    private final List<EventAware> eventAwarePanels = new ArrayList<>();
    private final UsersPanel usersPanel;
    private final Runnable onLogout;

    MainWindow(EventOsApiClient client, UserView user, Runnable onLogout) {
        super("EventOS — Painel do " + user.roleName() + " (" + user.name() + ")");
        this.onLogout = onLogout;
        this.ctx = new AppContext(client, user, this);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        if (DesktopBranding.icon() != null) setIconImage(DesktopBranding.icon());
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                exit();
            }
        });

        JTabbedPane tabs = new JTabbedPane();
        EventsPanel events = new EventsPanel(ctx);
        ActivitiesPanel activities = new ActivitiesPanel(ctx);
        AttendancePanel attendance = new AttendancePanel(ctx);
        SurveysPanel surveys = new SurveysPanel(ctx);
        ReportsPanel reports = new ReportsPanel(ctx);
        eventAwarePanels.addAll(List.of(events, activities, attendance, surveys, reports));
        tabs.addTab("Eventos", events);
        tabs.addTab("Atividades e Salas", activities);
        tabs.addTab("Frequência e QR Code", attendance);
        tabs.addTab("Avaliações e Métricas", surveys);
        tabs.addTab("Relatórios e Certificados", reports);
        if (user.isAdmin()) {
            usersPanel = new UsersPanel(ctx);
            tabs.addTab("Usuários", usersPanel);
        } else {
            usersPanel = null;
        }

        JPanel status = new JPanel(new BorderLayout());
        status.setBorder(new EmptyBorder(4, 8, 4, 8));
        status.add(ctx.statusLabel(), BorderLayout.WEST);

        setJMenuBar(buildMenu());
        add(tabs, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
        ctx.onRefreshAll(this::refreshAll);
        setMinimumSize(new Dimension(1000, 640));
        setSize(1180, 720);
        setLocationRelativeTo(null);
    }

    private JMenuBar buildMenu() {
        JMenu file = new JMenu("Arquivo");
        JMenuItem refresh = new JMenuItem("Atualizar tudo");
        refresh.addActionListener(e -> refreshAll());
        JMenuItem password = new JMenuItem("Alterar minha senha…");
        password.addActionListener(e -> changePassword());
        JMenuItem logout = new JMenuItem("Sair da conta");
        logout.addActionListener(e -> logout());
        JMenuItem exit = new JMenuItem("Fechar o EventOS");
        exit.addActionListener(e -> exit());
        file.add(refresh);
        file.add(password);
        file.addSeparator();
        file.add(logout);
        file.add(exit);
        JMenuBar bar = new JMenuBar();
        bar.add(file);
        return bar;
    }

    /** Recarrega os eventos uma única vez e avisa todas as abas. */
    void refreshAll() {
        ctx.run("Atualizando dados", () -> ctx.client().listEvents(), (List<EventView> events) -> {
            eventAwarePanels.forEach(panel -> panel.onEventsLoaded(events));
            if (usersPanel != null) usersPanel.reload();
        });
    }

    private void changePassword() {
        JPasswordField current = new JPasswordField(22);
        JPasswordField next = new JPasswordField(22);
        JPasswordField confirm = new JPasswordField(22);
        UiSupport.Form form = new UiSupport.Form();
        form.add("Senha atual:", current);
        form.add("Nova senha (mín. 6 caracteres):", next);
        form.add("Confirmar nova senha:", confirm);
        if (JOptionPane.showConfirmDialog(this, form, "Alterar minha senha", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        String newPassword = new String(next.getPassword());
        if (!newPassword.equals(new String(confirm.getPassword()))) {
            ctx.warn("A confirmação não confere com a nova senha.");
            return;
        }
        String currentPassword = new String(current.getPassword());
        ctx.run("Alterando senha", () -> {
            ctx.client().changePassword(ctx.user().id(), currentPassword, newPassword);
            return Boolean.TRUE;
        }, ok -> ctx.info("Senha alterada com sucesso."));
    }

    private void logout() {
        ctx.run("Encerrando sessão", () -> {
            try {
                ctx.client().logout();
            } catch (ApiException ignored) {
                // a sessão local já foi descartada; o servidor expira o token por conta própria
            }
            return Boolean.TRUE;
        }, ok -> {
            dispose();
            onLogout.run();
        });
    }

    private void exit() {
        try {
            ctx.client().logout();
        } catch (ApiException ignored) {
            // encerrando de qualquer forma
        }
        dispose();
        System.exit(0);
    }
}
