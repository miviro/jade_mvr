package src.jade_mvr;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.SimpleBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.wrapper.AgentController;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.border.LineBorder;

import com.formdev.flatlaf.FlatLightLaf;

public class MainAgent extends Agent {
    private List<PlayerData> playerDataList = new ArrayList<>();
    private GameManager gameManager;

    private Object[][] getPlayerDataArray() {
        Object[][] dataArray = new Object[playerDataList.size()][gui.columns.length];
        for (int i = 0; i < playerDataList.size(); i++) {
            PlayerData playerData = playerDataList.get(i);
            dataArray[i][0] = playerData.id;
            dataArray[i][1] = playerData.score;
            dataArray[i][2] = playerData.wins;
            dataArray[i][3] = playerData.losses;
            dataArray[i][4] = playerData.draws;
            dataArray[i][5] = playerData.points;
            dataArray[i][6] = "0"; // para rellenar la columna de rank, que se calculara on runtime
            dataArray[i][7] = playerData.status;
            dataArray[i][8] = playerData.actions;
            dataArray[i][9] = playerData.aid;
        }
        return dataArray;
    }

    private class PlayerData {
        int id;
        int score;
        int wins;
        int losses;
        int draws;
        int points;
        PlayerStatus status;
        String actions;
        AID aid;

        public PlayerData(int id, int score, int wins, int losses, int draws, int points, PlayerStatus status,
                String actions, AID aid) {
            this.id = id;
            this.score = score;
            this.wins = wins;
            this.losses = losses;
            this.draws = draws;
            this.points = points;
            this.status = status;
            this.actions = actions;
            this.aid = aid;
        }

        @Override
        public boolean equals(Object o) {
            return aid.equals(o);
        }
    }

    enum PlayerStatus {
        ACTIVE, INACTIVE
    }

    private GUI gui;
    private GameParametersStruct parameters = new GameParametersStruct();

    public GameParametersStruct getParameters() {
        return parameters;
    }

    public void setParameters(int N, int R, int S, int I) {
        parameters.N = N;
        parameters.R = R;
        parameters.S = S;
        parameters.I = I;
    }

    @Override
    protected void setup() {
        // Set FlatLaf Look and Feel before initializing GUI
        try {
            UIManager.setLookAndFeel(new FlatLightLaf());
        } catch (Exception ex) {
            ex.printStackTrace();
            // Optionally, fallback to default Look and Feel
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        gui = new GUI(this);
        System.setOut(new PrintStream(gui.getLoggingOutputStream()));

        updatePlayers();
        gui.logLine("Agent " + getAID().getName() + " is ready.");
    }

    public void runAllRounds() {
        runXRounds(parameters.R);
    }

    // Semaphore fields
    private final Object semaphoreLock = new Object();
    private volatile boolean canProceed = true;

    public void runXRounds(int rounds) {
        // Disable buttons and enable stopButton on EDT
        SwingUtilities.invokeLater(() -> {
            gui.newGameButton.setEnabled(false);
            gui.roundsSpinner.setEnabled(false);
            gui.runAllRoundsButton.setEnabled(false);
            gui.runXRoundsButton.setEnabled(false);
            gui.stopButton.setEnabled(true);
        });

        gui.logLine("Running " + rounds + " rounds");

        // Use SwingWorker for background processing
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                for (int i = 0; i < rounds; i++) {
                    // Check the semaphore before proceeding
                    synchronized (semaphoreLock) {
                        while (!canProceed) {
                            semaphoreLock.wait();
                        }
                    }
                    gui.logLine("Running round " + (i + 1));
                    // Perform the round
                    Thread.sleep(2000);
                }
                return null;
            }

            @Override
            protected void done() {
                gui.logLine("All rounds finished");
                SwingUtilities.invokeLater(() -> {
                    gui.newGameButton.setEnabled(true);
                    gui.roundsSpinner.setEnabled(true);
                    gui.runAllRoundsButton.setEnabled(true);
                    gui.runXRoundsButton.setEnabled(true);
                    gui.stopButton.setEnabled(false);
                });
            }
        };

        worker.execute();
    }

    public void stopGame() {
        gui.logLine("Stopping");
        synchronized (semaphoreLock) {
            canProceed = false;
        }
        SwingUtilities.invokeLater(() -> {
            gui.runAllRoundsButton.setEnabled(true);
            gui.roundsSpinner.setEnabled(true);
            gui.runXRoundsButton.setEnabled(true);
            gui.continueButton.setEnabled(true);
            gui.stopButton.setEnabled(false);
        });
    }

    public void continueGame() {
        gui.logLine("Continuing");
        synchronized (semaphoreLock) {
            canProceed = true;
            semaphoreLock.notifyAll();
        }
        SwingUtilities.invokeLater(() -> {
            gui.runAllRoundsButton.setEnabled(false);
            gui.roundsSpinner.setEnabled(false);
            gui.runXRoundsButton.setEnabled(false);
            gui.continueButton.setEnabled(false);
            gui.stopButton.setEnabled(true);
        });
    }

    public int newGame() {
        SwingUtilities.invokeLater(() -> {
            if (gameManager != null) {
                removeBehaviour(gameManager);
            }
            gui.runAllRoundsButton.setEnabled(true);
            gui.runXRoundsButton.setEnabled(true);

            remakeAgents(); // also deletes them from PLayerLists
            updatePlayers(); // detects agents via jade
            updatePlayers(); // dos veces por magia negra para actualizar la gui

            addAllAgentsToPlayerList();
            repopulateTable();

            // Initialize (inform ID)
            for (PlayerData player : playerDataList) {
                ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                msg.setContent("Id#" + player.id + "#" + parameters.N + "," + parameters.R + "," + parameters.S);
                msg.addReceiver(player.aid);
                send(msg);
            }

            gameManager = new GameManager();
            addBehaviour(gameManager);
        });
        return 0;
    }

    private void remakeAgents() {
        // TODO: simplemente limpiamos la lista en vez de matarlos, no encuentro manera de matarlos limpiamente
        for (PlayerData player : playerDataList) {
            try {
                AgentController agentController = getContainerController().getAgent(player.aid.getLocalName());
                agentController.kill();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        playerDataList.clear();

        for (int i = 0; i < parameters.N; i++) {
            try {
                // UUID para evitar name clashes ya que la parte de arriba no mata los agentes apropiadamente
                getContainerController().createNewAgent("randomAgent#" + UUID.randomUUID().toString(), "src.jade_mvr.RandomAgent", null).start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void addAllAgentsToPlayerList() {
        for (int i = 0; i < parameters.N; i++) {
        }
    }

    private void repopulateTable() {
        gui.centerPanel.removeAll();

        // Create a new JTable with the data
        JTable table = new JTable(getPlayerDataArray(), gui.columns) {
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
                    c.setBackground(gui.accentColor);
                    c.setForeground(Color.WHITE);
                } else {
                    c.setBackground(Color.WHITE);
                    c.setForeground(Color.BLACK);
                }
                return c;
            }
        };

        table.setFillsViewportHeight(true);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 14));
        table.getTableHeader().setBackground(gui.primaryColor);
        table.getTableHeader().setForeground(Color.WHITE);
        table.getTableHeader().setReorderingAllowed(false);
        table.setRowHeight(30);
        table.setSelectionBackground(gui.accentColor);
        table.setSelectionForeground(Color.WHITE);

        // Add the table to a scroll pane
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(new LineBorder(gui.primaryColor, 1, true));

        // Add the scroll pane to the center panel
        gui.centerPanel.add(scrollPane, BorderLayout.CENTER);
        // Revalidate and repaint the center panel to reflect changes
        gui.centerPanel.revalidate();
        gui.centerPanel.repaint();
    }

    public int updatePlayers() {
        playerDataList.clear();
        gui.logLine("Updating player list");
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("Player");
        template.addServices(sd);
        try {
            DFAgentDescription[] result = DFService.search(this, template);
            if (result.length > 0) {
                gui.logLine("Found " + result.length + " players");
            }
            for (int i = 0; i < result.length; ++i) {
                PlayerData playerData = new PlayerData(i, 0, 0, 0, 0, 0, PlayerStatus.ACTIVE, "", result[i].getName());
                playerDataList.add(playerData);
            }
        } catch (FIPAException fe) {
            gui.logLine(fe.getMessage());
        }

        String[] playerNames = playerDataList.stream()
                .map(player -> player.aid.getName())
                .toArray(String[]::new);
        gui.setPlayersUI(playerNames);
        return 0;
    }

    /**
     * In this behavior this agent manages the course of a match during all the
     * rounds.
     */
    private class GameManager extends SimpleBehaviour {

        @Override
        public void action() {
            /*
             * // Assign the IDs
             * // TODO: mover a newGame()
             * ArrayList<PlayerInformation> players = new ArrayList<>();
             * int lastId = 0;
             * for (AID a : playerAgents) {
             * players.add(new PlayerInformation(a, lastId++));
             * }
             * 
             * // Initialize (inform ID)
             * for (PlayerInformation player : players) {
             * ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
             * msg.setContent("Id#" + player.id + "#" + parameters.N + "," + parameters.R +
             * "," + parameters.S);
             * msg.addReceiver(player.aid);
             * send(msg);
             * }
             * // Organize the matches
             * for (int i = 0; i < players.size(); i++) {
             * for (int j = i + 1; j < players.size(); j++) { // too lazy to think, let's
             * see if it works or it breaks
             * playGame(players.get(i), players.get(j));
             * }
             * }
             */
            gui.logLine("GameManager action");
        }

        private void playGame(PlayerData player1, PlayerData player2) {
            /*
             * // Assuming player1.id < player2.id
             * ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
             * msg.addReceiver(player1.aid);
             * msg.addReceiver(player2.aid);
             * msg.setContent("NewGame#" + player1.id + "," + player2.id);
             * send(msg);
             * 
             * int pos1, pos2;
             * 
             * msg = new ACLMessage(ACLMessage.REQUEST);
             * msg.setContent("Position");
             * msg.addReceiver(player1.aid);
             * send(msg);
             * 
             * gui.logLine("Main Waiting for movement");
             * MessageTemplate mt =
             * MessageTemplate.not(MessageTemplate.MatchPerformative(ACLMessage.
             * ACCEPT_PROPOSAL));
             * ACLMessage move1 = blockingReceive(mt);
             * gui.logLine("Main Received " + move1.getContent() + " from " +
             * move1.getSender().getName());
             * pos1 = Integer.parseInt(move1.getContent().split("#")[1]);
             * 
             * msg = new ACLMessage(ACLMessage.REQUEST);
             * msg.setContent("Position");
             * msg.addReceiver(player2.aid);
             * send(msg);
             * 
             * gui.logLine("Main Waiting for movement");
             * ACLMessage move2 = blockingReceive();
             * gui.logLine("Main Received " + move1.getContent() + " from " +
             * move1.getSender().getName());
             * pos2 = Integer.parseInt(move1.getContent().split("#")[1]);
             * 
             * msg = new ACLMessage(ACLMessage.INFORM);
             * msg.addReceiver(player1.aid);
             * msg.addReceiver(player2.aid);
             * msg.setContent("Results#1#1");
             * send(msg);
             * msg.setContent("EndGame");
             * send(msg);
             */
        }

        @Override
        public boolean done() {
            return true;
        }
    }

    public class GameParametersStruct {

        int N;
        int R;
        int S;
        int I;

        public GameParametersStruct() { // TODO: set default R=500, I=1
            N = 2;
            R = 50;
            S = 4;
            I = 0;
        }

        @Override
        public String toString() {
            return "N=" + N +
                    ", S=" + S +
                    ", R=" + R +
                    ", I=" + I;
        }
    }
}
