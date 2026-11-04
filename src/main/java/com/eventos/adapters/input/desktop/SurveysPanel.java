package com.eventos.adapters.input.desktop;

import com.eventos.adapters.input.desktop.DesktopModels.ActivityView;
import com.eventos.adapters.input.desktop.DesktopModels.EventView;
import com.eventos.adapters.input.desktop.DesktopModels.QuestionMetric;
import com.eventos.adapters.input.desktop.DesktopModels.SurveyForm;
import com.eventos.adapters.input.desktop.DesktopModels.SurveyResultsView;
import java.awt.BorderLayout;
import java.awt.Font;
import java.util.List;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;

/** Avaliações: criação do questionário da atividade e consulta dos resultados consolidados (RF-24 a RF-28). */
final class SurveysPanel extends JPanel implements EventAware {
    private final AppContext ctx;
    private final JComboBox<EventView> eventCombo = new JComboBox<>();
    private final JComboBox<ActivityView> activityCombo = new JComboBox<>();
    private final JTextArea results = new JTextArea();
    private boolean loading;

    SurveysPanel(AppContext ctx) {
        super(new BorderLayout(8, 8));
        this.ctx = ctx;
        setBorder(new EmptyBorder(10, 10, 10, 10));
        results.setEditable(false);
        results.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        eventCombo.addActionListener(e -> {
            if (!loading) loadActivities();
        });
        activityCombo.addActionListener(e -> {
            if (!loading) loadResults();
        });
        add(UiSupport.toolbar(new JLabel("Evento:"), eventCombo, new JLabel("Atividade:"), activityCombo,
                UiSupport.button("Criar questionário…", this::createSurvey),
                UiSupport.button("Atualizar resultados", this::loadResults)), BorderLayout.NORTH);
        add(new JScrollPane(results), BorderLayout.CENTER);
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

    private void loadActivities() {
        EventView event = (EventView) eventCombo.getSelectedItem();
        if (event == null) {
            results.setText("");
            return;
        }
        ctx.run("Carregando atividades", () -> ctx.client().listActivities(event.id()), activities -> {
            loading = true;
            try {
                Combos.fillActivities(activityCombo, activities);
            } finally {
                loading = false;
            }
            loadResults();
        });
    }

    private void createSurvey() {
        ActivityView activity = (ActivityView) activityCombo.getSelectedItem();
        if (activity == null) {
            ctx.warn("Selecione uma atividade.");
            return;
        }
        SurveyForm form = SurveyBuilderDialog.prompt(ctx.frame(), activity.title());
        if (form == null) return;
        ctx.run("Criando questionário", () -> ctx.client().createSurvey(activity.id(), form), created -> {
            ctx.info("Questionário criado. " + created.identificationNotice());
            loadResults();
        });
    }

    private void loadResults() {
        ActivityView activity = (ActivityView) activityCombo.getSelectedItem();
        if (activity == null) {
            results.setText("");
            return;
        }
        ctx.run("Carregando resultados", () -> ctx.client().surveyResults(activity.id()), this::show);
    }

    private void show(SurveyResultsView r) {
        StringBuilder sb = new StringBuilder();
        sb.append("Atividade: ").append(r.activityTitle()).append('\n');
        sb.append("Questionário: ").append(r.surveyTitle()).append('\n');
        if (r.identificationNotice() != null && !r.identificationNotice().isBlank()) {
            sb.append("Política: ").append(r.identificationNotice()).append('\n');
        }
        sb.append("Total de respostas: ").append(r.totalResponses());
        if (r.averageRating() > 0) sb.append(String.format("  |  Média geral (escalas): %.2f / 5", r.averageRating()));
        sb.append("\n\n");
        for (QuestionMetric m : r.questionMetrics()) {
            sb.append("• ").append(m.questionText()).append("  (").append(m.answeredCount()).append(" resposta(s))\n");
            if (m.averageScore() > 0) sb.append(String.format("    Média: %.2f%n", m.averageScore()));
            if (m.optionDistribution() != null) {
                m.optionDistribution().forEach((option, count) ->
                        sb.append("    ").append(option).append(": ").append(count).append('\n'));
            }
        }
        if (!r.textComments().isEmpty()) {
            sb.append("\nComentários:\n");
            r.textComments().forEach(c -> sb.append("  - ").append(c).append('\n'));
        }
        if (r.totalResponses() == 0) {
            sb.append("Ainda não há respostas (ou o questionário não foi criado).\n");
        }
        results.setText(sb.toString());
        results.setCaretPosition(0);
    }
}
