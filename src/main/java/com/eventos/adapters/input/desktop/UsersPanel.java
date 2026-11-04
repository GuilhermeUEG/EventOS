package com.eventos.adapters.input.desktop;

import com.eventos.adapters.input.desktop.DesktopModels.UserView;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

/** Gestão de usuários e perfis, exclusiva do administrador (a API também valida o perfil). */
final class UsersPanel extends JPanel {
    private static final String[][] ROLES = {
            {"ORGANIZER", "Organizador"}, {"ADMIN", "Administrador"}, {"PARTICIPANT", "Participante"}};

    private final AppContext ctx;
    private final DefaultTableModel model = UiSupport.readOnlyModel("ID", "Nome", "E-mail", "Perfil");
    private final JTable table = UiSupport.table(model);
    private List<UserView> users = new ArrayList<>();

    UsersPanel(AppContext ctx) {
        super(new BorderLayout(8, 8));
        this.ctx = ctx;
        setBorder(new EmptyBorder(10, 10, 10, 10));
        add(UiSupport.toolbar(
                UiSupport.button("Atualizar", this::reload),
                UiSupport.button("Novo usuário…", this::createUser),
                UiSupport.button("Alterar perfil…", this::changeRole)), BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    void reload() {
        ctx.run("Carregando usuários", () -> ctx.client().listUsers(), list -> {
            users = list;
            model.setRowCount(0);
            for (UserView u : list) model.addRow(new Object[]{u.id(), u.name(), u.email(), u.roleName()});
        });
    }

    private static JComboBox<String> roleCombo() {
        JComboBox<String> combo = new JComboBox<>();
        for (String[] role : ROLES) combo.addItem(role[1]);
        return combo;
    }

    private void createUser() {
        JTextField name = new JTextField(24);
        JTextField email = new JTextField(24);
        JPasswordField password = new JPasswordField(24);
        JComboBox<String> role = roleCombo();
        UiSupport.Form form = new UiSupport.Form();
        form.add("Nome:", name);
        form.add("E-mail:", email);
        form.add("Senha (mín. 6 caracteres):", password);
        form.add("Perfil:", role);
        if (JOptionPane.showConfirmDialog(ctx.frame(), form, "Novo usuário", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        String pwd = new String(password.getPassword());
        String selectedRole = ROLES[role.getSelectedIndex()][0];
        ctx.run("Criando usuário", () -> ctx.client().createUser(name.getText().trim(), email.getText().trim(), pwd, selectedRole),
                created -> reload());
    }

    private void changeRole() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= users.size()) {
            ctx.warn("Selecione um usuário na tabela.");
            return;
        }
        UserView user = users.get(table.convertRowIndexToModel(row));
        JComboBox<String> role = roleCombo();
        UiSupport.Form form = new UiSupport.Form();
        form.add("Usuário:", new javax.swing.JLabel(user.name() + " (" + user.roleName() + ")"));
        form.add("Novo perfil:", role);
        if (JOptionPane.showConfirmDialog(ctx.frame(), form, "Alterar perfil", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        String selectedRole = ROLES[role.getSelectedIndex()][0];
        ctx.run("Alterando perfil", () -> ctx.client().changeRole(user.id(), selectedRole), updated -> reload());
    }
}
