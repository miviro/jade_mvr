package src.jade_mvr;

import com.formdev.flatlaf.FlatLightLaf;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class GUI extends JFrame implements ActionListener {
    private JLabel leftPanelRoundsLabel;
    private JLabel leftPanelExtraInformation;
    private JList<String> list;
    private MainAgent mainAgent;
    private JTextArea rightPanelLoggingTextArea;
    private LoggingOutputStream loggingOutputStream;

    // Define a consistent color scheme
    private final Color primaryColor = new Color(0x2C3E50); // Dark Blue
    private final Color secondaryColor = new Color(0xECF0F1); // Light Grey
    private final Color accentColor = new Color(0x3498DB); // Bright Blue

    public GUI() {
        initUI();
    }

    public GUI(MainAgent agent) {
        mainAgent = agent;
        initUI();
        loggingOutputStream = new LoggingOutputStream(rightPanelLoggingTextArea);
    }

    public void log(String s) {
        Runnable appendLine = () -> {
            rightPanelLoggingTextArea.append('[' + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "] - " + s + "\n");
            rightPanelLoggingTextArea.setCaretPosition(rightPanelLoggingTextArea.getDocument().getLength());
        };
        SwingUtilities.invokeLater(appendLine);
    }

    public OutputStream getLoggingOutputStream() {
        return loggingOutputStream;
    }

    public void logLine(String s) {
        log(s);
    }

    public void setPlayersUI(String[] players) {
        DefaultListModel<String> listModel = new DefaultListModel<>();
        for (String s : players) {
            listModel.addElement(s);
        }
        list.setModel(listModel);
    }

    public void initUI() {
        setTitle("Game Interface");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1200, 800));
        setPreferredSize(new Dimension(1600, 900));

        // Set FlatLaf as the Look and Feel
        try {
            UIManager.setLookAndFeel(new FlatLightLaf());
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        // Customize UI defaults if needed
        UIManager.put("Button.background", accentColor);
        UIManager.put("Button.foreground", Color.WHITE);
        UIManager.put("Button.hoverBackground", primaryColor);
        UIManager.put("List.background", secondaryColor);
        UIManager.put("Table.background", secondaryColor);
        UIManager.put("Table.foreground", Color.BLACK);
        UIManager.put("Table.selectionBackground", accentColor);
        UIManager.put("Table.selectionForeground", Color.WHITE);
        UIManager.put("TextArea.background", secondaryColor);
        UIManager.put("TextArea.foreground", Color.BLACK);

        setJMenuBar(createMainMenuBar());
        setContentPane(createMainContentPane());
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private Container createMainContentPane() {
        JPanel pane = new JPanel(new BorderLayout(10, 10));
        pane.setBorder(new EmptyBorder(15, 15, 15, 15));
        pane.setBackground(secondaryColor);

        // Add Toolbar at the top
        pane.add(createToolBar(), BorderLayout.NORTH);

        // Split Pane for main content
        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, createLeftPanel(), createCenterPanel());
        mainSplitPane.setResizeWeight(0.2);
        mainSplitPane.setOneTouchExpandable(true);
        pane.add(mainSplitPane, BorderLayout.CENTER);

        // Log area at the bottom
        pane.add(createLogPanel(), BorderLayout.SOUTH);

        return pane;
    }

    private JToolBar createToolBar() {
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        toolBar.setBackground(primaryColor);

        JButton newGameButton = createToolbarButton("New Game", "new_game_icon.png");
        newGameButton.setToolTipText("Start a new game");
        newGameButton.addActionListener(actionEvent -> {
            mainAgent.newGame();
            logLine("New Game initiated.");
        });

        JButton stopButton = createToolbarButton("Stop", "stop_icon.png");
        stopButton.setToolTipText("Stop the current game");
        stopButton.addActionListener(this);

        JButton continueButton = createToolbarButton("Continue", "continue_icon.png");
        continueButton.setToolTipText("Continue the game");
        continueButton.addActionListener(this);

        toolBar.add(newGameButton);
        toolBar.addSeparator(new Dimension(10, 0));
        toolBar.add(stopButton);
        toolBar.addSeparator(new Dimension(10, 0));
        toolBar.add(continueButton);

        return toolBar;
    }

    private JButton createToolbarButton(String text, String iconPath) {
        JButton button = new JButton(text);
        button.setFocusPainted(false);
        button.setBackground(accentColor);
        button.setForeground(Color.WHITE);
        button.setBorder(new CompoundBorder(
                new LineBorder(primaryColor, 1, true),
                new EmptyBorder(5, 15, 5, 15)
        ));
        button.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        // Optionally add icons if available
        /*
        try {
            ImageIcon icon = new ImageIcon(getClass().getResource("/icons/" + iconPath));
            button.setIcon(icon);
        } catch (Exception e) {
            // Handle missing icon
        }
        */
        return button;
    }

    private JPanel createLeftPanel() {
        JPanel leftPanel = new JPanel(new BorderLayout(10, 10));
        leftPanel.setBorder(new CompoundBorder(
                new TitledBorder(new LineBorder(primaryColor, 2, true), "Players", TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 16), primaryColor),
                new EmptyBorder(10, 10, 10, 10)
        ));
        leftPanel.setBackground(secondaryColor);

        // Top Info Panel
        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setBackground(secondaryColor);

        leftPanelRoundsLabel = new JLabel("Round 0 / null");
        leftPanelRoundsLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        leftPanelRoundsLabel.setForeground(primaryColor);
        leftPanelRoundsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        leftPanelExtraInformation = new JLabel("Parameters:");
        leftPanelExtraInformation.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        leftPanelExtraInformation.setForeground(primaryColor);
        leftPanelExtraInformation.setAlignmentX(Component.LEFT_ALIGNMENT);

        infoPanel.add(leftPanelRoundsLabel);
        infoPanel.add(Box.createVerticalStrut(10));
        infoPanel.add(leftPanelExtraInformation);

        leftPanel.add(infoPanel, BorderLayout.NORTH);

        // Player List
        DefaultListModel<String> listModel = new DefaultListModel<>();
        listModel.addElement("Empty");
        list = new JList<>(listModel);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setSelectedIndex(0);
        list.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        list.setBackground(secondaryColor);
        list.setForeground(Color.BLACK);
        list.setFixedCellHeight(30);
        list.setBorder(new LineBorder(primaryColor, 1, true));

        JScrollPane listScrollPane = new JScrollPane(list);
        listScrollPane.setBorder(new LineBorder(primaryColor, 1, true));

        leftPanel.add(listScrollPane, BorderLayout.CENTER);

        // Update Players Button
        JButton updatePlayersButton = new JButton("Update Players");
        updatePlayersButton.setFocusPainted(false);
        updatePlayersButton.setBackground(accentColor);
        updatePlayersButton.setForeground(Color.WHITE);
        updatePlayersButton.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        updatePlayersButton.setBorder(new RoundedBorder(10));
        updatePlayersButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        updatePlayersButton.addActionListener(actionEvent -> {
            mainAgent.updatePlayers();
            logLine("Player list updated.");
        });

        JPanel buttonPanel = new JPanel();
        buttonPanel.setBackground(secondaryColor);
        buttonPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 0, 10));
        buttonPanel.add(updatePlayersButton);

        leftPanel.add(buttonPanel, BorderLayout.SOUTH);

        return leftPanel;
    }

    private JPanel createCenterPanel() {
        JPanel centerPanel = new JPanel(new BorderLayout(10, 10));
        centerPanel.setBorder(new CompoundBorder(
                new TitledBorder(new LineBorder(primaryColor, 2, true), "Player Results", TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 16), primaryColor),
                new EmptyBorder(10, 10, 10, 10)
        ));
        centerPanel.setBackground(secondaryColor);

        // Player Results Table
        String[] columns = {"Player", "Score", "Wins", "Losses", "Draws", "Points", "Rank", "Status", "Actions", "Remarks"};
        Object[][] data = new Object[10][10];
        for (int i = 0; i < data.length; i++) {
            data[i] = new Object[]{
                "Player " + (i + 1), "0", "0", "0", "0", "0", "Unranked", "Active", "N/A", "N/A"
            };
        }

        JTable payoffTable = new JTable(data, columns) {
            // Make cells non-editable
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            // Implement cell renderer for better aesthetics
            @Override
            public Component prepareRenderer(javax.swing.table.TableCellRenderer renderer, int row, int column) {
                Component c = super.prepareRenderer(renderer, row, column);
                if (isRowSelected(row)) {
                    c.setBackground(accentColor);
                    c.setForeground(Color.WHITE);
                } else {
                    c.setBackground(Color.WHITE);
                    c.setForeground(Color.BLACK);
                }
                return c;
            }
        };
        payoffTable.setFillsViewportHeight(true);
        payoffTable.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        payoffTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 14));
        payoffTable.getTableHeader().setBackground(primaryColor);
        payoffTable.getTableHeader().setForeground(Color.WHITE);
        payoffTable.getTableHeader().setReorderingAllowed(false);
        payoffTable.setRowHeight(30);
        payoffTable.setSelectionBackground(accentColor);
        payoffTable.setSelectionForeground(Color.WHITE);

        JScrollPane tableScrollPane = new JScrollPane(payoffTable);
        tableScrollPane.setBorder(new LineBorder(primaryColor, 1, true));

        centerPanel.add(tableScrollPane, BorderLayout.CENTER);

        return centerPanel;
    }

    private JPanel createLogPanel() {
        JPanel logPanel = new JPanel(new BorderLayout(10, 10));
        logPanel.setBorder(new CompoundBorder(
                new TitledBorder(new LineBorder(primaryColor, 2, true), "Log", TitledBorder.LEFT, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 16), primaryColor),
                new EmptyBorder(10, 10, 10, 10)
        ));
        logPanel.setBackground(secondaryColor);

        rightPanelLoggingTextArea = new JTextArea(8, 50);
        rightPanelLoggingTextArea.setEditable(false);
        rightPanelLoggingTextArea.setLineWrap(true);
        rightPanelLoggingTextArea.setWrapStyleWord(true);
        rightPanelLoggingTextArea.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        rightPanelLoggingTextArea.setBackground(Color.WHITE);
        rightPanelLoggingTextArea.setForeground(Color.BLACK);
        rightPanelLoggingTextArea.setBorder(new LineBorder(primaryColor, 1, true));

        JScrollPane logScrollPane = new JScrollPane(rightPanelLoggingTextArea);
        logScrollPane.setBorder(new LineBorder(primaryColor, 1, true));

        logPanel.add(logScrollPane, BorderLayout.CENTER);

        return logPanel;
    }

    private JMenuBar createMainMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        menuBar.setBackground(primaryColor);
        menuBar.setForeground(Color.WHITE);

        // File Menu
        JMenu menuFile = new JMenu("File");
        menuFile.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        menuFile.setForeground(Color.WHITE);
        menuFile.setMnemonic(KeyEvent.VK_F);

        JMenuItem newGameFileMenu = new JMenuItem("New Game");
        newGameFileMenu.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        newGameFileMenu.setToolTipText("Start a new game");
        newGameFileMenu.addActionListener(actionEvent -> {
            mainAgent.newGame();
            logLine("New Game initiated via menu.");
        });

        JMenuItem exitFileMenu = new JMenuItem("Exit");
        exitFileMenu.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        exitFileMenu.setToolTipText("Exit application");
        exitFileMenu.setMnemonic(KeyEvent.VK_X);
        exitFileMenu.addActionListener(actionEvent -> {
            logLine("Application exiting.");
            System.exit(0);
        });

        menuFile.add(newGameFileMenu);
        menuFile.addSeparator();
        menuFile.add(exitFileMenu);
        menuBar.add(menuFile);

        // Edit Menu
        JMenu menuEdit = new JMenu("Edit");
        menuEdit.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        menuEdit.setForeground(Color.WHITE);
        menuEdit.setMnemonic(KeyEvent.VK_E);

        JMenuItem resetPlayerEditMenu = new JMenuItem("Reset Players");
        resetPlayerEditMenu.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        resetPlayerEditMenu.setToolTipText("Reset all players");
        resetPlayerEditMenu.setActionCommand("reset_players");
        resetPlayerEditMenu.addActionListener(this);

        JMenuItem parametersEditMenu = new JMenuItem("Parameters");
        parametersEditMenu.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        parametersEditMenu.setToolTipText("Modify the parameters of the game");
        parametersEditMenu.addActionListener(actionEvent -> {
            String params = JOptionPane.showInputDialog(this, "Enter parameters N,S,R,I,P");
            if (params != null && !params.trim().isEmpty()) {
                logLine("Parameters set to: " + params);
            } else {
                logLine("Parameters input canceled or empty.");
            }
        });

        menuEdit.add(resetPlayerEditMenu);
        menuEdit.add(parametersEditMenu);
        menuBar.add(menuEdit);

        // Run Menu
        JMenu menuRun = new JMenu("Run");
        menuRun.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        menuRun.setForeground(Color.WHITE);
        menuRun.setMnemonic(KeyEvent.VK_R);

        JMenuItem newRunMenu = new JMenuItem("New");
        newRunMenu.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        newRunMenu.setToolTipText("Starts a new series of games");
        newRunMenu.addActionListener(this);

        JMenuItem stopRunMenu = new JMenuItem("Stop");
        stopRunMenu.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        stopRunMenu.setToolTipText("Stops the execution of the current round");
        stopRunMenu.addActionListener(this);

        JMenuItem continueRunMenu = new JMenuItem("Continue");
        continueRunMenu.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        continueRunMenu.setToolTipText("Resume the execution");
        continueRunMenu.addActionListener(this);

        JMenuItem roundNumberRunMenu = new JMenuItem("Number of Rounds");
        roundNumberRunMenu.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        roundNumberRunMenu.setToolTipText("Change the number of rounds");
        roundNumberRunMenu.addActionListener(actionEvent -> {
            String rounds = JOptionPane.showInputDialog(this, "How many rounds?");
            if (rounds != null && !rounds.trim().isEmpty()) {
                logLine(rounds + " rounds set.");
            } else {
                logLine("Round number input canceled or empty.");
            }
        });

        menuRun.add(newRunMenu);
        menuRun.add(stopRunMenu);
        menuRun.add(continueRunMenu);
        menuRun.addSeparator();
        menuRun.add(roundNumberRunMenu);
        menuBar.add(menuRun);

        // Window Menu
        JMenu menuWindow = new JMenu("Window");
        menuWindow.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        menuWindow.setForeground(Color.WHITE);
        menuWindow.setMnemonic(KeyEvent.VK_W);

        JCheckBoxMenuItem toggleVerboseWindowMenu = new JCheckBoxMenuItem("Verbose", true);
        toggleVerboseWindowMenu.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        toggleVerboseWindowMenu.setToolTipText("Toggle verbose logging");
        toggleVerboseWindowMenu.addActionListener(actionEvent -> {
            rightPanelLoggingTextArea.setVisible(toggleVerboseWindowMenu.isSelected());
            logLine("Verbose logging " + (toggleVerboseWindowMenu.isSelected() ? "enabled." : "disabled."));
        });

        menuWindow.add(toggleVerboseWindowMenu);
        menuBar.add(menuWindow);

        return menuBar;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Object source = e.getSource();

        if (source instanceof JButton) {
            JButton button = (JButton) source;
            logLine("Button clicked: " + button.getText());
            handleButtonAction(button.getText());
        } else if (source instanceof JMenuItem) {
            JMenuItem menuItem = (JMenuItem) source;
            logLine("Menu item selected: " + menuItem.getText());
            handleMenuAction(menuItem.getText());
        }
    }

    private void handleButtonAction(String action) {
        switch (action) {
            case "Stop":
                logLine("Game stopped.");
                // Implement stop logic here
                break;
            case "Continue":
                logLine("Game continued.");
                // Implement continue logic here
                break;
            default:
                logLine("Unhandled button action: " + action);
        }
    }

    private void handleMenuAction(String action) {
        switch (action) {
            case "New":
            case "New Game":
                mainAgent.newGame();
                logLine("New Game initiated via menu/button.");
                break;
            case "Stop":
                logLine("Game stopped via menu.");
                // Implement stop logic here
                break;
            case "Continue":
                logLine("Game continued via menu.");
                // Implement continue logic here
                break;
            case "Reset Players":
                // Implement reset players logic here
                logLine("Players have been reset.");
                break;
            case "Parameters":
                // Handled in action listener
                break;
            case "Number of Rounds":
                // Handled in action listener
                break;
            case "Exit":
                logLine("Application exiting via menu.");
                System.exit(0);
                break;
            default:
                logLine("Unhandled menu action: " + action);
        }
    }

    // Custom Rounded Border for Buttons
    public class RoundedBorder extends AbstractBorder {
        private int radius;

        RoundedBorder(int radius) {
            this.radius = radius;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setColor(primaryColor);
            g2.setStroke(new BasicStroke(1));
            g2.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(this.radius + 1, this.radius + 1, this.radius + 1, this.radius + 1);
        }

        @Override
        public Insets getBorderInsets(Component c, Insets insets) {
            insets.left = insets.top = insets.right = insets.bottom = this.radius + 1;
            return insets;
        }
    }

    // Custom Logging OutputStream
    public class LoggingOutputStream extends OutputStream {
        private JTextArea textArea;

        public LoggingOutputStream(JTextArea jTextArea) {
            textArea = jTextArea;
        }

        @Override
        public void write(int i) throws IOException {
            SwingUtilities.invokeLater(() -> {
                textArea.append(String.valueOf((char) i));
                textArea.setCaretPosition(textArea.getDocument().getLength());
            });
        }
    }
}
