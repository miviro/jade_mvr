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

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;

import java.awt.Component;
import java.io.File;

public class MainAgent extends Agent {
    private static GUI view;
    private static GameParametersStruct gameParameters = new GameParametersStruct();;
    private static boolean verbose = false;
    private static ArrayList<String> agentTypesList = new ArrayList<String>();
    private static ArrayList<PlayerInformation> playerAgents = new ArrayList<PlayerInformation>();

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
        view.continueButton.setEnabled(false);
        view.playAllRoundsButton.setEnabled(true);
        view.playXRoundsButton.setEnabled(true);
        view.playXRoundsSpinner.setEnabled(true);
        view.setPanelEnabled(view.configPanel, false);

        view.appendLog("Starting new game", false);

        // Añadir jugadores a la tabla de estadísticas
        createPlayersAndAddToTable();
        sendConfigToPlayers();
        // for (int i = 0; i < MainAgent.getGameParameters().R; i++) {
        playRound();
        // }
    }

    private void playRound() {
        view.appendLog("Playing round", false);
        for (int i = 0; i < playerAgents.size(); i++) {
            for (int j = i + 1; j < playerAgents.size(); j++) {
                playGame(playerAgents.get(i), playerAgents.get(j));
            }
        }
    }

    private void playGame(PlayerInformation player1, PlayerInformation player2) {
        // Assuming player1.id < player2.id
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.addReceiver(player1.aid);
        msg.addReceiver(player2.aid);
        msg.setContent("NewGame#" + player1.id + "#" + player2.id);
        send(msg);

        int pos1, pos2;

        msg = new ACLMessage(ACLMessage.REQUEST);
        msg.setContent("Action");
        msg.addReceiver(player1.aid);
        send(msg);

        view.appendLog("Main Waiting for movement", true);
        ACLMessage move1 = blockingReceive();
        view.appendLog("Main Received " + move1.getContent() + " from " + move1.getSender().getName(), true);
        pos1 = Integer.parseInt(move1.getContent().split("#")[1]);

        msg = new ACLMessage(ACLMessage.REQUEST);
        msg.setContent("Action");
        msg.addReceiver(player2.aid);
        send(msg);

        view.appendLog("Main Waiting for movement", true);
        ACLMessage move2 = blockingReceive();
        view.appendLog("Main Received " + move1.getContent() + " from " + move1.getSender().getName(), true);
        pos2 = Integer.parseInt(move1.getContent().split("#")[1]);

        msg = new ACLMessage(ACLMessage.INFORM);
        msg.addReceiver(player1.aid);
        msg.addReceiver(player2.aid);
        msg.setContent("Results#1#1");
        send(msg);
        msg.setContent("EndGame");
        send(msg);
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
                        data[agentIndex][0] = agentType + agentIndex;
                        data[agentIndex][1] = 0; // Wins
                        data[agentIndex][2] = 0; // Lose
                        data[agentIndex][3] = 0; // Draw
                        data[agentIndex][4] = 0; // Points
                        data[agentIndex][5] = 0; // Invested
                        data[agentIndex][6] = ""; // Last Actions
                        data[agentIndex][7] = "Delete"; // Delete button

                        try {
                            getContainerController()
                                    .createNewAgent(agentType + agentIndex, "src.agents." + agentType, null).start();
                        } catch (Exception e) {
                            view.appendLog("Could not create agent " + agentType + ": " + e.getMessage(), true);
                        }
                        playerAgents.add(
                                new PlayerInformation(new AID(agentType + agentIndex, AID.ISLOCALNAME), agentIndex));
                        agentIndex++;
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

    private void quitGame() {
        // grafico
        view.appendLog("Game finished", false);

        view.newGameButton.setEnabled(true);
        view.quitGameButton.setEnabled(false);
        view.resetStatsButton.setEnabled(false);
        view.stopButton.setEnabled(false);
        view.continueButton.setEnabled(false);
        view.playAllRoundsButton.setEnabled(false);
        view.playXRoundsButton.setEnabled(false);
        view.playXRoundsSpinner.setEnabled(false);

        view.setPanelEnabled(view.configPanel, true);

        // Remove all entries from the table
        view.updateStatsTable(new Object[0][8]);

        // Kill all agents
        DFAgentDescription template = new DFAgentDescription();
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
        }
        playerAgents.clear();
    }

    private void resetStats() {
        // grafico
        view.appendLog("Stats reset", false);

        // Reset stats in the table model
        for (int i = 0; i < view.statsTableModel.getRowCount(); i++) {
            view.statsTableModel.setValueAt(0, i, 1); // Wins
            view.statsTableModel.setValueAt(0, i, 2); // Losses
            view.statsTableModel.setValueAt(0, i, 3); // Draws
            view.statsTableModel.setValueAt(0, i, 4); // Points
            view.statsTableModel.setValueAt(0, i, 5); // Invested
            view.statsTableModel.setValueAt("", i, 6); // Actions
        }
        view.statsTableModel.fireTableDataChanged();
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
                + gameParameters.R + ", I=" + gameParameters.I, true);
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
        int I;

        public GameParametersStruct() {
            N = 2;
            R = 500;
            S = 4;
            I = 1;
        }

        public GameParametersStruct(int n, int r, int s, int i) {
            N = n;
            R = r;
            S = s;
            I = i;
        }

        @Override
        public String toString() {
            return "N=" + N +
                    ", R=" + R +
                    ", S=" + S +
                    ", I=" + I;
        }
    }

    public static class PlayerInformation {
        AID aid;
        int id;

        public PlayerInformation(AID a, int i) {
            aid = a;
            id = i;
        }

        @Override
        public boolean equals(Object o) {
            return aid.equals(o);
        }
    }
}
