package com.eventos.adapters.input.desktop;

import com.eventos.adapters.input.desktop.DesktopModels.QuestionForm;
import com.eventos.adapters.input.desktop.DesktopModels.SurveyForm;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;

/**
 * Construtor de questionário (RF-24, RF-25): título, política de anonimato (informada ao participante
 * antes do envio) e perguntas de texto, escolha única ou escala numérica de 1 a 5.
 */
final class SurveyBuilderDialog extends JDialog {
    private static final String[][] TYPES = {
            {"TEXT", "Resposta textual"}, {"SINGLE_CHOICE", "Escolha única"}, {"RATING_SCALE", "Escala numérica (1 a 5)"}};

    private final JTextField title = new JTextField(32);
    private final JCheckBox anonymous = new JCheckBox("Respostas anônimas (o organizador não verá quem respondeu)");
    private final DefaultListModel<QuestionForm> questions = new DefaultListModel<>();
    private final JList<QuestionForm> questionList = new JList<>(questions);
    private final JTextField questionText = new JTextField(32);
    private final JComboBox<String> questionType = new JComboBox<>();
    private final JTextArea options = new JTextArea(3, 32);
    private final JCheckBox required = new JCheckBox("Resposta obrigatória", true);
    private SurveyForm result;

    private SurveyBuilderDialog(Window owner, String activityTitle) {
        super(owner, "Questionário — " + activityTitle, ModalityType.APPLICATION_MODAL);
        for (String[] t : TYPES) questionType.addItem(t[1]);
        questionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        options.setEnabled(false);
        questionType.addActionListener(e -> options.setEnabled(questionType.getSelectedIndex() == 1));

        UiSupport.Form head = new UiSupport.Form();
        head.add("Título:", title);
        head.addWide(anonymous);

        UiSupport.Form editor = new UiSupport.Form();
        editor.add("Pergunta:", questionText);
        editor.add("Tipo:", questionType);
        editor.add("Opções (uma por linha, mínimo 2):", new JScrollPane(options));
        editor.addWide(required);
        editor.addWide(UiSupport.button("Adicionar pergunta", this::addQuestion));
        editor.setBorder(BorderFactory.createTitledBorder("Nova pergunta"));

        JScrollPane listScroll = new JScrollPane(questionList);
        listScroll.setPreferredSize(new Dimension(560, 130));
        listScroll.setBorder(BorderFactory.createTitledBorder("Perguntas do questionário"));
        JPanel listPanel = new JPanel(new BorderLayout(4, 4));
        listPanel.add(listScroll, BorderLayout.CENTER);
        listPanel.add(UiSupport.button("Remover pergunta selecionada", () -> {
            int index = questionList.getSelectedIndex();
            if (index >= 0) questions.remove(index);
        }), BorderLayout.SOUTH);

        JPanel center = new JPanel(new BorderLayout(6, 6));
        center.add(editor, BorderLayout.NORTH);
        center.add(listPanel, BorderLayout.CENTER);

        JButton save = UiSupport.button("Criar questionário", this::save);
        JButton cancel = UiSupport.button("Cancelar", this::dispose);
        JPanel buttons = UiSupport.toolbar(save, cancel);

        JPanel content = new JPanel(new BorderLayout(6, 6));
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        content.add(head, BorderLayout.NORTH);
        content.add(center, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        pack();
        setLocationRelativeTo(owner);
    }

    /** Abre o construtor; devolve o questionário montado ou {@code null} se cancelado. */
    static SurveyForm prompt(Window owner, String activityTitle) {
        SurveyBuilderDialog dialog = new SurveyBuilderDialog(owner, activityTitle);
        dialog.setVisible(true);
        return dialog.result;
    }

    private void addQuestion() {
        String text = questionText.getText().trim();
        if (text.isEmpty()) {
            warn("Informe o enunciado da pergunta.");
            return;
        }
        String type = TYPES[questionType.getSelectedIndex()][0];
        List<String> optionList = new ArrayList<>();
        if ("SINGLE_CHOICE".equals(type)) {
            Arrays.stream(options.getText().split("\\R")).map(String::trim).filter(s -> !s.isEmpty()).forEach(optionList::add);
            if (optionList.stream().distinct().count() < 2) {
                warn("Perguntas de escolha única exigem ao menos duas opções distintas.");
                return;
            }
        }
        questions.addElement(new QuestionForm(text, type, optionList, required.isSelected()));
        questionText.setText("");
        options.setText("");
    }

    private void save() {
        if (title.getText().isBlank()) {
            warn("Informe o título do questionário.");
            return;
        }
        if (questions.isEmpty()) {
            warn("Adicione ao menos uma pergunta.");
            return;
        }
        result = new SurveyForm(title.getText().trim(), anonymous.isSelected(), java.util.Collections.list(questions.elements()));
        dispose();
    }

    private void warn(String message) {
        JOptionPane.showMessageDialog(this, message, "Atenção", JOptionPane.WARNING_MESSAGE);
    }
}
