package src.jade_mvr;

import javax.swing.*;
import javax.swing.text.NumberFormatter;

import src.jade_mvr.MainAgent.GameParametersStruct;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.text.DecimalFormat;

public class GUI extends JFrame {
    private JMenuBar menuBar;
    private JMenu fileMenu;
    private JMenu aboutMenu;
    private JPanel actionsPanel;
    private JPanel configPanel;
    private JPanel rightPanel;
    private JPanel statsPanel;
    private JPanel logPanel;
    private JButton newGameButton;
    private JButton stopButton;
    private JButton continueButton;
    private JButton playAllRoundsButton;
    private JButton playXRoundsButton;
    private JSpinner playXRoundsSpinner;
    private JLabel playXRoundsLabel;
    private JTextArea logTextArea;

    public GUI() {
        setTitle("JADE MVR");
        setSize(800, 600);
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
        actionsPanel.setPreferredSize(new Dimension(800, 60));

        newGameButton = new JButton("New Game");
        stopButton = new JButton("Stop");
        continueButton = new JButton("Continue");
        playAllRoundsButton = new JButton("Play All Rounds");

        playXRoundsButton = new JButton("Play");
        playXRoundsSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 1000, 1));
        playXRoundsLabel = new JLabel("rounds:");

        actionsPanel.add(newGameButton);
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
        configPanel.setPreferredSize(new Dimension(240, 540));

        JPanel bottomConfigPanel = new JPanel();
        bottomConfigPanel.setBorder(BorderFactory.createTitledBorder("Bottom Configuration"));
        bottomConfigPanel.setPreferredSize(new Dimension(240, 270));

        configPanel.add(createParametersPanel(), BorderLayout.NORTH);
        configPanel.add(bottomConfigPanel, BorderLayout.SOUTH);

        add(configPanel, BorderLayout.WEST);
    }

    private JPanel createParametersPanel() {
        JPanel parametersPanel = new JPanel(new GridLayout(4, 2, 10, 10));
        // Retrieve current parameters
        GameParametersStruct params = MainAgent.getGameParameters();
        // Create number-only formatted text fields with default values
        JFormattedTextField nField = new JFormattedTextField(new NumberFormatter(new DecimalFormat("#")));
        nField.setValue(params.N);
        JFormattedTextField sField = new JFormattedTextField(new NumberFormatter(new DecimalFormat("#")));
        sField.setValue(params.S);
        JFormattedTextField rField = new JFormattedTextField(new NumberFormatter(new DecimalFormat("#")));
        rField.setValue(params.R);
        JFormattedTextField iField = new JFormattedTextField(new NumberFormatter(new DecimalFormat("#")));
        iField.setValue(params.I);
        parametersPanel.add(new JLabel("Number of players (N):"));
        parametersPanel.add(nField);
        parametersPanel.add(new JLabel("Stock exchange fee (S%):"));
        parametersPanel.add(sField);
        parametersPanel.add(new JLabel("Number of rounds (R):"));
        parametersPanel.add(rField);
        parametersPanel.add(new JLabel("Inflation rate (I%):"));
        parametersPanel.add(iField);

            try {
                int n = ((Number) nField.getValue()).intValue();
                int s = ((Number) sField.getValue()).intValue();
                int r = ((Number) rField.getValue()).intValue();
                int i = ((Number) iField.getValue()).intValue();
                MainAgent.setGameParameters(new GameParametersStruct(n, s, r, i));
                appendLog("Parameters set to: N=" + n + ", S=" + s + ", R=" + r + ", I=" + i);
            } catch (Exception e) {
                appendLog("Invalid input for parameters.");
            }
        

        return parametersPanel;
    }

    private void createRightPanel() {
        rightPanel = new JPanel(new BorderLayout());

        statsPanel = new JPanel();
        statsPanel.setBorder(BorderFactory.createTitledBorder("Stats"));
        statsPanel.setPreferredSize(new Dimension(560, 300));
        rightPanel.add(statsPanel, BorderLayout.CENTER);

        add(rightPanel, BorderLayout.CENTER);
    }

    private void createLogPanel() {
        logPanel = new JPanel();
        logPanel.setBorder(BorderFactory.createTitledBorder("Log"));
        logPanel.setPreferredSize(new Dimension(800, 140));
        logTextArea = new JTextArea();
        logTextArea.setEditable(false);
        logTextArea.setLineWrap(true);
        logTextArea.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(logTextArea);
        scrollPane.setPreferredSize(new Dimension(780, 120));
        logPanel.add(scrollPane);
        add(logPanel, BorderLayout.SOUTH);
    }

    public void appendLog(String text) {
        logTextArea.append(text + "\n");
    }
}
