package com.eventos.adapters.input.desktop;

import com.eventos.adapters.input.desktop.DesktopModels.ActivityForm;
import com.eventos.adapters.input.desktop.DesktopModels.ActivityQuery;
import com.eventos.adapters.input.desktop.DesktopModels.ActivityView;
import com.eventos.adapters.input.desktop.DesktopModels.EventView;
import com.eventos.adapters.input.desktop.DesktopModels.SpeakerForm;
import com.eventos.adapters.input.desktop.DesktopModels.SpeakerView;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

/**
 * Programação do evento: cadastro de atividades (tipos, trilhas, salas, política de frequência,
 * palestrantes) e consulta com filtros combináveis aplicados pelo servidor (RF-05 a RF-09).
 */
final class ActivitiesPanel extends JPanel implements EventAware {
    private static final String[][] ACTIVITY_TYPES = {
            {"LECTURE", "Palestra"}, {"WORKSHOP", "Oficina / Mini-curso"}, {"ORAL_PRESENTATION", "Apresentação Oral"},
            {"POSTER", "Sessão de Pôster"}, {"ROUND_TABLE", "Mesa Redonda"}, {"OTHER", "Outro"}};
    private static final String[][] ATTENDANCE_POLICIES = {
            {"SINGLE_CHECKIN", "Check-in único"}, {"CHECKIN_CHECKOUT", "Entrada e saída"},
            {"MANUAL_ONLY", "Validação manual exclusiva"}};

    private final AppContext ctx;
    private final JComboBox<EventView> eventCombo = new JComboBox<>();
    private boolean loading;

    // formulário de cadastro
    private final JTextField title = new JTextField(22);
    private final JTextArea description = new JTextArea(2, 22);
    private final JTextField start = new JTextField(16);
    private final JTextField end = new JTextField(16);
    private final JTextField room = new JTextField(16);
    private final JTextField track = new JTextField("Geral", 16);
    private final JTextField capacity = new JTextField("50", 6);
    private final JComboBox<String> type = labelsCombo(ACTIVITY_TYPES);
    private final JComboBox<String> policy = labelsCombo(ATTENDANCE_POLICIES);
    private final JCheckBox requiresRegistration = new JCheckBox("Controla vagas / exige inscrição na atividade", true);
    private final DefaultTableModel speakersModel = new DefaultTableModel(new String[]{"Palestrante", "Papel"}, 0);
    private final JTable speakersTable = new JTable(speakersModel);

    // filtros e lista
    private final JTextField fDate = new JTextField(9);
    private final JTextField fFrom = new JTextField(5);
    private final JTextField fTo = new JTextField(5);
    private final JTextField fTrack = new JTextField(8);
    private final JComboBox<String> fType = new JComboBox<>();
    private final JTextField fRoom = new JTextField(8);
    private final JTextField fSearch = new JTextField(12);
    private final DefaultTableModel listModel = UiSupport.readOnlyModel(
            "ID", "Título", "Tipo", "Início", "Fim", "Sala", "Trilha", "Vagas", "Frequência", "Palestrantes");
    private final JTable listTable = UiSupport.table(listModel);
    private List<ActivityView> shownActivities = List.of();

    ActivitiesPanel(AppContext ctx) {
        super(new BorderLayout(8, 8));
        this.ctx = ctx;
        setBorder(new EmptyBorder(10, 10, 10, 10));

        add(UiSupport.toolbar(new JLabel("Evento:"), eventCombo,
                UiSupport.button("Atualizar lista", this::reloadList)), BorderLayout.NORTH);
        eventCombo.addActionListener(e -> {
            if (!loading) reloadList();
        });

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildForm(), buildList());
        split.setResizeWeight(0.0);
        split.setDividerLocation(400);
        add(split, BorderLayout.CENTER);
    }

    private static JComboBox<String> labelsCombo(String[][] options) {
        JComboBox<String> combo = new JComboBox<>();
        for (String[] option : options) combo.addItem(option[1]);
        return combo;
    }

    private JScrollPane buildForm() {
        LocalDateTime base = LocalDateTime.now().plusDays(7).withHour(9).withMinute(0).withSecond(0).withNano(0);
        start.setText(base.format(UiSupport.DISPLAY));
        end.setText(base.plusHours(2).format(UiSupport.DISPLAY));
        description.setLineWrap(true);
        description.setWrapStyleWord(true);

        UiSupport.Form form = new UiSupport.Form();
        form.add("Título:", title);
        form.add("Descrição:", new JScrollPane(description));
        form.add("Início:", start);
        form.add("Fim:", end);
        form.add("Sala / local:", room);
        form.add("Trilha / espaço:", track);
        form.add("Capacidade:", capacity);
        form.add("Tipo:", type);
        form.add("Frequência:", policy);
        form.addWide(requiresRegistration);

        JScrollPane speakersScroll = new JScrollPane(speakersTable);
        speakersScroll.setPreferredSize(new Dimension(300, 90));
        speakersModel.addRow(new Object[]{"", "Palestrante"});
        form.addWide(speakersScroll);
        form.addWide(UiSupport.toolbar(
                UiSupport.button("+ Palestrante", () -> speakersModel.addRow(new Object[]{"", "Palestrante"})),
                UiSupport.button("− Remover", () -> {
                    int row = speakersTable.getSelectedRow();
                    if (row >= 0) speakersModel.removeRow(row);
                })));
        form.addWide(UiSupport.button("Adicionar atividade", this::addActivity));
        JScrollPane scroll = new JScrollPane(form);
        scroll.setBorder(javax.swing.BorderFactory.createTitledBorder("Nova atividade"));
        return scroll;
    }

    private JPanel buildList() {
        fType.addItem("(todos)");
        for (String[] t : ACTIVITY_TYPES) fType.addItem(t[1]);
        JPanel filters = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 4));
        filters.add(new JLabel("Data (aaaa-mm-dd):"));
        filters.add(fDate);
        filters.add(new JLabel("Início de:"));
        filters.add(fFrom);
        filters.add(new JLabel("até:"));
        filters.add(fTo);
        filters.add(new JLabel("Trilha:"));
        filters.add(fTrack);
        filters.add(new JLabel("Tipo:"));
        filters.add(fType);
        filters.add(new JLabel("Sala:"));
        filters.add(fRoom);
        filters.add(new JLabel("Busca:"));
        filters.add(fSearch);
        filters.add(UiSupport.button("Filtrar", this::reloadList));
        filters.add(UiSupport.button("Limpar", this::clearFilters));

        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.add(filters, BorderLayout.NORTH);
        panel.add(new JScrollPane(listTable), BorderLayout.CENTER);
        panel.add(UiSupport.toolbar(UiSupport.button("Declaração de palestrante…", this::issueSpeakerDeclaration)), BorderLayout.SOUTH);
        panel.setBorder(javax.swing.BorderFactory.createTitledBorder("Programação"));
        return panel;
    }

    @Override
    public void onEventsLoaded(List<EventView> events) {
        loading = true;
        try {
            Combos.fillEvents(eventCombo, events);
        } finally {
            loading = false;
        }
        reloadList();
    }

    private EventView selectedEvent() {
        return (EventView) eventCombo.getSelectedItem();
    }

    private void clearFilters() {
        for (JTextField f : new JTextField[]{fDate, fFrom, fTo, fTrack, fRoom, fSearch}) f.setText("");
        fType.setSelectedIndex(0);
        reloadList();
    }

    private void reloadList() {
        EventView event = selectedEvent();
        listModel.setRowCount(0);
        if (event == null) return;
        int typeIndex = fType.getSelectedIndex();
        ActivityQuery query = new ActivityQuery(fDate.getText(), fFrom.getText(), fTo.getText(), fTrack.getText(),
                typeIndex > 0 ? ACTIVITY_TYPES[typeIndex - 1][0] : null, fRoom.getText(), fSearch.getText());
        ctx.run("Carregando programação", () -> ctx.client().listActivities(event.id(), query), this::showActivities);
    }

    private void showActivities(List<ActivityView> activities) {
        shownActivities = List.copyOf(activities);
        listModel.setRowCount(0);
        for (ActivityView a : activities) {
            String slots = a.requiresRegistration() ? a.currentEnrollments() + "/" + a.maxCapacity() : "sem controle";
            String speakers = a.speakers().stream().map(SpeakerView::name).collect(Collectors.joining(", "));
            listModel.addRow(new Object[]{a.id(), a.title(), a.typeName(), UiSupport.dateTime(a.period().start()),
                    UiSupport.dateTime(a.period().end()), a.location().room(), a.location().spaceOrTrack(), slots,
                    a.attendancePolicy().policyName(), speakers});
        }
    }

    /** Gera a declaração em PDF da pessoa vinculada à atividade selecionada, com o papel exercido (RF-35). */
    private void issueSpeakerDeclaration() {
        int viewRow = listTable.getSelectedRow();
        if (viewRow < 0) {
            ctx.warn("Selecione uma atividade na lista.");
            return;
        }
        ActivityView activity = shownActivities.get(listTable.convertRowIndexToModel(viewRow));
        List<SpeakerView> people = activity.speakers();
        if (people.isEmpty()) {
            ctx.warn("Esta atividade não tem palestrantes ou apresentadores cadastrados.");
            return;
        }
        SpeakerView chosen = people.get(0);
        if (people.size() > 1) {
            String[] labels = people.stream().map(p -> p.name() + " (" + p.roleInActivity() + ")").toArray(String[]::new);
            Object answer = javax.swing.JOptionPane.showInputDialog(ctx.frame(), "Para quem emitir a declaração?",
                    "Declaração de palestrante", javax.swing.JOptionPane.QUESTION_MESSAGE, null, labels, labels[0]);
            if (answer == null) return;
            chosen = people.get(java.util.Arrays.asList(labels).indexOf(answer));
        }
        SpeakerView person = chosen;
        ctx.run("Gerando declaração", () -> ctx.client().speakerDeclarationPdf(activity.id(), person.id()),
                bytes -> UiSupport.saveFile(ctx, "declaracao_" + person.name().replaceAll("[^A-Za-z0-9]+", "_") + ".pdf", bytes));
    }

    private void addActivity() {
        EventView event = selectedEvent();
        if (event == null) {
            ctx.warn("Selecione um evento.");
            return;
        }
        if (speakersTable.isEditing()) speakersTable.getCellEditor().stopCellEditing();
        String error = validateForm();
        if (error != null) {
            ctx.warn(error);
            return;
        }
        List<SpeakerForm> speakers = new ArrayList<>();
        for (int i = 0; i < speakersModel.getRowCount(); i++) {
            String name = String.valueOf(speakersModel.getValueAt(i, 0)).trim();
            if (name.isEmpty()) continue;
            speakers.add(new SpeakerForm(name, String.valueOf(speakersModel.getValueAt(i, 1)).trim(), ""));
        }
        ActivityForm form = new ActivityForm(title.getText().trim(), description.getText().trim(),
                start.getText().trim(), end.getText().trim(), room.getText().trim(), track.getText().trim(),
                Integer.parseInt(capacity.getText().trim()), ACTIVITY_TYPES[type.getSelectedIndex()][0],
                ATTENDANCE_POLICIES[policy.getSelectedIndex()][0], requiresRegistration.isSelected(), speakers);
        ctx.run("Adicionando atividade", () -> ctx.client().addActivity(event.id(), form), created -> {
            title.setText("");
            description.setText("");
            ctx.refreshAll();
        });
    }

    private String validateForm() {
        if (title.getText().isBlank()) return "Informe o título da atividade.";
        if (room.getText().isBlank()) return "Informe a sala ou local da atividade.";
        if (start.getText().isBlank() || end.getText().isBlank()) return "Informe o início e o fim da atividade.";
        try {
            if (Integer.parseInt(capacity.getText().trim()) < 1) return "A capacidade deve ser de pelo menos 1.";
        } catch (NumberFormatException e) {
            return "A capacidade deve ser um número inteiro.";
        }
        return null;
    }
}
