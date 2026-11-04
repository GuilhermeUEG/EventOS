package com.eventos.adapters.input.desktop;

import com.eventos.adapters.input.desktop.DesktopModels.EventForm;
import com.eventos.adapters.input.desktop.DesktopModels.EventView;
import com.eventos.adapters.input.desktop.DesktopModels.PolicyForm;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

/** Gestão de eventos (RF-04): criar, editar, publicar, cancelar, encerrar e configurar o certificado. */
final class EventsPanel extends JPanel implements EventAware {
    private final AppContext ctx;
    private final DefaultTableModel model = UiSupport.readOnlyModel(
            "ID", "Título", "Situação", "Período", "Capacidade", "Prazo de inscrição", "Fuso", "Critério de certificado");
    private final JTable table = UiSupport.table(model);
    private List<EventView> events = new ArrayList<>();

    EventsPanel(AppContext ctx) {
        super(new BorderLayout(8, 8));
        this.ctx = ctx;
        setBorder(new EmptyBorder(10, 10, 10, 10));

        JButton create = UiSupport.button("Novo evento", this::createEvent);
        JButton edit = UiSupport.button("Editar", this::editEvent);
        JButton publish = UiSupport.button("Publicar", () -> transition("Publicando evento", false, "publish"));
        JButton cancel = UiSupport.button("Cancelar evento", () -> transition("Cancelando evento", true, "cancel"));
        JButton finish = UiSupport.button("Encerrar evento", () -> transition("Encerrando evento", true, "finish"));
        JButton policy = UiSupport.button("Critério de certificado…", this::changePolicy);
        add(UiSupport.toolbar(create, edit, publish, cancel, finish, policy), BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    @Override
    public void onEventsLoaded(List<EventView> loaded) {
        events = loaded;
        model.setRowCount(0);
        for (EventView e : loaded) {
            model.addRow(new Object[]{e.id(), e.title(), e.statusName(),
                    UiSupport.dateTime(e.period().start()) + " a " + UiSupport.dateTime(e.period().end()),
                    e.maxCapacity(), UiSupport.dateTime(e.registrationDeadline()), e.timeZone(),
                    e.certificatePolicy() == null ? "-" : e.certificatePolicy().policyDescription()});
        }
    }

    private EventView selected() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= events.size()) {
            ctx.warn("Selecione um evento na tabela.");
            return null;
        }
        return events.get(table.convertRowIndexToModel(row));
    }

    private void createEvent() {
        EventForm form = EventFormDialog.prompt(ctx.frame(), null);
        if (form == null) return;
        ctx.run("Criando evento", () -> ctx.client().createEvent(form), created -> {
            ctx.info("Evento criado como rascunho. Cadastre ao menos uma atividade e então publique.");
            ctx.refreshAll();
        });
    }

    private void editEvent() {
        EventView event = selected();
        if (event == null) return;
        EventForm form = EventFormDialog.prompt(ctx.frame(), event);
        if (form == null) return;
        ctx.run("Salvando alterações", () -> ctx.client().updateEvent(event.id(), form), updated -> ctx.refreshAll());
    }

    private void transition(String message, boolean confirm, String action) {
        EventView event = selected();
        if (event == null) return;
        if (confirm && !ctx.confirm("Confirma a operação sobre o evento \"" + event.title() + "\"? Ela não pode ser desfeita.")) {
            return;
        }
        ctx.run(message, () -> switch (action) {
            case "publish" -> ctx.client().publishEvent(event.id());
            case "cancel" -> ctx.client().cancelEvent(event.id());
            default -> ctx.client().finishEvent(event.id());
        }, result -> ctx.refreshAll());
    }

    private void changePolicy() {
        EventView event = selected();
        if (event == null) return;
        ctx.run("Carregando atividades", () -> ctx.client().listActivities(event.id()), activities -> {
            CertificatePolicyEditor editor = new CertificatePolicyEditor(activities);
            editor.load(event.certificatePolicy());
            while (true) {
                int option = JOptionPane.showConfirmDialog(ctx.frame(), editor,
                        "Critério de certificado — " + event.title(), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
                if (option != JOptionPane.OK_OPTION) return;
                PolicyForm policy = editor.toForm();
                if (policy == null) {
                    ctx.warn(editor.missingSelectionMessage());
                    continue;
                }
                ctx.run("Salvando critério", () -> ctx.client().changeCertificatePolicy(event.id(), policy),
                        updated -> ctx.refreshAll());
                return;
            }
        });
    }
}
