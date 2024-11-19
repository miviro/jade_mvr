package src.jade_mvr;

import jade.core.Agent;

public class MainAgent {
    private static GUI view;
    private static GameParametersStruct gameParameters;

    public static GameParametersStruct getGameParameters() {
        return gameParameters;
    }

    public static void setGameParameters(GameParametersStruct gameParameters) {
        MainAgent.gameParameters = gameParameters;
    }

    public static void main(String[] args) {
        gameParameters = new GameParametersStruct();
        view = new GUI();
        // Show the GUI
        view.setVisible(true);
        view.appendLog("Application started");
        view.appendLog("Application started1");
        view.appendLog("Application started2");
    }


    private void updateView() {
    }

    public static class GameParametersStruct {
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
