package src.jade_mvr;

import jade.core.Agent;
import jade.wrapper.AgentContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import src.agents.*;
import java.io.File;

public class MainAgent extends Agent {
    private static GUI view;
    private static GameParametersStruct gameParameters;
    private static boolean verbose = false;
    private static ArrayList<String> agentsList = new ArrayList<String>();

    private MainAgent() {
        gameParameters = new GameParametersStruct();
        initAgentsList();

        view = new GUI();
        // Show the GUI
        view.setVisible(true);
        view.appendLog("Application started", false);
                        // getContainerController().createNewAgent("randomAgent#" + UUID.randomUUID().toString(), "src.jade_mvr.RandomAgent", null).start();

    }

    public static ArrayList<String> getAgentsList() {
        return agentsList;
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
        view.appendLog("Parameters set to: N=" + gameParameters.N + ", S=" + gameParameters.S + ", R=" + gameParameters.R + ", I=" + gameParameters.I, true);
    }

    public static void main(String[] args) {
        new MainAgent();
    }

    private static void initAgentsList() {
        agentsList.clear();
        File agentsDir = new File("src/agents");
        if (agentsDir.exists() && agentsDir.isDirectory()) {
            File[] files = agentsDir.listFiles((dir, name) -> name.endsWith(".java"));
            if (files != null) {
                for (File file : files) {
                    String fileName = file.getName();
                    if (fileName.endsWith(".java")) {
                        agentsList.add(fileName.substring(0, fileName.length() - 5));
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
