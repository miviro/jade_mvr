package src.jade_mvr;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.text.NumberFormatter;

import src.jade_mvr.MainAgent.GameParametersStruct;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.text.DecimalFormat;
import java.util.ArrayList;

public class GUI extends JFrame {
    private JMenuBar menuBar;
    private JMenu fileMenu;
    private JMenu aboutMenu;
    private JPanel actionsPanel;
    private JPanel configPanel;
    private JPanel rightPanel;
    private JPanel statsPanel;
    private JPanel logPanel;
    private JPanel knownAgentsPanel;
    private JButton newGameButton;
    private JButton resetStats;
    private JButton stopButton;
    private JButton continueButton;
    private JButton playAllRoundsButton;
    private JButton playXRoundsButton;
    private JSpinner playXRoundsSpinner;
    private JLabel playXRoundsLabel;
    private JTextArea logTextArea;
    private JCheckBox verboseCheckBox;

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
        fileMenu = new JMenu("File");
        aboutMenu = new JMenu("About");
        menuBar.add(fileMenu);
        menuBar.add(aboutMenu);
        setJMenuBar(menuBar);
    }

    private void createActionsPanel() {
        actionsPanel = new JPanel();
        actionsPanel.setBorder(BorderFactory.createTitledBorder("Actions"));
        actionsPanel.setPreferredSize(new Dimension(1200, 60));

        newGameButton = new JButton("New Game");
        resetStats = new JButton("Reset Stats");
        stopButton = new JButton("Stop");
        continueButton = new JButton("Continue");
        playAllRoundsButton = new JButton("Play All Rounds");

        playXRoundsButton = new JButton("Play");
        playXRoundsSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 1000, 1));
        playXRoundsLabel = new JLabel("rounds");

        actionsPanel.add(newGameButton);
        actionsPanel.add(resetStats);
        actionsPanel.add(stopButton);
        actionsPanel.add(continueButton);
        actionsPanel.add(playAllRoundsButton);
        actionsPanel.add(playXRoundsButton);
        actionsPanel.add(playXRoundsSpinner);
        actionsPanel.add(playXRoundsLabel);

        add(actionsPanel, BorderLayout.NORTH);
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
            int defaultValue = (i == agentNames.size() - 1) ? 
                             baseAgentsPerType + remainder : 
                             baseAgentsPerType;
            
            agentPanel.add(new JSpinner(new SpinnerNumberModel(defaultValue, 1, 100, 1)), BorderLayout.EAST);
            knownAgentsPanel.add(agentPanel);
        }

        return knownAgentsPanel;
    }

    private JPanel createParametersPanel() {
        JPanel parametersPanel = new JPanel(new GridLayout(4, 2, 10, 10));
        parametersPanel.setBorder(BorderFactory.createTitledBorder("Parameters"));

        // Retrieve current parameters
        GameParametersStruct params = MainAgent.getGameParameters();
        // Create spinners with default values
        JSpinner nSpinner = new JSpinner(new SpinnerNumberModel(params.N, 1, 1000, 1));
        JSpinner sSpinner = new JSpinner(new SpinnerNumberModel(params.S, 0, 100, 1));
        JSpinner rSpinner = new JSpinner(new SpinnerNumberModel(params.R, 1, 1000, 1));
        JSpinner iSpinner = new JSpinner(new SpinnerNumberModel(params.I, 0, 100, 1));
        parametersPanel.add(new JLabel("Number of players (N):"));
        parametersPanel.add(nSpinner);
        parametersPanel.add(new JLabel("Stock exchange fee (S%):"));
        parametersPanel.add(sSpinner);
        parametersPanel.add(new JLabel("Number of rounds (R):"));
        parametersPanel.add(rSpinner);
        parametersPanel.add(new JLabel("Inflation rate (I%):"));
        parametersPanel.add(iSpinner);

        ChangeListener updateListener = e -> {
            try {
                int n = (Integer) nSpinner.getValue();
                int s = (Integer) sSpinner.getValue();
                int r = (Integer) rSpinner.getValue();
                int i = (Integer) iSpinner.getValue();
                MainAgent.setGameParameters(new GameParametersStruct(n, s, r, i));
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
        iSpinner.addChangeListener(updateListener);

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
        String[] columnNames = { "Name", "Wins", "Lose", "Draw", "Points", "Invested", "Last Actions", "Delete" };
        Object[][] data = {
                { "Agent A", 10, 5, 2, 32, 1000, "Action 1", "Delete" },
                { "Agent B", 8, 7, 1, 25, 800, "Action 2", "Delete" },
                // Add more rows as needed
        };

        DefaultTableModel model = new DefaultTableModel(data, columnNames) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 8; // Only the "Delete" column is editable
            }
        };

        JTable table = new JTable(model);
        table.getColumn("Delete").setCellRenderer(new ButtonRenderer());
        table.getColumn("Delete").setCellEditor(new ButtonEditor(new JCheckBox()));

        JScrollPane scrollPane = new JScrollPane(table);
        table.setFillsViewportHeight(true);

        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.add(scrollPane, BorderLayout.CENTER);

        return tablePanel;
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
                // Call the function to handle the delete action
                handleDeleteAction();
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

        private void handleDeleteAction() {
            // Implement the delete action here
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
