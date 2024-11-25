package src.jade_mvr;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.SimpleBehaviour;
import jade.wrapper.ControllerException;
import jade.wrapper.StaleProxyException;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;

import java.util.ArrayList;
import java.util.HashMap;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import java.awt.Component;
import java.io.File;
import java.lang.reflect.InvocationTargetException;

public class MainAgent extends Agent {
    private static GUI view;
    private static GameParametersStruct gameParameters = new GameParametersStruct();;
    private static boolean verbose = false;
    private static ArrayList<String> agentTypesList = new ArrayList<String>();
    private static ArrayList<PlayerInformation> playerAgents = new ArrayList<PlayerInformation>();
    public static Object roundLock = new Object();
    private static int currentRound = 0;
    private int stopAtRound = 0;
    private int agendUniqueId = 0;

    private Thread gameThread;
    private volatile boolean gameRunning = false;

    private static float getIndexValue(int currentRound) {
        return (float) 10.00;
    }

    private static float getInflationRate(int currentRound) {
        return (float) 1 / 100;
    }

    @Override
    protected void setup() {

    }

    public int updatePlayers() {
        view.appendLog("Updating player list", false);
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("Player");
        template.addServices(sd);
        try {
            DFAgentDescription[] result = DFService.search(this, template);
            if (result.length > 0) {
                view.appendLog("Found " + result.length + " players", false);
            }
            // playerAgents = new AID[result.length];
            for (int i = 0; i < result.length; ++i) {
                // playerAgents[i] = result[i].getName();
            }
        } catch (FIPAException fe) {
            view.appendLog(fe.getMessage(), true);
        }

        return 0;
    }

    public MainAgent() {
        setup();
        addBehaviour(new GameManager());
    }

    private void startNewGame() {
        currentRound = 0;
        stopAtRound = 0;
        int totalAgents = getTotalAgents();

        if (totalAgents != MainAgent.getGameParameters().N) {
            view.appendLog("The sum of agents (" + totalAgents + ") does not match the total number of players ("
                    + MainAgent.getGameParameters().N + ").", false);
            return;
        }

        // grafico
        view.newGameButton.setEnabled(false);
        view.quitGameButton.setEnabled(true);
        view.resetStatsButton.setEnabled(true);
        view.stopButton.setEnabled(false);
        view.playAllRoundsButton.setEnabled(true);
        view.playXRoundsButton.setEnabled(true);
        view.playXRoundsSpinner.setEnabled(true);
        view.setPanelEnabled(view.configPanel, false);

        view.appendLog("Starting new game", false);

        // Añadir jugadores a la tabla de estadísticas
        createPlayersAndAddToTable();
        sendConfigToPlayers();

        gameRunning = true;

        gameThread = new Thread(() -> {
            while (gameRunning) {

                for (int i = 0; i < MainAgent.getGameParameters().R; i++) {
                    // Check if we need to pause
                    synchronized (MainAgent.roundLock) {
                        // We have executed all requested rounds
                        if (stopAtRound <= currentRound) {
                            try {
                                SwingUtilities.invokeAndWait(() -> {
                                    view.newGameButton.setEnabled(false);
                                    view.quitGameButton.setEnabled(true);
                                    view.resetStatsButton.setEnabled(true);
                                    view.stopButton.setEnabled(false);
                                    view.playAllRoundsButton.setEnabled(true);
                                    view.playXRoundsButton.setEnabled(true);
                                    view.playXRoundsSpinner.setEnabled(true);
                                });
                            } catch (InvocationTargetException e) {
                                e.printStackTrace();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            try {
                                roundLock.wait(); // Wait without blocking the GUI thread
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                SwingUtilities.invokeLater(() -> {
                                    view.appendLog("Thread interrupted: " + e.getMessage(), true);
                                });
                            }
                        }
                    }
                    // si estamos aqui, tenemos rondas por ejecutar
                    view.newGameButton.setEnabled(false);
                    view.quitGameButton.setEnabled(false);
                    view.resetStatsButton.setEnabled(false);
                    view.stopButton.setEnabled(true);
                    // view.continueButton.setEnabled(false);
                    view.playAllRoundsButton.setEnabled(false);
                    view.playXRoundsButton.setEnabled(false);
                    view.playXRoundsSpinner.setEnabled(false);

                    playRound();
                    processRoundOver();
                    // Reset current round money for all players
                    for (PlayerInformation player : playerAgents) {
                        player.setCurrentRoundMoney(0);
                    }
                }
                processGameOver();

                gameRunning = false;

                // si estamos aqui, hemos terminado todas las rondas
                view.newGameButton.setEnabled(false);
                view.quitGameButton.setEnabled(true);
                view.resetStatsButton.setEnabled(true);
                view.stopButton.setEnabled(false);
                // view.continueButton.setEnabled(false);
                view.playAllRoundsButton.setEnabled(false);
                view.playXRoundsButton.setEnabled(false);
                view.playXRoundsSpinner.setEnabled(false);

                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
            }
        });
        gameThread.start();
    }

    private void playXRounds(int rounds) {
        if (rounds <= 0) {
            view.appendLog("Invalid number of rounds: " + rounds, true);
            return;
        }
        if (currentRound + rounds > getGameParameters().R) {
            setStopAtRound(getGameParameters().R);
        } else {
            setStopAtRound(currentRound + rounds);
        }
    }

    private void setStopAtRound(int round) {
        synchronized (roundLock) {
            stopAtRound = round;
            roundLock.notifyAll(); // Notify all waiting threads
        }
    }

    private void pauseGame() {
        setStopAtRound(currentRound);
    }

    private void processGameOver() {
        for (PlayerInformation player : playerAgents) {
            float assetsValue = player.getAssets() * getIndexValue(currentRound);
            float totalPayoff = player.getMoney() + assetsValue;
            float fee = assetsValue * ((float) gameParameters.S / 100);
            totalPayoff -= fee;
            player.setMoney(totalPayoff);

            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            msg.setContent("GameOver#" + player.id + "#" + totalPayoff);
            msg.addReceiver(player.aid);
            send(msg);
        }
        view.appendLog("Game ended.", false);
    }

    private void processRoundOver() {
        // SEND REQUEST RoundOver#[player id]#[total player round payoff]#[total player
        // round money adjusted for inflation]#[inflation rate, decimal]#[total player
        // owned assets]#[asset individual price] to all players
        for (PlayerInformation player : playerAgents) {
            ACLMessage roundOverMsg = new ACLMessage(ACLMessage.REQUEST);
            float totalRoundPayoff = player.getCurrentRoundMoney();
            float totalMoney = player.getMoney();
            float totalAssets = player.getAssets();
            float assetPrice = getIndexValue(currentRound);
            String content = "RoundOver#" + player.id + "#" + totalRoundPayoff + "#" + totalMoney + "#"
                    + getInflationRate(currentRound) + "#" + totalAssets + "#" + assetPrice;
            view.appendLog(content, true);
            roundOverMsg.setContent(content);
            roundOverMsg.addReceiver(player.aid);
            send(roundOverMsg);
        }

        // Receive buy/sell orders from players
        java.util.Map<AID, ACLMessage> receivedMessages = new HashMap<>();

        while (receivedMessages.size() < playerAgents.size()) {
            ACLMessage msg = blockingReceive();
            if (msg != null && msg.getPerformative() == ACLMessage.INFORM) {
                AID sender = msg.getSender();
                if (!receivedMessages.containsKey(sender)) {
                    receivedMessages.put(sender, msg);
                    String content = msg.getContent();
                    String[] parts = content.split("#");
                    System.out.println("Received message: " + content);
                    if (parts.length == 2) {
                        String action = parts[0];
                        float amount;
                        try {
                            amount = Float.parseFloat(parts[1]);
                            PlayerInformation player = null;
                            for (PlayerInformation p : playerAgents) {
                                if (p.aid.equals(sender)) {
                                    player = p;
                                    break;
                                }
                            }
                            if (player != null) {
                                if ("Buy".equalsIgnoreCase(action)) {
                                    if (player.getMoney() >= (amount * getIndexValue(currentRound))) {
                                        player.setMoney(player.getMoney() - (amount * getIndexValue(currentRound)));
                                        player.setAssets(player.getAssets() + amount);
                                        view.appendLog("Player " + player.id + " bought " + amount + " assets ", true);
                                    } else {
                                        view.appendLog("Player " + player.id + " tried to buy more than they have.",
                                                true);
                                    }
                                } else if ("Sell".equalsIgnoreCase(action)) {
                                    if (player.getAssets() >= amount) {
                                        float assetValue = amount * getIndexValue(currentRound);
                                        float fee = assetValue * ((float) getGameParameters().S / 100);
                                        player.setAssets(player.getAssets() - amount);
                                        player.setMoney(player.getMoney() + assetValue - fee);
                                        view.appendLog("Player " + player.id + " sold assets worth " + assetValue,
                                                true);
                                    } else {
                                        view.appendLog("Player " + player.id + " tried to sell more than they have.",
                                                true);
                                    }
                                } else {
                                    view.appendLog("Invalid action '" + action + "' from player " + player.id, true);
                                }
                            } else {
                                view.appendLog("Player not found for message from " + sender.getLocalName(), true);
                            }
                        } catch (NumberFormatException e) {
                            view.appendLog("Invalid amount from player " + sender.getLocalName(), true);
                        }
                    } else {
                        view.appendLog("Invalid message format from player " + sender.getLocalName(), true);
                    }
                } else {
                    view.appendLog("Duplicate message from player " + sender.getLocalName(), true);
                }
            }
        }

        // Send Accounting message to each player
        for (PlayerInformation player : playerAgents) {
            ACLMessage accountingMsg = new ACLMessage(ACLMessage.INFORM);
            String content = String.format("Accounting#%d#%.2f#%.2f", player.id, player.getMoney(), player.getAssets());
            accountingMsg.setContent(content);
            accountingMsg.addReceiver(player.aid);
            send(accountingMsg);
        }

        // Apply inflation to each player's money
        for (PlayerInformation player : playerAgents) {
            float moneyWithInflation = player.getMoney() * (1 - getInflationRate(currentRound));
            player.setMoney(moneyWithInflation);
        }
    }

    private void playRound() {
        for (int i = 0; i < playerAgents.size(); i++) {
            for (int j = i + 1; j < playerAgents.size(); j++) {
                PlayerInformation player1 = playerAgents.get(i);
                PlayerInformation player2 = playerAgents.get(j);

                GameInfo gameInfo = playGame(player1, player2);

                if (gameInfo.player1Reward > gameInfo.player2Reward) {
                    player1.setWins(player1.getWins() + 1);
                    player2.setLosses(player2.getLosses() + 1);
                } else if (gameInfo.player1Reward < gameInfo.player2Reward) {
                    player1.setLosses(player1.getLosses() + 1);
                    player2.setWins(player2.getWins() + 1);
                } else {
                    player1.setDraws(player1.getDraws() + 1);
                    player2.setDraws(player2.getDraws() + 1);
                }

                player1.addLastActionList(gameInfo.resultContent);
                player2.addLastActionList(gameInfo.resultContent);
                view.appendLog(gameInfo.resultContent, true);

                player1.setMoney(player1.getMoney() + gameInfo.player1Reward);
                player2.setMoney(player2.getMoney() + gameInfo.player2Reward);

                player1.setCurrentRoundMoney(gameInfo.player1Reward);
                player2.setCurrentRoundMoney(gameInfo.player2Reward);

                player1.addLastAction(gameInfo.player1Action);
                player2.addLastAction(gameInfo.player2Action);
            }
        }
        // EVERY ROUND: Update the table with new values
        for (int k = 0; k < view.statsTableModel.getRowCount(); k++) {
            PlayerInformation player = playerAgents.get(k);
            view.statsTableModel.setValueAt(player.getWins(), k, 1); // Wins
            view.statsTableModel.setValueAt(player.getLosses(), k, 2); // Losses
            view.statsTableModel.setValueAt(player.getDraws(), k, 3); // Draws
            view.statsTableModel.setValueAt(player.getMoney(), k, 4); // Money
            view.statsTableModel.setValueAt(player.getAssets(), k, 5); // Assets
            view.statsTableModel.setValueAt(player.getLastActions(), k, 6); // LAST ACTIONS
        }

        // Sort players by total value and get the winner
        ArrayList<PlayerInformation> sortedPlayers = new ArrayList<>(playerAgents);
        sortedPlayers.sort((a, b) -> b.compareTo(a));
        // añadir 1 a currentRound, mostrar +1 para que empiece en 1
        view.verboseLabel.setText("Round " + (currentRound++ + 1) + " / " + getGameParameters().R
                + ", index value: " + getIndexValue(currentRound) + ", inflation rate: "
                + getInflationRate(currentRound) + ", current winner: " + sortedPlayers.get(0).aid.getLocalName());

        view.statsTableModel.fireTableDataChanged();
    }

    private GameInfo playGame(PlayerInformation player1, PlayerInformation player2) {
        // Send INFORM NewGame#[player1.id]#[player2.id] to both players
        ACLMessage newGameMsg = new ACLMessage(ACLMessage.INFORM);
        newGameMsg.setContent("NewGame#" + player1.id + "#" + player2.id);
        newGameMsg.addReceiver(player1.aid);
        newGameMsg.addReceiver(player2.aid);
        send(newGameMsg);

        // Send REQUEST Action to both players
        ACLMessage actionRequest = new ACLMessage(ACLMessage.REQUEST);
        actionRequest.setContent("Action");
        actionRequest.addReceiver(player1.aid);
        actionRequest.addReceiver(player2.aid);
        send(actionRequest);

        // Wait for both players to send INFORM Action#[action]
        String player1Action = null;
        String player2Action = null;
        while (player1Action == null || player2Action == null) {
            ACLMessage msg = blockingReceive(); // da error pero funciona
            if (msg != null) {
                String[] content = msg.getContent().split("#");
                if (content[0].equals("Action") && content.length > 1) {
                    String action = content[1];
                    if (msg.getSender().equals(player1.aid)) {
                        player1Action = action;
                    } else if (msg.getSender().equals(player2.aid)) {
                        player2Action = action;
                    }
                }
            }
        }

        // Resolve game
        int player1Reward = 0;
        int player2Reward = 0;

        // Example game logic
        if (player1Action.equals("C") && player2Action.equals("C")) {
            player1Reward = 2;
            player2Reward = 2;
        } else if (player1Action.equals("C") && player2Action.equals("D")) {
            player1Reward = 0;
            player2Reward = 4;
        } else if (player1Action.equals("D") && player2Action.equals("C")) {
            player1Reward = 4;
            player2Reward = 0;
        } else if (player1Action.equals("D") && player2Action.equals("D")) {
            player1Reward = 0;
            player2Reward = 0;
        }

        // Send INFORM
        // Results#[player1.id],[player2.id]#[player1Action],[player2Action]#[player1Reward],[player2Reward]
        ACLMessage resultsMsg = new ACLMessage(ACLMessage.INFORM);
        String resultContent = "Results#" + player1.id + "," + player2.id + "#" + player1Action + "," + player2Action
                + "#" + player1Reward + "," + player2Reward;
        resultsMsg.setContent(resultContent);
        resultsMsg.addReceiver(player1.aid);
        resultsMsg.addReceiver(player2.aid);
        send(resultsMsg);
        return new GameInfo(player1Action, player2Action, player1Reward, player2Reward,
                "Round: " + currentRound + ": " + resultContent);

    }

    private class GameInfo {

        String player1Action;
        String player2Action;
        int player1Reward;
        int player2Reward;
        String resultContent;

        public GameInfo(String p1a, String p2a, int p1r, int p2r, String rc) {
            player1Action = p1a;
            player2Action = p2a;
            player1Reward = p1r;
            player2Reward = p2r;
            resultContent = rc;
        }
    }

    private void sendConfigToPlayers() {
        for (PlayerInformation player : playerAgents) {
            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            String content = "Id#" + player.id + "#" + gameParameters.N + "," + gameParameters.R + ","
                    + (float) gameParameters.S / 100; // convertir S a decimas
            msg.setContent(content);
            view.appendLog(content, true);
            msg.addReceiver(player.aid);
            send(msg);
        }
    }

    private void createPlayersAndAddToTable() {
        Object[][] data = new Object[getGameParameters().N][8];
        int agentIndex = 0;

        for (Component component : view.knownAgentsPanel.getComponents()) {
            if (component instanceof JPanel) {
                JPanel agentPanel = (JPanel) component;
                String agentType = null;
                int agentCount = 0;

                for (Component subComponent : agentPanel.getComponents()) {
                    if (subComponent instanceof JSpinner) {
                        agentCount = (Integer) ((JSpinner) subComponent).getValue();
                    } else if (subComponent instanceof JLabel) {
                        agentType = ((JLabel) subComponent).getText();
                    }
                }

                if (agentType != null && agentCount > 0) {
                    for (int i = 0; i < agentCount; i++) {
                        data[agentIndex][0] = agentType + agentIndex + "." + agendUniqueId;
                        data[agentIndex][1] = 0; // Wins
                        data[agentIndex][2] = 0; // Lose
                        data[agentIndex][3] = 0; // Draw
                        data[agentIndex][4] = 0; // Money
                        data[agentIndex][5] = 0; // Invested
                        data[agentIndex][6] = ""; // Last Actions
                        data[agentIndex][7] = "Delete"; // Delete button

                        try {
                            getContainerController()
                                    .createNewAgent(agentType + agentIndex + "." + agendUniqueId,
                                            "src.agents." + agentType,
                                            null)
                                    .start();
                            // unique ID para evitar colisiones en los mensajes mandados a agentes que se
                            // borran antes de tiempo

                            playerAgents.add(
                                    new PlayerInformation(
                                            new AID(agentType + agentIndex + "." + agendUniqueId++, AID.ISLOCALNAME),
                                            agentIndex));
                            agentIndex++;
                        } catch (Exception e) {
                            view.appendLog("Could not create agent " + agentType + ": " + e.getMessage(), true);
                        }
                    }
                }
            }
        }

        view.updateStatsTable(data);
    }

    private int getTotalAgents() {
        int totalAgents = 0;
        for (Component component : view.knownAgentsPanel.getComponents()) {
            if (component instanceof JPanel) {
                JPanel agentPanel = (JPanel) component;
                for (Component subComponent : agentPanel.getComponents()) {
                    if (subComponent instanceof JSpinner) {
                        totalAgents += (Integer) ((JSpinner) subComponent).getValue();
                    }
                }
            }
        }
        return totalAgents;
    }

    public void deleteAgent(String agentName) {
        // Send GameOver message
        PlayerInformation playerDeleted = null;

        for (PlayerInformation p : playerAgents) {
            if (p.aid.getLocalName().equals(agentName)) {
                playerDeleted = p;
                break;
            }
        }

        float assetsValue = playerDeleted.getAssets() * getIndexValue(currentRound);
        float totalPayoff = playerDeleted.getMoney() + assetsValue;
        float fee = assetsValue * ((float) gameParameters.S / 100);
        totalPayoff -= fee;
        playerDeleted.setMoney(totalPayoff);

        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.setContent("GameOver#" + playerDeleted.id + "#" + totalPayoff);
        msg.addReceiver(playerDeleted.aid);
        send(msg);

        // que se maten ellos solos, pasamos a ignorarlos solamente
        /*try {
            getContainerController().getAgent(agentName).kill();
        } catch (StaleProxyException e) {
            view.appendLog("Could not kill agent " + agentName + ": " + e.getMessage(), true);
        } catch (ControllerException e) {
            view.appendLog("Could not kill agent " + agentName + ": " + e.getMessage(), true);
            e.printStackTrace();
        }*/
        playerAgents.removeIf(player -> player.aid.getLocalName().equals(agentName));
        // Rebuild the table with the updated playerAgents list
        Object[][] data = new Object[playerAgents.size()][8];
        for (int i = 0; i < playerAgents.size(); i++) {
            PlayerInformation player = playerAgents.get(i);
            data[i][0] = player.aid.getLocalName();
            data[i][1] = player.getWins();
            data[i][2] = player.getLosses();
            data[i][3] = player.getDraws();
            data[i][4] = player.getMoney();
            data[i][5] = player.getAssets();
            data[i][6] = player.getLastActions();
            data[i][7] = "Delete";
        }
        view.updateStatsTable(data);
    }

    private void quitGame() {
        gameRunning = false;

        if (gameThread != null && gameThread.isAlive()) {
            gameThread.interrupt();
            gameThread = null;
        }

        processGameOver(); // para que no peten los mensajes
        currentRound = 0;
        stopAtRound = 0;
        // grafico

        view.newGameButton.setEnabled(true);
        view.quitGameButton.setEnabled(false);
        view.resetStatsButton.setEnabled(false);
        view.stopButton.setEnabled(false);
        // view.continueButton.setEnabled(false);
        view.playAllRoundsButton.setEnabled(false);
        view.playXRoundsButton.setEnabled(false);
        view.playXRoundsSpinner.setEnabled(false);
        view.verboseLabel.setText("Round 0 / null, index value: null , inflation rate: null");

        view.setPanelEnabled(view.configPanel, true);

        // Remove all entries from the table
        view.updateStatsTable(new Object[0][8]);

        // TODO: Kill all agents
        /*DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("Player");
        template.addServices(sd);
        try {
            DFAgentDescription[] result = DFService.search(this, template);
            for (DFAgentDescription agentDesc : result) {
                AID agentID = agentDesc.getName();
                try {
                    getContainerController().getAgent(agentID.getLocalName()).kill();
                } catch (StaleProxyException e) {
                    view.appendLog("Could not kill agent " + agentID.getLocalName() + ": " + e.getMessage(), true);
                } catch (ControllerException e) {
                    view.appendLog("Could not kill agent " + agentID.getLocalName() + ": " + e.getMessage(), true);
                    e.printStackTrace();
                }
            }
        } catch (FIPAException fe) {
            view.appendLog(fe.getMessage(), true);
        }*/
        playerAgents.clear();
        view.stopButton.setEnabled(false); // por algun motivo se queda activado, desactivar otra vez
        view.newGameButton.setEnabled(true); // idem
    }

    private void resetStats() {
        // grafico
        view.appendLog("Stats reset", false);

        // Reset stats in the table model
        for (int i = 0; i < view.statsTableModel.getRowCount(); i++) {
            view.statsTableModel.setValueAt(0, i, 1); // Wins
            view.statsTableModel.setValueAt(0, i, 2); // Losses
            view.statsTableModel.setValueAt(0, i, 3); // Draws
            view.statsTableModel.setValueAt(0, i, 4); // Money
            view.statsTableModel.setValueAt(0, i, 5); // Invested
            view.statsTableModel.setValueAt("", i, 6); // Actions
        }
        view.statsTableModel.fireTableDataChanged();

        // Reset stats in the playerAgents list
        for (PlayerInformation player : playerAgents) {
            player.setWins(0);
            player.setLosses(0);
            player.setDraws(0);
            player.setMoney(0);
            player.setAssets(0);
            player.resetLastActions();
        }
    }

    private class GameManager extends SimpleBehaviour {

        @Override
        public void action() {
            System.out.println("GameManager");
            initAgentTypesList();
            view = new GUI();
            view.setVisible(true);
            view.newGameButton.addActionListener(e -> startNewGame());
            view.quitGameButton.addActionListener(e -> quitGame());
            view.resetStatsButton.addActionListener(e -> resetStats());
            view.table.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    int row = view.table.rowAtPoint(e.getPoint());
                    int col = view.table.columnAtPoint(e.getPoint());
                    if (row >= 0 && col >= 0) {
                        // delete
                        if (col == 7) {
                            String agentName = (String) view.statsTableModel.getValueAt(row, 0);
                            deleteAgent(agentName);
                        }
                        // last actions
                        else if (col == 6) {
                            PlayerInformation player = playerAgents.get(row);
                            ArrayList<String> lastActionsList = player.getLastActionsList();
                            if (lastActionsList.size() > 0) {
                                JTextArea textArea = new JTextArea();
                                JScrollPane scrollPane = new JScrollPane(textArea);
                                textArea.setEditable(false);
                                for (String action : lastActionsList) {
                                    textArea.append(action + "\n");
                                }

                                JFrame frame = new JFrame("Action History for " + player.aid.getLocalName());
                                frame.setSize(300, 200);
                                frame.add(scrollPane);
                                frame.setLocationRelativeTo(null);
                                frame.setVisible(true);
                            }
                        }
                    }
                }
            });

            view.stopButton.addActionListener(e -> pauseGame());
            // view.continueButton.addActionListener(e -> setStopAtRound(currentRound + 1));
            view.playAllRoundsButton.addActionListener(e -> playXRounds(getGameParameters().R));
            view.playXRoundsButton.addActionListener(e -> playXRounds((Integer) view.playXRoundsSpinner.getValue()));

            updatePlayers();
            view.appendLog("Application started", false);
        }

        @Override
        public boolean done() {
            return true;
        }
    }

    public static ArrayList<String> getAgentTypesList() {
        return agentTypesList;
    }

    public static boolean getVerbose() {
        return verbose;
    }

    public static void setVerbose(boolean verbose) {
        MainAgent.verbose = verbose;
        view.appendLog("Verbose set to: " + verbose, false);
    }

    public static GameParametersStruct getGameParameters() {
        return gameParameters;
    }

    public static void setGameParameters(GameParametersStruct gameParameters) {
        MainAgent.gameParameters = gameParameters;
        view.appendLog("Parameters set to: N=" + gameParameters.N + ", S=" + gameParameters.S + ", R="
                + gameParameters.R, true);
    }

    public static void main(String[] args) {
        new MainAgent();
    }

    private static void initAgentTypesList() {
        agentTypesList.clear();
        playerAgents.clear();
        File agentsDir = new File("src/agents");
        if (agentsDir.exists() && agentsDir.isDirectory()) {
            File[] files = agentsDir.listFiles((dir, name) -> name.endsWith(".java"));
            if (files != null) {
                for (File file : files) {
                    String fileName = file.getName();
                    if (fileName.endsWith(".java")) {
                        agentTypesList.add(fileName.substring(0, fileName.length() - 5));
                    }
                }
            }
        }
    }

    public static class GameParametersStruct {
        int N;
        int R;
        int S;

        public GameParametersStruct() {
            N = 2;
            R = 500;
            S = 4;
        }

        public GameParametersStruct(int n, int r, int s) {
            N = n;
            R = r;
            S = s;
        }

        @Override
        public String toString() {
            return "N=" + N +
                    ", R=" + R +
                    ", S=" + S;
        }
    }

    public static class PlayerInformation implements Comparable<PlayerInformation> {
        AID aid;
        int id;
        private int wins;
        private int losses;
        private int draws;
        private float money;
        private float currentRoundMoney;

        private float assets;
        private String lastActions = "";
        private ArrayList<String> lastActionsList = new ArrayList<String>();

        public float getCurrentRoundMoney() {
            return currentRoundMoney;
        }

        public void setCurrentRoundMoney(float currentRoundMoney) {
            this.currentRoundMoney = currentRoundMoney;
        }

        public ArrayList<String> getLastActionsList() {
            return lastActionsList;
        }

        public void setLastActionsList(ArrayList<String> lastActionsList) {
            this.lastActionsList = lastActionsList;
        }

        public void addLastActionList(String action) {
            this.lastActionsList.add(action);
        }

        public String getLastActions() {
            return lastActions;
        }

        public void resetLastActions() {
            this.lastActions = "";

        }

        // Keep last 5 actions
        public void addLastAction(String action) {
            this.lastActions = action + this.lastActions;
            if (this.lastActions.length() > 5) {
                this.lastActions = this.lastActions.substring(0, 5);
            }
        }

        public int getWins() {

            return wins;
        }

        public void setWins(int wins) {
            this.wins = wins;
        }

        public int getLosses() {
            return losses;
        }

        public void setLosses(int losses) {
            this.losses = losses;
        }

        public int getDraws() {
            return draws;
        }

        public void setDraws(int draws) {
            this.draws = draws;
        }

        public float getMoney() {
            return money;
        }

        public void setMoney(float money) {
            this.money = (float) (Math.round(money * 100.0) / 100.0); // dos decimales
        }

        public float getAssets() {
            return assets;
        }

        public void setAssets(float assets) {
            this.assets = (float) (Math.round(assets * 100.0) / 100.0); // dos decimales
        }

        public PlayerInformation(AID a, int i) {
            aid = a;
            id = i;
        }

        @Override
        public boolean equals(Object o) {
            return aid.equals(o);
        }

        @Override
        public int compareTo(PlayerInformation other) {
            float thisTotal = this.money
                    + (this.assets * getIndexValue(currentRound) * (1 - (float) gameParameters.S / 100));
            float otherTotal = other.money
                    + (other.assets * getIndexValue(currentRound) * (1 - (float) gameParameters.S / 100));
            return Float.compare(thisTotal, otherTotal);
        }
    }
}
