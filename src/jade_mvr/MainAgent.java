package src.jade_mvr;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.SimpleBehaviour;
import jade.wrapper.StaleProxyException;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

import java.util.ArrayList;

import java.io.File;

public class MainAgent extends Agent {
    private static GUI view;
    private static GameParametersStruct gameParameters = new GameParametersStruct();;
    private static boolean verbose = false;
    private static ArrayList<String> agentTypesList = new ArrayList<String>();

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

    private class GameManager extends SimpleBehaviour {

        @Override
        public void action() {
            System.out.println("GameManager");
            initAgentTypesList();
            view = new GUI();
            view.setVisible(true);

            updatePlayers();
            view.appendLog("Application started", false);
            try {
                getContainerController().createNewAgent("randomAgent#1", "src.agents.RandomAgent", null).start();
            } catch (StaleProxyException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
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
}
