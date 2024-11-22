package src.jade_mvr;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;

import src.jade_mvr.MainAgent.GameParametersStruct;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.net.URI;
import java.awt.Desktop;
import java.util.ArrayList;

public class GUI extends JFrame {
    public JMenuBar menuBar;
    public JMenu quitMenu;
    public JPanel actionsPanel;
    public JPanel configPanel;
    public JPanel rightPanel;
    public JPanel statsPanel;
    public JPanel logPanel;
    public JPanel knownAgentsPanel;
    public JButton newGameButton;
    public JButton quitGameButton;
    public JButton resetStatsButton;
    public JButton stopButton;
    // public JButton continueButton;
    public JButton playAllRoundsButton;
    public JButton playXRoundsButton;
    public JSpinner playXRoundsSpinner;
    public JLabel playXRoundsLabel;
    public JLabel verboseLabel;
    public JTextArea logTextArea;
    public JCheckBox verboseCheckBox;
    public DefaultTableModel statsTableModel;

    public GUI() {
        setTitle("JADE MVR");
        setSize(1200, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        createLogPanel();
        createMenuBar();
        createActionsPanel();
        createConfigPanel();
        createRightPanel();
    }

    private void createMenuBar() {
        menuBar = new JMenuBar();
        quitMenu = new JMenu("Quit");
        JMenuItem confirmMenuItem = new JMenuItem("Confirm");
        confirmMenuItem.addActionListener(actionEvent -> {
            System.exit(0);
        });
        quitMenu.add(confirmMenuItem);

        // About
        JMenuItem aboutMenuItem = new JMenuItem("Author: Miguel Vila Rodríguez - 2024 - https://github.com/miviro/jade_mvr");
        aboutMenuItem.addActionListener(actionEvent -> {
            try {
                Desktop.getDesktop().browse(URI.create("https://github.com/miviro/jade_mvr"));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
        
        menuBar.add(quitMenu);
        menuBar.add(aboutMenuItem);
        menuBar.add(Box.createHorizontalGlue());

        setJMenuBar(menuBar);
    }

    private void createActionsPanel() {
        actionsPanel = new JPanel();
        actionsPanel.setBorder(BorderFactory.createTitledBorder("Actions"));
        actionsPanel.setPreferredSize(new Dimension(1200, 60));

        newGameButton = new JButton("New Game");

        quitGameButton = new JButton("Quit Game");
        quitGameButton.setEnabled(false);

        resetStatsButton = new JButton("Reset Stats");
        resetStatsButton.setEnabled(false);

        stopButton = new JButton("Stop");
        stopButton.setEnabled(false);
        // continueButton.setEnabled(false);
        playAllRoundsButton = new JButton("Play All Rounds");
        playAllRoundsButton.setEnabled(false);

        playXRoundsButton = new JButton("Play");
        playXRoundsButton.setEnabled(false);
        playXRoundsSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 1000, 1));
        playXRoundsSpinner.setEnabled(false);
        playXRoundsLabel = new JLabel("rounds");

        actionsPanel.add(newGameButton);
        actionsPanel.add(quitGameButton);
        actionsPanel.add(resetStatsButton);
        actionsPanel.add(stopButton);
        // actionsPanel.add(continueButton);
        actionsPanel.add(playAllRoundsButton);
        actionsPanel.add(playXRoundsButton);
        actionsPanel.add(playXRoundsSpinner);
        actionsPanel.add(playXRoundsLabel);

        add(actionsPanel, BorderLayout.NORTH);
    }

    void setPanelEnabled(JPanel panel, Boolean isEnabled) {
        panel.setEnabled(isEnabled);

        Component[] components = panel.getComponents();

        for (Component component : components) {
            if (component instanceof JPanel) {
                setPanelEnabled((JPanel) component, isEnabled);
            }
            component.setEnabled(isEnabled);
        }
    }

    private void createConfigPanel() {
        configPanel = new JPanel(new BorderLayout());
        configPanel.setBorder(BorderFactory.createTitledBorder("Configuration"));
        configPanel.setPreferredSize(new Dimension(340, 540));

        configPanel.add(createParametersPanel(), BorderLayout.NORTH);
        knownAgentsPanel = createKnownAgentsPanel();
        configPanel.add(knownAgentsPanel, BorderLayout.SOUTH);

        add(configPanel, BorderLayout.WEST);
    }

    private JPanel createKnownAgentsPanel() {
        JPanel knownAgentsPanel = new JPanel();
        knownAgentsPanel.setBorder(BorderFactory.createTitledBorder("Known Agents"));
        knownAgentsPanel.setLayout(new BoxLayout(knownAgentsPanel, BoxLayout.Y_AXIS));

        ArrayList<String> agentNames = MainAgent.getAgentTypesList();
        int totalAgents = MainAgent.getGameParameters().N;
        int baseAgentsPerType = totalAgents / agentNames.size();
        int remainder = totalAgents % agentNames.size();

        for (int i = 0; i < agentNames.size(); i++) {
            JPanel agentPanel = new JPanel(new BorderLayout());
            agentPanel.add(new JLabel(agentNames.get(i)), BorderLayout.WEST);

            // Last agent gets the remainder
            int defaultValue = (i == agentNames.size() - 1) ? baseAgentsPerType + remainder : baseAgentsPerType;

            agentPanel.add(new JSpinner(new SpinnerNumberModel(defaultValue, 1, 100, 1)), BorderLayout.EAST);
            knownAgentsPanel.add(agentPanel);
        }

        return knownAgentsPanel;
    }

    private JPanel createParametersPanel() {
        JPanel parametersPanel = new JPanel(new GridLayout(3, 2, 10, 10));
        parametersPanel.setBorder(BorderFactory.createTitledBorder("Parameters"));

        // Retrieve current parameters
        GameParametersStruct params = MainAgent.getGameParameters();
        JSpinner nSpinner = new JSpinner(new SpinnerNumberModel(params.N, 1, 100000, 1));
        JSpinner sSpinner = new JSpinner(new SpinnerNumberModel(params.S, 0, 10000, 1));
        JSpinner rSpinner = new JSpinner(new SpinnerNumberModel(params.R, 1, 100000, 1));
        parametersPanel.add(new JLabel("Number of players (N):"));
        parametersPanel.add(nSpinner);
        parametersPanel.add(new JLabel("Stock exchange fee (S%):"));
        parametersPanel.add(sSpinner);
        parametersPanel.add(new JLabel("Number of rounds (R):"));
        parametersPanel.add(rSpinner);

        ChangeListener updateListener = e -> {
            try {
                int n = (Integer) nSpinner.getValue();
                int r = (Integer) rSpinner.getValue();
                int s = (Integer) sSpinner.getValue();
                MainAgent.setGameParameters(new GameParametersStruct(n, r, s));
                // Update known agents panel
                configPanel.remove(knownAgentsPanel);
                knownAgentsPanel = createKnownAgentsPanel();
                configPanel.add(knownAgentsPanel, BorderLayout.SOUTH);
                configPanel.revalidate();
                configPanel.repaint();
            } catch (Exception ex) {
                appendLog("Invalid input for parameters.", false);
                appendLog(ex.getMessage(), true);
            }
        };

        nSpinner.addChangeListener(updateListener);
        sSpinner.addChangeListener(updateListener);
        rSpinner.addChangeListener(updateListener);

        return parametersPanel;
    }

    private void createRightPanel() {
        rightPanel = new JPanel(new BorderLayout());

        statsPanel = new JPanel(new BorderLayout());
        statsPanel.setBorder(BorderFactory.createTitledBorder("Stats"));
        statsPanel.setPreferredSize(new Dimension(560, 300));
        statsPanel.add(createStatsTablePanel(), BorderLayout.CENTER);
        rightPanel.add(statsPanel, BorderLayout.CENTER);

        add(rightPanel, BorderLayout.CENTER);
    }

    private JPanel createStatsTablePanel() {
        String[] columnNames = { "Name", "Wins", "Lose", "Draw", "Money", "Assets", "Last Actions", "Delete" };
        Object[][] data = {};

        statsTableModel = new DefaultTableModel(data, columnNames) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 8; // Only the "Delete" column is editable
            }
        };

        JTable table = new JTable(statsTableModel);
        table.getColumn("Delete").setCellRenderer(new ButtonRenderer());
        table.getColumn("Delete").setCellEditor(new ButtonEditor(new JCheckBox()));

        JScrollPane scrollPane = new JScrollPane(table);
        table.setFillsViewportHeight(true);

        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.add(scrollPane, BorderLayout.CENTER);

        return tablePanel;
    }

    public void updateStatsTable(Object[][] data) {
        statsTableModel.setRowCount(0);
        for (Object[] row : data) {
            statsTableModel.addRow(row);
        }
    }

    class ButtonRenderer extends JButton implements TableCellRenderer {
        public ButtonRenderer() {
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
                int row, int column) {
            setText((value == null) ? "" : value.toString());
            return this;
        }
    }

    class ButtonEditor extends DefaultCellEditor {
        private String label;
        private boolean isPushed;

        public ButtonEditor(JCheckBox checkBox) {
            super(checkBox);
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row,
                int column) {
            label = (value == null) ? "" : value.toString();
            JButton button = new JButton(label);
            button.addActionListener(e -> fireEditingStopped());
            isPushed = true;
            return button;
        }

        @Override
        public Object getCellEditorValue() {
            if (isPushed) {
                appendLog("Delete button clicked", false);
            }
            isPushed = false;
            return label;
        }

        @Override
        public boolean stopCellEditing() {
            isPushed = false;
            return super.stopCellEditing();
        }

        @Override
        protected void fireEditingStopped() {
            super.fireEditingStopped();
        }

        private void handleDeleteAgent() {
            appendLog("Delete button clicked", false);
        }
    }

    private void createLogPanel() {
        logPanel = new JPanel(new BorderLayout());

        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        titlePanel.add(new JLabel("Log"));
        verboseCheckBox = new JCheckBox("Verbose");
        verboseCheckBox.addActionListener(e -> MainAgent.setVerbose(verboseCheckBox.isSelected()));
        titlePanel.add(verboseCheckBox);

        verboseLabel = new JLabel("Round 0 / null, current index value: null, current inflation rate: null");
        titlePanel.add(verboseLabel);

        logPanel.add(titlePanel, BorderLayout.NORTH);
        logPanel.setPreferredSize(new Dimension(1200, 140));

        logTextArea = new JTextArea();
        logTextArea.setEditable(false);
        logTextArea.setLineWrap(true);
        logTextArea.setWrapStyleWord(true);

        JScrollPane scrollPane = new JScrollPane(logTextArea);
        scrollPane.setPreferredSize(new Dimension(1180, 120));

        logPanel.add(scrollPane, BorderLayout.CENTER);
        add(logPanel, BorderLayout.SOUTH);
        logTextArea.setCaretPosition(logTextArea.getDocument().getLength());
    }

    public void appendLog(String text, boolean verbose) {
        if (verbose && !MainAgent.getVerbose()) {
            return;
        }
        logTextArea.append(text + "\n");
        logTextArea.setCaretPosition(logTextArea.getDocument().getLength());
    }
}
