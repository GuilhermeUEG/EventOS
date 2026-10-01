package com.eventos.adapters.input.desktop;

import com.eventos.application.dtos.AttendanceReportDto;
import com.eventos.application.dtos.AttendanceStatusDto;
import com.eventos.application.dtos.EnrolledReportDto;
import com.eventos.application.dtos.SurveyResultsDto;
import com.eventos.application.ports.input.AttendanceUseCase;
import com.eventos.application.ports.input.AuthUseCase;
import com.eventos.application.ports.input.CertificateUseCase;
import com.eventos.application.ports.input.EventUseCase;
import com.eventos.application.ports.input.RegistrationUseCase;
import com.eventos.application.ports.input.ReportUseCase;
import com.eventos.application.ports.input.SurveyUseCase;
import com.eventos.domain.model.Activity;
import com.eventos.domain.model.ActivityLocation;
import com.eventos.domain.model.ActivityType;
import com.eventos.domain.model.AttendanceType;
import com.eventos.domain.model.Event;
import com.eventos.domain.model.EventStatus;
import com.eventos.domain.model.FeedbackSurvey;
import com.eventos.domain.model.Period;
import com.eventos.domain.model.QuestionType;
import com.eventos.domain.model.Speaker;
import com.eventos.domain.model.SurveyQuestion;
import com.eventos.domain.policies.AttendancePolicyFactory;
import com.eventos.domain.policies.MinimumAttendancePercentagePolicy;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

public class SwingDesktopApp extends JFrame {
    private final EventOsApiClient apiClient;

    private JTextField txtEventTitle;
    private JTextArea txtEventDesc;
    private JTextField txtEventStart;
    private JTextField txtEventEnd;
    private JTextField txtEventCapacity;
    private JCheckBox chkActivitySelectionEnabled;
    private JCheckBox chkActivitySelectionRequired;
    private JTextField txtRegistrationDeadline;
    private JTextField txtEventTimeZone;
    private DefaultListModel<String> eventListModel;
    private JList<String> eventList;
    private List<Event> loadedEvents = new ArrayList<>();

    private JComboBox<String> cbActivityEvents;
    private JTextField txtActTitle;
    private JTextArea txtActDesc;
    private JTextField txtActStart;
    private JTextField txtActEnd;
    private JTextField txtActRoom;
    private JTextField txtActTrack;
    private JTextField txtActCapacity;
    private JComboBox<ActivityType> cbActType;
    private JComboBox<String> cbActAttendancePolicy;
    private JTextField txtSpeakerName;
    private JTextField txtSpeakerRole;
    private DefaultListModel<String> activityListModel;

    private JComboBox<String> cbAttendanceEvents;
    private JComboBox<String> cbAttendanceActivities;
    private JTable tableAttendance;
    private DefaultTableModel modelAttendance;
    private JTextField txtQrTokenDisplay;

    private JComboBox<String> cbSurveyActivities;
    private JTextArea txtSurveyResultsDisplay;

    private JComboBox<String> cbReportEvents;
    private JTextArea txtReportPreview;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public SwingDesktopApp(EventOsApiClient apiClient) {
        this.apiClient = apiClient;
        initUI();
        refreshAllData();
    }

    private void initUI() {
        setTitle("EventOS - Painel Administrativo do Organizador [POO II]");
        setSize(1100, 750);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.BOLD, 13));

        tabbedPane.addTab("Gestao de Eventos", createEventsPanel());
        tabbedPane.addTab("Atividades e Salas", createActivitiesPanel());
        tabbedPane.addTab("Frequencia e QR Code", createAttendancePanel());
        tabbedPane.addTab("Avaliacoes e Metricas", createSurveysPanel());
        tabbedPane.addTab("Relatorios e Certificados", createReportsPanel());

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(tabbedPane, BorderLayout.CENTER);

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(15, 23, 42));
        headerPanel.setBorder(new EmptyBorder(12, 16, 12, 16));
        JLabel titleLabel = new JLabel("EventOS Studio - Modulo de Gestao Administrativa");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        titleLabel.setForeground(Color.WHITE);
        headerPanel.add(titleLabel, BorderLayout.WEST);

        JButton btnGlobalRefresh = new JButton("Atualizar Tudo");
        btnGlobalRefresh.addActionListener(e -> refreshAllData());
        headerPanel.add(btnGlobalRefresh, BorderLayout.EAST);

        getContentPane().add(headerPanel, BorderLayout.NORTH);
    }

    private JPanel createEventsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(420);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(15, 15, 15, 15));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 4, 4, 4);

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        JLabel lbl = new JLabel("Cadastrar Novo Evento");
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
        form.add(lbl, gbc);

        gbc.gridwidth = 1;
        gbc.gridy = 1; gbc.gridx = 0; form.add(new JLabel("Titulo:"), gbc);
        txtEventTitle = new JTextField();
        gbc.gridx = 1; form.add(txtEventTitle, gbc);

        gbc.gridy = 2; gbc.gridx = 0; form.add(new JLabel("Descricao:"), gbc);
        txtEventDesc = new JTextArea(3, 20);
        txtEventDesc.setLineWrap(true);
        gbc.gridx = 1; form.add(new JScrollPane(txtEventDesc), gbc);

        gbc.gridy = 3; gbc.gridx = 0; form.add(new JLabel("Inicio (dd/MM/yyyy HH:mm):"), gbc);
        txtEventStart = new JTextField(LocalDateTime.now().plusDays(1).format(FMT));
        gbc.gridx = 1; form.add(txtEventStart, gbc);

        gbc.gridy = 4; gbc.gridx = 0; form.add(new JLabel("Fim (dd/MM/yyyy HH:mm):"), gbc);
        txtEventEnd = new JTextField(LocalDateTime.now().plusDays(3).format(FMT));
        gbc.gridx = 1; form.add(txtEventEnd, gbc);

        gbc.gridy = 5; gbc.gridx = 0; form.add(new JLabel("Capacidade Maxima:"), gbc);
        txtEventCapacity = new JTextField("300");
        gbc.gridx = 1; form.add(txtEventCapacity, gbc);

        gbc.gridy = 6; gbc.gridx = 0; form.add(new JLabel("Prazo de inscrição:"), gbc);
        txtRegistrationDeadline = new JTextField(LocalDateTime.now().plusDays(1).minusHours(1).format(FMT));
        gbc.gridx = 1; form.add(txtRegistrationDeadline, gbc);

        gbc.gridy = 7; gbc.gridx = 0; form.add(new JLabel("Fuso horário:"), gbc);
        txtEventTimeZone = new JTextField("America/Sao_Paulo");
        gbc.gridx = 1; form.add(txtEventTimeZone, gbc);

        gbc.gridy = 8; gbc.gridx = 0; form.add(new JLabel("Escolha de atividades:"), gbc);
        JPanel selectionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        chkActivitySelectionEnabled = new JCheckBox("Habilitada", true);
        chkActivitySelectionRequired = new JCheckBox("Obrigatória", false);
        selectionPanel.add(chkActivitySelectionEnabled);
        selectionPanel.add(chkActivitySelectionRequired);
        gbc.gridx = 1; form.add(selectionPanel, gbc);

        gbc.gridy = 9; gbc.gridx = 0; gbc.gridwidth = 2;
        JButton btnCreate = new JButton("Criar Evento (Rascunho)");
        btnCreate.setBackground(new Color(37, 99, 235));
        btnCreate.setForeground(Color.WHITE);
        btnCreate.addActionListener(e -> handleCreateEvent());
        form.add(btnCreate, gbc);

        splitPane.setLeftComponent(form);

        JPanel right = new JPanel(new BorderLayout(8, 8));
        right.setBorder(new EmptyBorder(10, 10, 10, 10));

        eventListModel = new DefaultListModel<>();
        eventList = new JList<>(eventListModel);
        right.add(new JScrollPane(eventList), BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnPublish = new JButton("Publicar Evento");
        btnPublish.addActionListener(e -> handlePublishSelectedEvent());
        JButton btnCancel = new JButton("Cancelar Evento");
        btnCancel.addActionListener(e -> handleCancelSelectedEvent());
        JButton btnFinish = new JButton("Encerrar Evento");
        btnFinish.addActionListener(e -> handleFinishSelectedEvent());
        actions.add(btnPublish);
        actions.add(btnCancel);
        actions.add(btnFinish);
        right.add(actions, BorderLayout.SOUTH);

        splitPane.setRightComponent(right);
        panel.add(splitPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createActivitiesPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(460);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(15, 15, 15, 15));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 4, 4, 4);

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        JLabel lbl = new JLabel("Adicionar Atividade ao Evento");
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
        form.add(lbl, gbc);

        gbc.gridwidth = 1;
        gbc.gridy = 1; gbc.gridx = 0; form.add(new JLabel("Evento:"), gbc);
        cbActivityEvents = new JComboBox<>();
        cbActivityEvents.addActionListener(e -> refreshActivityList());
        gbc.gridx = 1; form.add(cbActivityEvents, gbc);

        gbc.gridy = 2; gbc.gridx = 0; form.add(new JLabel("Titulo:"), gbc);
        txtActTitle = new JTextField();
        gbc.gridx = 1; form.add(txtActTitle, gbc);

        gbc.gridy = 3; gbc.gridx = 0; form.add(new JLabel("Descricao:"), gbc);
        txtActDesc = new JTextArea(2, 20);
        gbc.gridx = 1; form.add(new JScrollPane(txtActDesc), gbc);

        gbc.gridy = 4; gbc.gridx = 0; form.add(new JLabel("Inicio:"), gbc);
        txtActStart = new JTextField(LocalDateTime.now().plusDays(1).withHour(9).format(FMT));
        gbc.gridx = 1; form.add(txtActStart, gbc);

        gbc.gridy = 5; gbc.gridx = 0; form.add(new JLabel("Fim:"), gbc);
        txtActEnd = new JTextField(LocalDateTime.now().plusDays(1).withHour(11).format(FMT));
        gbc.gridx = 1; form.add(txtActEnd, gbc);

        gbc.gridy = 6; gbc.gridx = 0; form.add(new JLabel("Sala / Local:"), gbc);
        txtActRoom = new JTextField("Auditorio Principal");
        gbc.gridx = 1; form.add(txtActRoom, gbc);

        gbc.gridy = 7; gbc.gridx = 0; form.add(new JLabel("Trilha/Espaco:"), gbc);
        txtActTrack = new JTextField("Engenharia");
        gbc.gridx = 1; form.add(txtActTrack, gbc);

        gbc.gridy = 8; gbc.gridx = 0; form.add(new JLabel("Capacidade:"), gbc);
        txtActCapacity = new JTextField("100");
        gbc.gridx = 1; form.add(txtActCapacity, gbc);

        gbc.gridy = 9; gbc.gridx = 0; form.add(new JLabel("Tipo:"), gbc);
        cbActType = new JComboBox<>(ActivityType.values());
        gbc.gridx = 1; form.add(cbActType, gbc);

        gbc.gridy = 10; gbc.gridx = 0; form.add(new JLabel("Politica Frequencia:"), gbc);
        cbActAttendancePolicy = new JComboBox<>(new String[]{"SINGLE_CHECKIN", "CHECKIN_CHECKOUT", "MANUAL_ONLY"});
        gbc.gridx = 1; form.add(cbActAttendancePolicy, gbc);

        gbc.gridy = 11; gbc.gridx = 0; form.add(new JLabel("Palestrante:"), gbc);
        txtSpeakerName = new JTextField("Dr. Palestrante Convidado");
        gbc.gridx = 1; form.add(txtSpeakerName, gbc);

        gbc.gridy = 12; gbc.gridx = 0; form.add(new JLabel("Papel:"), gbc);
        txtSpeakerRole = new JTextField("Palestrante Principal");
        gbc.gridx = 1; form.add(txtSpeakerRole, gbc);

        gbc.gridy = 13; gbc.gridx = 0; gbc.gridwidth = 2;
        JButton btnAddAct = new JButton("Adicionar Atividade (com deteccao de conflitos)");
        btnAddAct.setBackground(new Color(16, 185, 129));
        btnAddAct.setForeground(Color.WHITE);
        btnAddAct.addActionListener(e -> handleAddActivity());
        form.add(btnAddAct, gbc);

        splitPane.setLeftComponent(form);

        JPanel right = new JPanel(new BorderLayout(8, 8));
        right.setBorder(new EmptyBorder(10, 10, 10, 10));
        activityListModel = new DefaultListModel<>();
        right.add(new JScrollPane(new JList<>(activityListModel)), BorderLayout.CENTER);

        splitPane.setRightComponent(right);
        panel.add(splitPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createAttendancePanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        top.add(new JLabel("Evento:"));
        cbAttendanceEvents = new JComboBox<>();
        cbAttendanceEvents.addActionListener(e -> refreshAttendanceActivities());
        top.add(cbAttendanceEvents);

        top.add(new JLabel("Atividade:"));
        cbAttendanceActivities = new JComboBox<>();
        cbAttendanceActivities.addActionListener(e -> refreshAttendanceTable());
        top.add(cbAttendanceActivities);

        JButton btnGenQr = new JButton("Gerar Token QR Code");
        btnGenQr.addActionListener(e -> handleGenerateQrToken());
        top.add(btnGenQr);

        txtQrTokenDisplay = new JTextField(15);
        txtQrTokenDisplay.setEditable(false);
        top.add(txtQrTokenDisplay);

        panel.add(top, BorderLayout.NORTH);

        String[] cols = {"ID Usuario", "Nome", "E-mail", "Status Presenca", "Ultimo Registro", "Origem"};
        modelAttendance = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        tableAttendance = new JTable(modelAttendance);
        panel.add(new JScrollPane(tableAttendance), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        JButton btnManualCheckIn = new JButton("Lancar Presenca Manual (Check-in)");
        btnManualCheckIn.addActionListener(e -> handleManualAttendance(AttendanceType.CHECK_IN));
        JButton btnManualCheckOut = new JButton("Lancar Saida Manual (Check-out)");
        btnManualCheckOut.addActionListener(e -> handleManualAttendance(AttendanceType.CHECK_OUT));

        bottom.add(btnManualCheckIn);
        bottom.add(btnManualCheckOut);
        panel.add(bottom, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createSurveysPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        top.add(new JLabel("Atividade:"));
        cbSurveyActivities = new JComboBox<>();
        cbSurveyActivities.addActionListener(e -> refreshSurveyResults());
        top.add(cbSurveyActivities);

        JButton btnAddDefSurvey = new JButton("Criar Questionario Padrao");
        btnAddDefSurvey.addActionListener(e -> handleCreateDefaultSurvey());
        top.add(btnAddDefSurvey);

        panel.add(top, BorderLayout.NORTH);

        txtSurveyResultsDisplay = new JTextArea();
        txtSurveyResultsDisplay.setFont(new Font("Monospaced", Font.PLAIN, 12));
        txtSurveyResultsDisplay.setEditable(false);
        panel.add(new JScrollPane(txtSurveyResultsDisplay), BorderLayout.CENTER);

        return panel;
    }

    private JPanel createReportsPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        top.add(new JLabel("Evento:"));
        cbReportEvents = new JComboBox<>();
        cbReportEvents.addActionListener(e -> refreshReportPreview());
        top.add(cbReportEvents);

        JButton btnExportCsv = new JButton("Exportar CSV (Inscritos)");
        btnExportCsv.addActionListener(e -> handleExportCsv());
        top.add(btnExportCsv);

        JButton btnExportPdfInsc = new JButton("Exportar PDF (Inscritos)");
        btnExportPdfInsc.addActionListener(e -> handleExportEnrolledPdf());
        top.add(btnExportPdfInsc);

        JButton btnExportPdfFreq = new JButton("Exportar PDF (Frequencia)");
        btnExportPdfFreq.addActionListener(e -> handleExportAttendancePdf());
        top.add(btnExportPdfFreq);

        panel.add(top, BorderLayout.NORTH);

        txtReportPreview = new JTextArea();
        txtReportPreview.setFont(new Font("Monospaced", Font.PLAIN, 12));
        txtReportPreview.setEditable(false);
        panel.add(new JScrollPane(txtReportPreview), BorderLayout.CENTER);

        return panel;
    }

    private void handleCreateEvent() {
        try {
            String title = txtEventTitle.getText();
            String desc = txtEventDesc.getText();
            LocalDateTime start = LocalDateTime.parse(txtEventStart.getText().trim(), FMT);
            LocalDateTime end = LocalDateTime.parse(txtEventEnd.getText().trim(), FMT);
            int cap = Integer.parseInt(txtEventCapacity.getText().trim());

            Event event = new Event(null, title, desc, new Period(start, end),
                    EventStatus.DRAFT, 1L, null, cap, new MinimumAttendancePercentagePolicy(75.0));
            event.configureRegistration(chkActivitySelectionEnabled.isSelected(),
                    chkActivitySelectionRequired.isSelected(),
                    LocalDateTime.parse(txtRegistrationDeadline.getText().trim(), FMT),
                    txtEventTimeZone.getText().trim());

            apiClient.createEvent(event);
            JOptionPane.showMessageDialog(this, "Evento criado com sucesso (Status: RASCUNHO)!", "Sucesso", JOptionPane.INFORMATION_MESSAGE);
            refreshAllData();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erro: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handlePublishSelectedEvent() {
        int idx = eventList.getSelectedIndex();
        if (idx < 0 || idx >= loadedEvents.size()) {
            JOptionPane.showMessageDialog(this, "Selecione um evento na lista.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Event e = loadedEvents.get(idx);
        try {
            apiClient.publishEvent(e.getId());
            JOptionPane.showMessageDialog(this, "Evento publicado com sucesso! Ja esta visivel no site publico.", "Publicado", JOptionPane.INFORMATION_MESSAGE);
            refreshAllData();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erro ao publicar: " + ex.getMessage(), "Erro de Validacao", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleCancelSelectedEvent() {
        int idx = eventList.getSelectedIndex();
        if (idx < 0 || idx >= loadedEvents.size()) return;
        Event e = loadedEvents.get(idx);
        try {
            apiClient.cancelEvent(e.getId());
            JOptionPane.showMessageDialog(this, "Evento cancelado.", "Info", JOptionPane.INFORMATION_MESSAGE);
            refreshAllData();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erro: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleFinishSelectedEvent() {
        int idx = eventList.getSelectedIndex();
        if (idx < 0 || idx >= loadedEvents.size()) return;
        Event event = loadedEvents.get(idx);
        try {
            apiClient.finishEvent(event.getId());
            JOptionPane.showMessageDialog(this, "Evento encerrado.", "Info", JOptionPane.INFORMATION_MESSAGE);
            refreshAllData();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erro: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleAddActivity() {
        try {
            int evIdx = cbActivityEvents.getSelectedIndex();
            if (evIdx < 0 || evIdx >= loadedEvents.size()) return;
            Event event = loadedEvents.get(evIdx);

            String title = txtActTitle.getText();
            String desc = txtActDesc.getText();
            LocalDateTime start = LocalDateTime.parse(txtActStart.getText().trim(), FMT);
            LocalDateTime end = LocalDateTime.parse(txtActEnd.getText().trim(), FMT);
            String room = txtActRoom.getText().trim();
            String track = txtActTrack.getText().trim();
            int cap = Integer.parseInt(txtActCapacity.getText().trim());
            ActivityType type = (ActivityType) cbActType.getSelectedItem();
            String polStr = (String) cbActAttendancePolicy.getSelectedItem();

            List<Speaker> speakers = new ArrayList<>();
            if (!txtSpeakerName.getText().trim().isEmpty()) {
                speakers.add(new Speaker(null, txtSpeakerName.getText().trim(), txtSpeakerRole.getText().trim(), "", null));
            }

            Activity act = new Activity(null, event.getId(), title, desc, new Period(start, end),
                    new ActivityLocation(room, track, cap), type, cap, 0, speakers,
                    AttendancePolicyFactory.create(polStr), true);

            apiClient.addActivity(event.getId(), act);
            JOptionPane.showMessageDialog(this, "Atividade adicionada com sucesso!", "Sucesso", JOptionPane.INFORMATION_MESSAGE);
            refreshAllData();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erro: " + ex.getMessage(), "Erro de Agendamento/Conflito", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleGenerateQrToken() {
        try {
            Activity act = getSelectedActivityForAttendance();
            if (act == null) return;
            String token = apiClient.generateQrToken(act.getId());
            txtQrTokenDisplay.setText(token);
            JOptionPane.showMessageDialog(this, "Token QR Code gerado com sucesso!\nToken seguro: " + token +
                    "\n\nOs participantes podem utilizar este token no site publico para registrar presenca.", "QR Code Ativo", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erro: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleManualAttendance(AttendanceType type) {
        int row = tableAttendance.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Selecione um participante na tabela para lancar presenca manual.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Long userId = (Long) modelAttendance.getValueAt(row, 0);
        Activity act = getSelectedActivityForAttendance();
        if (act == null) return;

        String reason = JOptionPane.showInputDialog(this, "Informe o motivo/justificativa para lancamento manual (RN-11, RN-12):", "Auditoria de Frequencia", JOptionPane.QUESTION_MESSAGE);
        if (reason == null || reason.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Operacao cancelada. A justificativa e obrigatoria para conformidade e auditoria.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            apiClient.recordManualAttendance(act.getId(), userId, type, reason.trim());
            JOptionPane.showMessageDialog(this, "Presenca manual lancada e auditada com sucesso!", "Sucesso", JOptionPane.INFORMATION_MESSAGE);
            refreshAttendanceTable();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erro: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleCreateDefaultSurvey() {
        try {
            Activity act = getSelectedActivityForSurvey();
            if (act == null) return;

            FeedbackSurvey survey = new FeedbackSurvey(null, act.getId(), "Avaliacao de " + act.getTitle(),
                    List.of(
                            new SurveyQuestion(null, "Como voce avalia o dominio e didatica do apresentador?", QuestionType.RATING_SCALE, List.of(), true),
                            new SurveyQuestion(null, "O conteudo atendeu as suas expectativas?", QuestionType.SINGLE_CHOICE, List.of("Sim, totalmente", "Parcialmente", "Nao atendeu"), true),
                            new SurveyQuestion(null, "Criticas ou sugestoes:", QuestionType.TEXT, List.of(), false)
                    ), true);

            apiClient.createSurvey(survey);
            JOptionPane.showMessageDialog(this, "Questionario configurado com sucesso!", "Sucesso", JOptionPane.INFORMATION_MESSAGE);
            refreshSurveyResults();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erro: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleExportCsv() {
        Event ev = getSelectedEventForReport();
        if (ev == null) return;
        try {
            byte[] csv = apiClient.enrolledCsv(ev.getId());
            JFileChooser fc = new JFileChooser();
            fc.setSelectedFile(new File("relatorio_inscritos_" + ev.getId() + ".csv"));
            if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                try (FileOutputStream fos = new FileOutputStream(fc.getSelectedFile())) {
                    fos.write(csv);
                }
                JOptionPane.showMessageDialog(this, "Arquivo CSV salvo com sucesso!", "Exportacao Concluida", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erro ao exportar CSV: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleExportEnrolledPdf() {
        Event ev = getSelectedEventForReport();
        if (ev == null) return;
        try {
            byte[] pdf = apiClient.enrolledPdf(ev.getId());
            JFileChooser fc = new JFileChooser();
            fc.setSelectedFile(new File("relatorio_inscritos_" + ev.getId() + ".pdf"));
            if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                try (FileOutputStream fos = new FileOutputStream(fc.getSelectedFile())) {
                    fos.write(pdf);
                }
                JOptionPane.showMessageDialog(this, "Relatorio em PDF gerado com sucesso!", "PDF Criado", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erro ao gerar PDF: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleExportAttendancePdf() {
        Event ev = getSelectedEventForReport();
        if (ev == null) return;
        try {
            byte[] pdf = apiClient.attendancePdf(ev.getId());
            JFileChooser fc = new JFileChooser();
            fc.setSelectedFile(new File("relatorio_frequencia_" + ev.getId() + ".pdf"));
            if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                try (FileOutputStream fos = new FileOutputStream(fc.getSelectedFile())) {
                    fos.write(pdf);
                }
                JOptionPane.showMessageDialog(this, "Relatorio de frequencia em PDF gerado com sucesso!", "PDF Criado", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erro ao gerar PDF: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void refreshAllData() {
        try {
            loadedEvents = apiClient.listEvents();
            eventListModel.clear();
            cbActivityEvents.removeAllItems();
            cbAttendanceEvents.removeAllItems();
            cbReportEvents.removeAllItems();

            for (Event e : loadedEvents) {
                eventListModel.addElement(String.format("ID: %-3d | %-35s | Status: %-12s | Inicio: %s",
                        e.getId(), e.getTitle(), e.getStatus().getDescription(), e.getPeriod().getStart().format(FMT)));
                cbActivityEvents.addItem(e.getId() + " - " + e.getTitle());
                cbAttendanceEvents.addItem(e.getId() + " - " + e.getTitle());
                cbReportEvents.addItem(e.getId() + " - " + e.getTitle());
            }

            refreshActivityList();
            refreshAttendanceActivities();
            refreshSurveyActivities();
            refreshReportPreview();
        } catch (Exception e) {
            System.err.println("Erro ao atualizar dados: " + e.getMessage());
        }
    }

    private void refreshActivityList() {
        activityListModel.clear();
        int idx = cbActivityEvents.getSelectedIndex();
        if (idx < 0 || idx >= loadedEvents.size()) return;
        Event ev = loadedEvents.get(idx);
        List<Activity> acts = apiClient.listActivities(ev.getId());
        for (Activity a : acts) {
            activityListModel.addElement(String.format("ID: %-3d | %-30s | Sala: %-15s | Vagas: %d/%d | Pol: %s",
                    a.getId(), a.getTitle(), a.getLocation().getRoom(), a.getCurrentEnrollments(), a.getMaxCapacity(), a.getAttendancePolicy().getPolicyName()));
        }
    }

    private void refreshAttendanceActivities() {
        cbAttendanceActivities.removeAllItems();
        int idx = cbAttendanceEvents.getSelectedIndex();
        if (idx < 0 || idx >= loadedEvents.size()) return;
        Event ev = loadedEvents.get(idx);
        List<Activity> acts = apiClient.listActivities(ev.getId());
        for (Activity a : acts) {
            cbAttendanceActivities.addItem(a.getId() + " - " + a.getTitle());
        }
        refreshAttendanceTable();
    }

    private void refreshAttendanceTable() {
        modelAttendance.setRowCount(0);
        Activity act = getSelectedActivityForAttendance();
        if (act == null) return;

        List<AttendanceStatusDto> list = apiClient.attendanceOverview(act.getId());
        for (AttendanceStatusDto dto : list) {
            modelAttendance.addRow(new Object[]{
                    dto.getUserId(),
                    dto.getUserName(),
                    dto.getUserEmail(),
                    dto.getStatus().getDescription(),
                    dto.getLastRecordTime() != null ? dto.getLastRecordTime().format(FMT) : "-",
                    dto.getRecordedBy()
            });
        }
    }

    private void refreshSurveyActivities() {
        cbSurveyActivities.removeAllItems();
        for (Event e : loadedEvents) {
            for (Activity a : apiClient.listActivities(e.getId())) {
                cbSurveyActivities.addItem(a.getId() + " - " + a.getTitle() + " (" + e.getTitle() + ")");
            }
        }
        refreshSurveyResults();
    }

    private void refreshSurveyResults() {
        Activity act = getSelectedActivityForSurvey();
        if (act == null) {
            txtSurveyResultsDisplay.setText("Nenhuma atividade selecionada.");
            return;
        }

        try {
            SurveyResultsDto res = apiClient.surveyResults(act.getId());
            StringBuilder sb = new StringBuilder();
            sb.append("========================================================================\n");
            sb.append("CONSOLIDACAO DE AVALIACOES - ATIVIDADE: ").append(res.getActivityTitle()).append("\n");
            sb.append("Questionario: ").append(res.getSurveyTitle()).append("\n");
            sb.append("Total de Respostas Coletadas: ").append(res.getTotalResponses()).append("\n");
            sb.append("Media Geral de Satisfacao: ").append(String.format("%.2f / 5.00", res.getAverageRating())).append("\n");
            sb.append("========================================================================\n\n");

            sb.append("--- METRICAS POR QUESTAO ---\n");
            for (SurveyResultsDto.QuestionMetricDto q : res.getQuestionMetrics()) {
                sb.append("* ").append(q.getQuestionText()).append(" (").append(q.getQuestionType()).append(")\n");
                if (q.getAverageScore() > 0) {
                    sb.append("  Media: ").append(String.format("%.2f", q.getAverageScore())).append(" / 5.0\n");
                }
                if (q.getOptionDistribution() != null) {
                    q.getOptionDistribution().forEach((k, v) -> sb.append("  - ").append(k).append(": ").append(v).append(" voto(s)\n"));
                }
            }

            sb.append("\n--- COMENTARIOS E FEEDBACKS QUALITATIVOS ---\n");
            if (res.getTextComments().isEmpty()) {
                sb.append("Nenhum comentario textual registrado.\n");
            } else {
                for (String c : res.getTextComments()) {
                    sb.append("Feedback: ").append(c).append("\n");
                }
            }

            txtSurveyResultsDisplay.setText(sb.toString());
        } catch (Exception e) {
            txtSurveyResultsDisplay.setText("Erro ao carregar avaliacoes: " + e.getMessage());
        }
    }

    private void refreshReportPreview() {
        Event ev = getSelectedEventForReport();
        if (ev == null) {
            txtReportPreview.setText("Nenhum evento selecionado.");
            return;
        }

        try {
            EnrolledReportDto enrolled = apiClient.enrolledReport(ev.getId());
            AttendanceReportDto attendance = apiClient.attendanceReport(ev.getId());

            StringBuilder sb = new StringBuilder();
            sb.append("RELATORIO EXECUTIVO DO EVENTO: ").append(ev.getTitle()).append("\n");
            sb.append("Periodo: ").append(ev.getPeriod().getStart().format(FMT)).append(" ate ").append(ev.getPeriod().getEnd().format(FMT)).append("\n");
            sb.append("Status Atual: ").append(ev.getStatus().getDescription()).append("\n");
            sb.append("Total de Inscritos: ").append(enrolled.getTotalEnrolled()).append(" / ").append(enrolled.getEventCapacity()).append("\n");
            sb.append("Taxa Geral de Frequencia: ").append(String.format("%.1f%%", attendance.getOverallAttendanceRate())).append("\n\n");

            sb.append("--- RESUMO DE ATIVIDADES ---\n");
            for (AttendanceReportDto.ActivityAttendanceSummaryDto a : attendance.getActivitySummaries()) {
                sb.append(String.format("* [%-3d] %-30s | Sala: %-15s | Presentes: %-3d | Ausentes: %-3d\n",
                        a.getActivityId(), a.getActivityTitle(), a.getRoom(), a.getTotalPresent(), a.getTotalAbsent()));
            }

            txtReportPreview.setText(sb.toString());
        } catch (Exception e) {
            txtReportPreview.setText("Erro ao gerar previa: " + e.getMessage());
        }
    }

    private Activity getSelectedActivityForAttendance() {
        String sel = (String) cbAttendanceActivities.getSelectedItem();
        if (sel == null) return null;
        Long id = Long.parseLong(sel.split(" - ")[0]);
        return apiClient.getActivity(id);
    }

    private Activity getSelectedActivityForSurvey() {
        String sel = (String) cbSurveyActivities.getSelectedItem();
        if (sel == null) return null;
        Long id = Long.parseLong(sel.split(" - ")[0]);
        return apiClient.getActivity(id);
    }

    private Event getSelectedEventForReport() {
        int idx = cbReportEvents.getSelectedIndex();
        if (idx < 0 || idx >= loadedEvents.size()) return null;
        return loadedEvents.get(idx);
    }
}
