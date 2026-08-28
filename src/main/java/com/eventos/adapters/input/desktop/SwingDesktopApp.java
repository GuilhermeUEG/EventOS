package com.eventos.adapters.input.desktop;

import com.eventos.application.ports.input.EventUseCase;
import com.eventos.domain.Event;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class SwingDesktopApp extends JFrame {

    private final EventUseCase eventUseCase;

    private DefaultListModel<String> listModel;
    private JList<String> eventList;
    private JTextField txtTitle;
    private JTextArea txtDescription;
    private JTextField txtStartDate;
    private JTextField txtEndDate;

    public SwingDesktopApp(EventUseCase eventUseCase) {
        this.eventUseCase = eventUseCase;
        initializeUI();
        refreshEventList();
    }

    private void initializeUI() {
        setTitle("EventOS - Plataforma de Gestão de Eventos");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 500);
        setLocationRelativeTo(null);

        // Core Layout
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        mainPanel.setBackground(new Color(245, 247, 250)); // Premium light gray background
        setContentPane(mainPanel);

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(30, 41, 59)); // Slate 800 dark blue
        headerPanel.setBorder(new EmptyBorder(10, 15, 10, 15));
        JLabel lblHeader = new JLabel("EventOS");
        lblHeader.setFont(new Font("Segoe UI", Font.BOLD, 22));
        lblHeader.setForeground(Color.WHITE);
        JLabel lblSubtitle = new JLabel("Painel Administrativo da Faculdade - POO II");
        lblSubtitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblSubtitle.setForeground(new Color(203, 213, 225));
        headerPanel.add(lblHeader, BorderLayout.WEST);
        headerPanel.add(lblSubtitle, BorderLayout.EAST);
        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // Split Pane for Form vs List
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(350);
        splitPane.setResizeWeight(0.5);
        mainPanel.add(splitPane, BorderLayout.CENTER);

        // LEFT: Registration Form
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBackground(Color.WHITE);
        formPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);

        // Form Title
        JLabel lblFormTitle = new JLabel("Novo Evento");
        lblFormTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblFormTitle.setForeground(new Color(15, 23, 42));
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        formPanel.add(lblFormTitle, gbc);
        gbc.gridwidth = 1;

        // Title Field
        gbc.gridx = 0; gbc.gridy = 1;
        formPanel.add(new JLabel("Título:"), gbc);
        txtTitle = new JTextField();
        gbc.gridx = 1;
        formPanel.add(txtTitle, gbc);

        // Description Field
        gbc.gridx = 0; gbc.gridy = 2;
        formPanel.add(new JLabel("Descrição:"), gbc);
        txtDescription = new JTextArea(3, 20);
        txtDescription.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
        gbc.gridx = 1;
        formPanel.add(new JScrollPane(txtDescription), gbc);

        // Start Date
        gbc.gridx = 0; gbc.gridy = 3;
        formPanel.add(new JLabel("Início (dd/MM/yyyy HH:mm):"), gbc);
        txtStartDate = new JTextField(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        gbc.gridx = 1;
        formPanel.add(txtStartDate, gbc);

        // End Date
        gbc.gridx = 0; gbc.gridy = 4;
        formPanel.add(new JLabel("Fim (dd/MM/yyyy HH:mm):"), gbc);
        txtEndDate = new JTextField(LocalDateTime.now().plusDays(2).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        gbc.gridx = 1;
        formPanel.add(txtEndDate, gbc);

        // Register Button
        JButton btnRegister = new JButton("Cadastrar Evento");
        btnRegister.setBackground(new Color(37, 99, 235)); // Slate Blue
        btnRegister.setForeground(Color.WHITE);
        btnRegister.setFocusPainted(false);
        btnRegister.addActionListener(e -> handleCreateEvent());
        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 2;
        formPanel.add(btnRegister, gbc);

        // Demo Data Button
        JButton btnDemo = new JButton("Gerar Dados de Teste");
        btnDemo.setBackground(new Color(79, 70, 229)); // Purple
        btnDemo.setForeground(Color.WHITE);
        btnDemo.setFocusPainted(false);
        btnDemo.addActionListener(e -> generateDemoData());
        gbc.gridy = 6;
        formPanel.add(btnDemo, gbc);

        splitPane.setLeftComponent(formPanel);

        // RIGHT: Event List
        JPanel listPanel = new JPanel(new BorderLayout(5, 5));
        listPanel.setBackground(Color.WHITE);
        listPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JLabel lblListTitle = new JLabel("Eventos Cadastrados");
        lblListTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblListTitle.setForeground(new Color(15, 23, 42));
        listPanel.add(lblListTitle, BorderLayout.NORTH);

        listModel = new DefaultListModel<>();
        eventList = new JList<>(listModel);
        eventList.setFont(new Font("Monospaced", Font.PLAIN, 12));
        listPanel.add(new JScrollPane(eventList), BorderLayout.CENTER);

        JButton btnRefresh = new JButton("Atualizar Lista");
        btnRefresh.addActionListener(e -> refreshEventList());
        listPanel.add(btnRefresh, BorderLayout.SOUTH);

        splitPane.setRightComponent(listPanel);
    }

    private void handleCreateEvent() {
        try {
            String title = txtTitle.getText();
            String description = txtDescription.getText();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            LocalDateTime start = LocalDateTime.parse(txtStartDate.getText(), formatter);
            LocalDateTime end = LocalDateTime.parse(txtEndDate.getText(), formatter);

            Event event = new Event(null, title, description, start, end);
            eventUseCase.createEvent(event);

            JOptionPane.showMessageDialog(this, "Evento cadastrado com sucesso!", "Sucesso", JOptionPane.INFORMATION_MESSAGE);
            clearForm();
            refreshEventList();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erro ao cadastrar evento: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void generateDemoData() {
        try {
            LocalDateTime now = LocalDateTime.now();
            Event simposio = new Event(null, "Simpósio de Engenharia de Software", "Grande evento anual acadêmico", now.plusDays(1), now.plusDays(3));
            eventUseCase.createEvent(simposio);

            Event workshop = new Event(null, "Workshop de Java Avançado", "Técnicas avançadas de concorrência e POO", now.plusDays(5), now.plusDays(6));
            eventUseCase.createEvent(workshop);

            JOptionPane.showMessageDialog(this, "Dados de demonstração inseridos no banco H2 com sucesso!", "Sucesso", JOptionPane.INFORMATION_MESSAGE);
            refreshEventList();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erro ao gerar dados: " + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void refreshEventList() {
        listModel.clear();
        try {
            List<Event> events = eventUseCase.listEvents();
            if (events.isEmpty()) {
                listModel.addElement("Nenhum evento cadastrado ainda.");
            } else {
                for (Event e : events) {
                    listModel.addElement(String.format("ID: %-3d | %-25s | Status: %-10s", e.getId(), e.getTitle(), e.getStatus()));
                }
            }
        } catch (Exception ex) {
            listModel.addElement("Erro ao carregar lista: " + ex.getMessage());
        }
    }

    private void clearForm() {
        txtTitle.setText("");
        txtDescription.setText("");
        txtStartDate.setText(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        txtEndDate.setText(LocalDateTime.now().plusDays(2).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
    }
}
