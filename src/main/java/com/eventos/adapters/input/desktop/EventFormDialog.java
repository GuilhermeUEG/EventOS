package com.eventos.adapters.input.desktop;

import com.eventos.adapters.input.desktop.DesktopModels.EventForm;
import com.eventos.adapters.input.desktop.DesktopModels.EventView;
import com.eventos.adapters.input.desktop.DesktopModels.PolicyForm;
import java.awt.Component;
import java.time.LocalDateTime;
import java.time.ZoneId;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/**
 * Formulário de criação/edição de evento. A política de certificado só é pedida na criação; depois
 * ela é alterada pelo botão próprio (que oferece as atividades já cadastradas).
 */
final class EventFormDialog {
    private EventFormDialog() {
    }

    /** Devolve o formulário preenchido ou {@code null} se o usuário cancelar. */
    static EventForm prompt(Component parent, EventView existing) {
        JTextField title = new JTextField(30);
        JTextArea description = new JTextArea(3, 30);
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        JTextField start = new JTextField(16);
        JTextField end = new JTextField(16);
        JTextField capacity = new JTextField("500", 6);
        JTextField deadline = new JTextField(16);
        JTextField zone = new JTextField("America/Sao_Paulo", 20);
        JCheckBox selectionEnabled = new JCheckBox("Participante escolhe atividades na inscrição", true);
        JCheckBox selectionRequired = new JCheckBox("Escolher ao menos uma atividade é obrigatório", false);
        CertificatePolicyEditor policyEditor = new CertificatePolicyEditor(null);

        if (existing != null) {
            title.setText(existing.title());
            description.setText(existing.description());
            start.setText(UiSupport.dateTime(existing.period().start()));
            end.setText(UiSupport.dateTime(existing.period().end()));
            capacity.setText(String.valueOf(existing.maxCapacity()));
            deadline.setText(UiSupport.dateTime(existing.registrationDeadline()));
            zone.setText(existing.timeZone());
            selectionEnabled.setSelected(existing.activitySelectionEnabled());
            selectionRequired.setSelected(existing.activitySelectionRequired());
        } else {
            LocalDateTime base = LocalDateTime.now().plusDays(7).withHour(8).withMinute(0).withSecond(0).withNano(0);
            start.setText(base.format(UiSupport.DISPLAY));
            end.setText(base.plusDays(1).withHour(18).format(UiSupport.DISPLAY));
            deadline.setText(base.format(UiSupport.DISPLAY));
        }

        UiSupport.Form form = new UiSupport.Form();
        form.add("Título:", title);
        form.add("Descrição:", new JScrollPane(description));
        form.add("Início (dd/MM/aaaa HH:mm):", start);
        form.add("Fim (dd/MM/aaaa HH:mm):", end);
        form.add("Capacidade do evento:", capacity);
        form.add("Prazo de inscrição:", deadline);
        form.add("Fuso horário:", zone);
        form.addWide(selectionEnabled);
        form.addWide(selectionRequired);
        if (existing == null) {
            form.addWide((JComponent) policyEditor);
        }

        while (true) {
            int option = JOptionPane.showConfirmDialog(parent, form,
                    existing == null ? "Novo evento (rascunho)" : "Editar evento",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (option != JOptionPane.OK_OPTION) return null;

            String error = validate(title, start, end, capacity, zone);
            PolicyForm policy = existing == null ? policyEditor.toForm() : null;
            if (error == null && existing == null && policy == null) error = policyEditor.missingSelectionMessage();
            if (error != null) {
                JOptionPane.showMessageDialog(parent, error, "Dados inválidos", JOptionPane.WARNING_MESSAGE);
                continue;
            }
            return new EventForm(title.getText().trim(), description.getText().trim(), start.getText().trim(),
                    end.getText().trim(), Integer.parseInt(capacity.getText().trim()), deadline.getText().trim(),
                    zone.getText().trim(), selectionEnabled.isSelected(), selectionRequired.isSelected(), policy);
        }
    }

    private static String validate(JTextField title, JTextField start, JTextField end, JTextField capacity, JTextField zone) {
        if (title.getText().isBlank()) return "Informe o título do evento.";
        if (start.getText().isBlank() || end.getText().isBlank()) return "Informe o início e o fim do evento.";
        try {
            if (Integer.parseInt(capacity.getText().trim()) < 1) return "A capacidade deve ser de pelo menos 1.";
        } catch (NumberFormatException e) {
            return "A capacidade deve ser um número inteiro.";
        }
        try {
            ZoneId.of(zone.getText().trim());
        } catch (Exception e) {
            return "Fuso horário inválido (exemplo: America/Sao_Paulo).";
        }
        return null;
    }
}
