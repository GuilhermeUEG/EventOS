package com.eventos.adapters.input.desktop;

import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;

/** Utilitários de layout, tabelas e formatação compartilhados pelas telas. */
final class UiSupport {
    static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private UiSupport() {
    }

    /** ISO-8601 -> dd/MM/yyyy HH:mm; devolve o texto original se não for possível interpretar. */
    static String dateTime(String iso) {
        if (iso == null || iso.isBlank()) return "-";
        try {
            return LocalDateTime.parse(iso).format(DISPLAY);
        } catch (Exception e) {
            return iso;
        }
    }

    static String attendanceStatus(String status) {
        if (status == null) return "-";
        return switch (status) {
            case "PRESENT" -> "Presente";
            case "PARTIAL" -> "Presença parcial";
            case "ABSENT" -> "Ausente";
            default -> status;
        };
    }

    static String recordOrigin(String recordedBy) {
        if (recordedBy == null) return "-";
        if ("QR_SCAN".equals(recordedBy)) return "QR Code";
        if (recordedBy.startsWith("MANUAL_BY_USER_")) return "Manual (usuário " + recordedBy.substring("MANUAL_BY_USER_".length()) + ")";
        return recordedBy;
    }

    static DefaultTableModel readOnlyModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    /** Pergunta onde salvar e grava o conteúdo (relatórios, declarações). */
    static void saveFile(AppContext ctx, String suggestedName, byte[] content) {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(suggestedName));
        if (chooser.showSaveDialog(ctx.frame()) != JFileChooser.APPROVE_OPTION) return;
        File target = chooser.getSelectedFile();
        if (target.exists() && !ctx.confirm("O arquivo " + target.getName() + " já existe. Deseja substituí-lo?")) return;
        try {
            Files.write(target.toPath(), content);
            ctx.info("Arquivo salvo em:\n" + target.getAbsolutePath());
        } catch (IOException e) {
            ctx.showError(new IllegalStateException("Não foi possível salvar o arquivo: " + e.getMessage(), e));
        }
    }

    static JTable table(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.setRowHeight(22);
        table.getTableHeader().setReorderingAllowed(false);
        return table;
    }

    static JButton button(String text, Runnable action) {
        JButton button = new JButton(text);
        button.addActionListener(e -> action.run());
        return button;
    }

    static JPanel toolbar(Component... components) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        for (Component c : components) panel.add(c);
        return panel;
    }

    static <T> Consumer<T> ignore() {
        return value -> { };
    }

    /** Formulário de duas colunas (rótulo + campo) em GridBagLayout. */
    static final class Form extends JPanel {
        private int row;

        Form() {
            super(new GridBagLayout());
        }

        Form add(String label, JComponent field) {
            GridBagConstraints left = new GridBagConstraints();
            left.gridx = 0;
            left.gridy = row;
            left.anchor = GridBagConstraints.NORTHWEST;
            left.insets = new Insets(4, 6, 4, 6);
            add(new JLabel(label), left);

            GridBagConstraints right = new GridBagConstraints();
            right.gridx = 1;
            right.gridy = row;
            right.weightx = 1.0;
            right.fill = GridBagConstraints.HORIZONTAL;
            right.insets = new Insets(4, 6, 4, 6);
            add(field, right);
            row++;
            return this;
        }

        Form addWide(JComponent component) {
            GridBagConstraints c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row++;
            c.gridwidth = 2;
            c.weightx = 1.0;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(6, 6, 6, 6);
            add(component, c);
            return this;
        }
    }
}
