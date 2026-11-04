package com.eventos.adapters.input.desktop;

import com.eventos.adapters.input.desktop.DesktopModels.ActivityView;
import com.eventos.adapters.input.desktop.DesktopModels.CertificatePolicyView;
import com.eventos.adapters.input.desktop.DesktopModels.PolicyForm;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import javax.swing.DefaultListModel;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;

/**
 * Editor da política de elegibilidade de certificado: percentual mínimo, quantidade mínima de atividades
 * ou atividades obrigatórias (esta última só quando o evento já possui atividades).
 */
final class CertificatePolicyEditor extends JPanel {
    private static final String[] TYPES = {"MIN_PERCENTAGE", "MIN_ACTIVITIES", "REQUIRED_ACTIVITIES"};
    private static final String[] LABELS = {"Percentual mínimo de presença", "Quantidade mínima de atividades",
            "Atividades obrigatórias"};

    private final JComboBox<String> typeCombo = new JComboBox<>();
    private final JSpinner percentage = new JSpinner(new SpinnerNumberModel(75.0, 1.0, 100.0, 5.0));
    private final JSpinner minActivities = new JSpinner(new SpinnerNumberModel(1, 1, 500, 1));
    private final DefaultListModel<ActivityView> activitiesModel = new DefaultListModel<>();
    private final JList<ActivityView> activitiesList = new JList<>(activitiesModel);
    private final boolean allowRequired;

    CertificatePolicyEditor(List<ActivityView> eventActivities) {
        super(new BorderLayout(6, 6));
        this.allowRequired = eventActivities != null && !eventActivities.isEmpty();
        int count = allowRequired ? 3 : 2;
        for (int i = 0; i < count; i++) typeCombo.addItem(LABELS[i]);
        if (allowRequired) eventActivities.forEach(activitiesModel::addElement);
        activitiesList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        UiSupport.Form form = new UiSupport.Form();
        form.add("Critério:", typeCombo);
        form.add("Percentual (%):", percentage);
        form.add("Mínimo de atividades:", minActivities);
        add(form, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(activitiesList);
        scroll.setPreferredSize(new Dimension(380, 120));
        scroll.setBorder(javax.swing.BorderFactory.createTitledBorder("Atividades obrigatórias"));
        add(scroll, BorderLayout.CENTER);

        typeCombo.addActionListener(e -> updateEnabled());
        updateEnabled();
    }

    private void updateEnabled() {
        int index = typeCombo.getSelectedIndex();
        percentage.setEnabled(index == 0);
        minActivities.setEnabled(index == 1);
        activitiesList.setEnabled(index == 2);
    }

    /** Pré-seleciona os valores da política atual do evento. */
    void load(CertificatePolicyView policy) {
        if (policy == null || policy.policyKey() == null) return;
        switch (policy.policyKey()) {
            case "MIN_ACTIVITIES", "MANDATORY_COUNT" -> {
                typeCombo.setSelectedIndex(1);
                if (policy.minActivities() != null) minActivities.setValue(policy.minActivities());
            }
            case "REQUIRED_ACTIVITIES" -> {
                if (allowRequired) typeCombo.setSelectedIndex(2);
                List<Integer> indexes = new ArrayList<>();
                for (int i = 0; i < activitiesModel.size(); i++) {
                    if (policy.activityIds() != null && policy.activityIds().contains(activitiesModel.get(i).id())) {
                        indexes.add(i);
                    }
                }
                activitiesList.setSelectedIndices(indexes.stream().mapToInt(Integer::intValue).toArray());
            }
            default -> {
                typeCombo.setSelectedIndex(0);
                if (policy.percentage() != null) percentage.setValue(policy.percentage());
            }
        }
    }

    /** Devolve a política escolhida ou {@code null} (com mensagem em {@code error}) se algo estiver faltando. */
    PolicyForm toForm() {
        return switch (typeCombo.getSelectedIndex()) {
            case 1 -> new PolicyForm(TYPES[1], null, ((Number) minActivities.getValue()).intValue(), null);
            case 2 -> {
                List<Long> ids = activitiesList.getSelectedValuesList().stream().map(ActivityView::id).toList();
                yield ids.isEmpty() ? null : new PolicyForm(TYPES[2], null, null, ids);
            }
            default -> new PolicyForm(TYPES[0], ((Number) percentage.getValue()).doubleValue(), null, null);
        };
    }

    String missingSelectionMessage() {
        return "Selecione ao menos uma atividade obrigatória.";
    }
}
