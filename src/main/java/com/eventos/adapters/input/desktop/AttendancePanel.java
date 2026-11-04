package com.eventos.adapters.input.desktop;

import com.eventos.adapters.input.desktop.DesktopModels.ActivityView;
import com.eventos.adapters.input.desktop.DesktopModels.AttendanceOverviewRow;
import com.eventos.adapters.input.desktop.DesktopModels.AttendanceRecordView;
import com.eventos.adapters.input.desktop.DesktopModels.EventView;
import com.eventos.adapters.input.desktop.DesktopModels.QrCodeView;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.datatransfer.StringSelection;
import java.awt.Toolkit;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

/**
 * Frequência (RF-19 a RF-23): QR Code da atividade (imagem + conteúdo copiável), situação calculada
 * de cada participante, lançamento manual, correção por revogação e histórico auditável.
 */
final class AttendancePanel extends JPanel implements EventAware {
    private static final String[][] MANUAL_TYPES = {
            {"MANUAL_ENTRY", "Presença confirmada manualmente"}, {"CHECK_IN", "Entrada (check-in)"},
            {"CHECK_OUT", "Saída (check-out)"}};

    private final AppContext ctx;
    private final JComboBox<EventView> eventCombo = new JComboBox<>();
    private final JComboBox<ActivityView> activityCombo = new JComboBox<>();
    private final JLabel qrImage = new JLabel("Gere o QR Code da atividade", SwingConstants.CENTER);
    private final JTextField qrToken = new JTextField(22);
    private final JLabel qrValidity = new JLabel(" ");
    private final DefaultTableModel overviewModel = UiSupport.readOnlyModel(
            "ID", "Participante", "E-mail", "Situação", "Última marcação", "Origem");
    private final JTable overviewTable = UiSupport.table(overviewModel);
    private List<AttendanceOverviewRow> overview = new ArrayList<>();
    private boolean loading;

    AttendancePanel(AppContext ctx) {
        super(new BorderLayout(8, 8));
        this.ctx = ctx;
        setBorder(new EmptyBorder(10, 10, 10, 10));

        eventCombo.addActionListener(e -> {
            if (!loading) loadActivities();
        });
        activityCombo.addActionListener(e -> {
            if (!loading) {
                clearQr();
                loadOverview();
            }
        });
        add(UiSupport.toolbar(new JLabel("Evento:"), eventCombo, new JLabel("Atividade:"), activityCombo,
                UiSupport.button("Atualizar", this::loadOverview)), BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildQrPanel(), buildOverviewPanel());
        split.setDividerLocation(330);
        add(split, BorderLayout.CENTER);

        add(UiSupport.toolbar(
                UiSupport.button("Lançar presença manual…", this::recordManual),
                UiSupport.button("Revogar presença…", this::revoke),
                UiSupport.button("Histórico de registros…", this::showHistory)), BorderLayout.SOUTH);
    }

    private JPanel buildQrPanel() {
        qrImage.setPreferredSize(new Dimension(300, 300));
        qrImage.setBorder(javax.swing.BorderFactory.createLineBorder(java.awt.Color.LIGHT_GRAY));
        qrToken.setEditable(false);
        JPanel tokenRow = new JPanel(new BorderLayout(4, 4));
        tokenRow.add(qrToken, BorderLayout.CENTER);
        tokenRow.add(UiSupport.button("Copiar", this::copyToken), BorderLayout.EAST);

        JPanel south = new JPanel(new BorderLayout(4, 4));
        south.add(UiSupport.button("Gerar QR Code", this::generateQr), BorderLayout.NORTH);
        south.add(tokenRow, BorderLayout.CENTER);
        south.add(qrValidity, BorderLayout.SOUTH);

        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(javax.swing.BorderFactory.createTitledBorder("QR Code da atividade"));
        panel.add(qrImage, BorderLayout.CENTER);
        panel.add(south, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildOverviewPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(javax.swing.BorderFactory.createTitledBorder("Participantes inscritos na atividade"));
        panel.add(new JScrollPane(overviewTable), BorderLayout.CENTER);
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
        loadActivities();
    }

    private ActivityView selectedActivity() {
        ActivityView activity = (ActivityView) activityCombo.getSelectedItem();
        if (activity == null) ctx.warn("Selecione uma atividade.");
        return activity;
    }

    private void loadActivities() {
        EventView event = (EventView) eventCombo.getSelectedItem();
        if (event == null) {
            loading = true;
            activityCombo.removeAllItems();
            loading = false;
            overviewModel.setRowCount(0);
            return;
        }
        ctx.run("Carregando atividades", () -> ctx.client().listActivities(event.id()), activities -> {
            loading = true;
            try {
                Combos.fillActivities(activityCombo, activities);
            } finally {
                loading = false;
            }
            loadOverview();
        });
    }

    private void loadOverview() {
        ActivityView activity = (ActivityView) activityCombo.getSelectedItem();
        overviewModel.setRowCount(0);
        overview = new ArrayList<>();
        if (activity == null) return;
        ctx.run("Carregando frequência", () -> ctx.client().attendanceOverview(activity.id()), rows -> {
            overview = rows;
            overviewModel.setRowCount(0);
            for (AttendanceOverviewRow r : rows) {
                overviewModel.addRow(new Object[]{r.userId(), r.userName(), r.userEmail(),
                        UiSupport.attendanceStatus(r.status()), UiSupport.dateTime(r.lastRecordTime()),
                        UiSupport.recordOrigin(r.recordedBy())});
            }
        });
    }

    private void clearQr() {
        qrImage.setIcon(null);
        qrImage.setText("Gere o QR Code da atividade");
        qrToken.setText("");
        qrValidity.setText(" ");
    }

    private void generateQr() {
        ActivityView activity = selectedActivity();
        if (activity == null) return;
        ctx.run("Gerando QR Code", () -> ctx.client().generateQrCode(activity.id()), this::showQr);
    }

    private void showQr(QrCodeView qr) {
        try {
            qrImage.setIcon(new ImageIcon(ImageIO.read(new ByteArrayInputStream(qr.imageBytes()))));
            qrImage.setText(null);
        } catch (Exception e) {
            qrImage.setIcon(null);
            qrImage.setText("Não foi possível exibir a imagem; use o código ao lado.");
        }
        qrToken.setText(qr.qrToken());
        qrValidity.setText("Válido por " + (qr.validForSeconds() / 60) + " minutos. Não contém dados pessoais.");
    }

    private void copyToken() {
        if (qrToken.getText().isBlank()) return;
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(qrToken.getText()), null);
        ctx.statusLabel().setText("Conteúdo do QR Code copiado.");
    }

    private AttendanceOverviewRow selectedParticipant() {
        int row = overviewTable.getSelectedRow();
        if (row < 0 || row >= overview.size()) {
            ctx.warn("Selecione um participante na tabela.");
            return null;
        }
        return overview.get(overviewTable.convertRowIndexToModel(row));
    }

    private void recordManual() {
        ActivityView activity = selectedActivity();
        AttendanceOverviewRow participant = activity == null ? null : selectedParticipant();
        if (participant == null) return;

        JComboBox<String> typeCombo = new JComboBox<>();
        for (String[] t : MANUAL_TYPES) typeCombo.addItem(t[1]);
        JTextArea reason = new JTextArea(3, 30);
        reason.setLineWrap(true);
        UiSupport.Form form = new UiSupport.Form();
        form.add("Participante:", new JLabel(participant.userName()));
        form.add("Registro:", typeCombo);
        form.add("Justificativa (obrigatória):", new JScrollPane(reason));
        if (JOptionPane.showConfirmDialog(ctx.frame(), form, "Lançamento manual de presença",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        if (reason.getText().isBlank()) {
            ctx.warn("Informe a justificativa: ela fica registrada para auditoria.");
            return;
        }
        String type = MANUAL_TYPES[typeCombo.getSelectedIndex()][0];
        ctx.run("Registrando presença", () -> ctx.client().recordManualAttendance(
                activity.id(), participant.userId(), type, reason.getText().trim()), saved -> loadOverview());
    }

    private void revoke() {
        ActivityView activity = selectedActivity();
        AttendanceOverviewRow participant = activity == null ? null : selectedParticipant();
        if (participant == null) return;
        JTextArea reason = new JTextArea(3, 30);
        reason.setLineWrap(true);
        UiSupport.Form form = new UiSupport.Form();
        form.add("Participante:", new JLabel(participant.userName()));
        form.add("Justificativa da correção (obrigatória):", new JScrollPane(reason));
        if (JOptionPane.showConfirmDialog(ctx.frame(), form, "Revogar presença",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        if (reason.getText().isBlank()) {
            ctx.warn("Informe a justificativa: ela fica registrada para auditoria.");
            return;
        }
        ctx.run("Revogando presença", () -> ctx.client().revokeAttendance(
                activity.id(), participant.userId(), reason.getText().trim()), saved -> {
            ctx.info("Presença revogada. Os registros anteriores permanecem no histórico.");
            loadOverview();
        });
    }

    private void showHistory() {
        ActivityView activity = selectedActivity();
        if (activity == null) return;
        ctx.run("Carregando histórico", () -> ctx.client().attendanceRecords(activity.id()), this::showRecords);
    }

    private void showRecords(List<AttendanceRecordView> records) {
        DefaultTableModel model = UiSupport.readOnlyModel("Data/hora", "Participante (ID)", "Registro", "Origem", "Justificativa");
        for (AttendanceRecordView r : records) {
            model.addRow(new Object[]{UiSupport.dateTime(r.timestamp()), r.userId(), r.typeName(),
                    UiSupport.recordOrigin(r.recordedBy()), r.notes() == null ? "" : r.notes()});
        }
        JScrollPane scroll = new JScrollPane(UiSupport.table(model));
        scroll.setPreferredSize(new Dimension(760, 320));
        JOptionPane.showMessageDialog(ctx.frame(), scroll, "Histórico de registros — revogações incluídas",
                JOptionPane.PLAIN_MESSAGE);
    }
}
