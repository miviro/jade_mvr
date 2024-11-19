package src.jade_mvr;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Dimension;

public class GUI extends JFrame {
    private JMenuBar menuBar;
    private JMenu fileMenu;
    private JMenu aboutMenu;
    private JPanel actionsPanel;
    private JPanel configPanel;
    private JPanel rightPanel;
    private JPanel statsPanel;
    private JPanel logPanel;

    public GUI() {
        setTitle("JADE MVR");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        createMenuBar();
        createActionsPanel();
        createConfigPanel();
        createRightPanel();
        createLogPanel();
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
        add(actionsPanel, BorderLayout.NORTH);
    }

    private void createConfigPanel() {
        configPanel = new JPanel();
        configPanel.setBorder(BorderFactory.createTitledBorder("Configuration"));
        configPanel.setPreferredSize(new Dimension(240, 540));
        add(configPanel, BorderLayout.WEST);
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
        add(logPanel, BorderLayout.SOUTH);
    }
}
