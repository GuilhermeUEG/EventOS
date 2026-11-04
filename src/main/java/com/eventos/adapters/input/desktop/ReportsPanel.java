package com.eventos.adapters.input.desktop;

import com.eventos.adapters.input.desktop.DesktopModels.ActivitySummary;
import com.eventos.adapters.input.desktop.DesktopModels.AttendanceReportView;
import com.eventos.adapters.input.desktop.DesktopModels.EligibilityView;
import com.eventos.adapters.input.desktop.DesktopModels.EnrolledParticipant;
import com.eventos.adapters.input.desktop.DesktopModels.EnrolledReportView;
import com.eventos.adapters.input.desktop.DesktopModels.EventView;
import com.eventos.adapters.input.desktop.DesktopModels.ParticipantAttendanceRow;
import java.awt.BorderLayout;
import java.awt.Font;
import java.util.List;
import java.util.concurrent.Callable;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;

/**
 * Relatórios operacionais (RF-29 a RF-31): pré-visualização, exportação em CSV e PDF salva em
 * arquivo, e consulta de elegibilidade de certificado de um participante (RF-32, CA-08).
 */
final class ReportsPanel extends JPanel implements EventAware {
    private final AppContext ctx;
    private final JComboBox<EventView> eventCombo = new JComboBox<>();
    private final JComboBox<EnrolledParticipant> participantCombo = new JComboBox<>();
    private final JTextArea preview = new JTextArea();
    private boolean loading;

    ReportsPanel(AppContext ctx) {
        super(new BorderLayout(8, 8));
        this.ctx = ctx;
        setBorder(new EmptyBorder(10, 10, 10, 10));
        preview.setEditable(false);
        preview.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        eventCombo.addActionListener(e -> {
            if (!loading) loadEnrolledPreview();
        });

        JPanel top = new JPanel(new java.awt.GridLayout(0, 1));
        top.add(UiSupport.toolbar(new JLabel("Evento:"), eventCombo,
                UiSupport.button("Ver inscritos", this::loadEnrolledPreview),
                UiSupport.button("Ver frequência", this::loadAttendancePreview)));
        top.add(UiSupport.toolbar(new JLabel("Exportar:"),
                UiSupport.button("Inscritos (CSV)", () -> export("inscritos", ".csv", c -> c.enrolledCsv(eventId()))),
                UiSupport.button("Inscritos (PDF)", () -> export("inscritos", ".pdf", c -> c.enrolledPdf(eventId()))),
                UiSupport.button("Frequência (CSV)", () -> export("frequencia", ".csv", c -> c.attendanceCsv(eventId()))),
                UiSupport.button("Frequência (PDF)", () -> export("frequencia", ".pdf", c -> c.attendancePdf(eventId())))));
        top.add(UiSupport.toolbar(new JLabel("Certificado — participante:"), participantCombo,
                UiSupport.button("Consultar elegibilidade", this::checkEligibility)));
        add(top, BorderLayout.NORTH);
        add(new JScrollPane(preview), BorderLayout.CENTER);
    }

    @FunctionalInterface
    private interface Download {
        byte[] get(EventOsApiClient client);
    }

    @Override
    public void onEventsLoaded(List<EventView> events) {
        loading = true;
        try {
            Combos.fillEvents(eventCombo, events);
        } finally {
            loading = false;
        }
        loadEnrolledPreview();
    }

    private Long eventId() {
        EventView event = (EventView) eventCombo.getSelectedItem();
        return event == null ? null : event.id();
    }

    private boolean requireEvent() {
        if (eventCombo.getSelectedItem() == null) {
            ctx.warn("Selecione um evento.");
            return false;
        }
        return true;
    }

    private void loadEnrolledPreview() {
        Long eventId = eventId();
        if (eventId == null) {
            preview.setText("");
            return;
        }
        ctx.run("Carregando relatório de inscritos", () -> ctx.client().enrolledReport(eventId), this::showEnrolled);
    }

    private void showEnrolled(EnrolledReportView r) {
        participantCombo.removeAllItems();
        StringBuilder sb = new StringBuilder();
        sb.append("RELATÓRIO DE INSCRITOS — ").append(r.eventTitle()).append('\n');
        sb.append("Confirmadas: ").append(r.totalEnrolled()).append("  |  Canceladas: ").append(r.totalCancelled())
                .append("  |  Capacidade: ").append(r.eventCapacity()).append("\n\n");
        sb.append(String.format("%-4s %-28s %-34s %-12s %-17s %s%n", "ID", "Nome", "E-mail", "Situação", "Inscrição", "Atividades"));
        for (EnrolledParticipant p : r.participants()) {
            sb.append(String.format("%-4s %-28s %-34s %-12s %-17s %d%n", p.userId(), cut(p.name(), 28), cut(p.email(), 34),
                    p.status(), UiSupport.dateTime(p.registrationDate()), p.selectedActivitiesCount()));
            participantCombo.addItem(p);
        }
        preview.setText(sb.toString());
        preview.setCaretPosition(0);
    }

    private void loadAttendancePreview() {
        if (!requireEvent()) return;
        Long eventId = eventId();
        ctx.run("Carregando relatório de frequência", () -> ctx.client().attendanceReport(eventId), this::showAttendance);
    }

    private void showAttendance(AttendanceReportView r) {
        StringBuilder sb = new StringBuilder();
        sb.append("RELATÓRIO DE FREQUÊNCIA E PARTICIPAÇÃO — ").append(r.eventTitle()).append('\n');
        sb.append(String.format("Participantes: %d  |  Taxa geral de presença: %.1f%%%n%n", r.totalParticipants(), r.overallAttendanceRate()));
        sb.append("Totais por atividade\n");
        sb.append(String.format("%-4s %-44s %-12s %9s %8s %8s%n", "ID", "Atividade", "Sala", "Presentes", "Parciais", "Ausentes"));
        for (ActivitySummary s : r.activitySummaries()) {
            sb.append(String.format("%-4s %-44s %-12s %9d %8d %8d%n", s.activityId(), cut(s.activityTitle(), 44),
                    cut(s.room(), 12), s.totalPresent(), s.totalPartial(), s.totalAbsent()));
        }
        sb.append("\nSituação por participante e atividade\n");
        sb.append(String.format("%-26s %-40s %-17s %5s  %s%n", "Participante", "Atividade", "Situação", "Regs.", "Última marcação"));
        for (ParticipantAttendanceRow row : r.rows()) {
            sb.append(String.format("%-26s %-40s %-17s %5d  %s (%s)%n", cut(row.userName(), 26), cut(row.activityTitle(), 40),
                    UiSupport.attendanceStatus(row.status()), row.recordsCount(), UiSupport.dateTime(row.lastRecordTime()),
                    UiSupport.recordOrigin(row.lastRecordedBy())));
        }
        preview.setText(sb.toString());
        preview.setCaretPosition(0);
    }

    private void export(String name, String extension, Download download) {
        if (!requireEvent()) return;
        Long eventId = eventId();
        Callable<byte[]> work = () -> download.get(ctx.client());
        ctx.run("Gerando arquivo", work, bytes -> UiSupport.saveFile(ctx, "relatorio_" + name + "_" + eventId + extension, bytes));
    }

    private void checkEligibility() {
        if (!requireEvent()) return;
        EnrolledParticipant participant = (EnrolledParticipant) participantCombo.getSelectedItem();
        if (participant == null) {
            ctx.warn("Selecione um participante inscrito (carregue o relatório de inscritos).");
            return;
        }
        Long eventId = eventId();
        ctx.run("Consultando elegibilidade", () -> ctx.client().certificateEligibility(eventId, participant.userId()),
                e -> showEligibility(participant, e));
    }

    private void showEligibility(EnrolledParticipant participant, EligibilityView e) {
        String text = participant.name() + "\n\n"
                + (e.eligible() ? "ELEGÍVEL ao certificado." : "NÃO elegível ao certificado.") + "\n"
                + e.justification() + "\n\n"
                + "Critério: " + e.policyDescription() + "\n"
                + String.format("Atividades com presença: %d de %d  |  Horas: %.1f de %.1f%s",
                e.activitiesAttended(), e.totalActivities(), e.hoursAttended(), e.totalHours(),
                e.alreadyIssued() ? "\nCertificado já emitido." : "");
        ctx.info(text);
    }

    private static String cut(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
