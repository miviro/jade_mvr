package src.agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;

import java.util.ArrayList;
import java.util.HashMap;

public class NN_Agent extends Agent {
    enum GameAction {
        C, D
    }

    private class Partida {
        public GameAction accionPropia;
        public GameAction accionOponente;

        public Partida(GameAction accionPropia, GameAction accionOponente) {
            this.accionPropia = accionPropia;
            this.accionOponente = accionOponente;
        }
    }

    private State state;
    private AID mainAgent;
    private int myId, opponentId;
    private int R;
    private int currentRound;
    private ACLMessage msg;

    // Add these new instance variables
    private ArrayList<Float> money;
    private ArrayList<Float> stocks;
    private ArrayList<Float> stockPrices;
    private ArrayList<Float> inflationRates;
    private float currentTrend;

    // Define maximum sizes for the lists
    private static final int MAX_SIZE = 100;

    // Define dynamic maximum values for normalization
    private float maxStockPrice = 1000f;
    private float maxInflation = 10f;

    // Define gridSide as a class-level variable
    private int gridSide = 10; // Grid size of 10x10

    // Separate SOM instances
    private SOM somStockMarket;
    private SOM somPrisonersDilemma;

    // Store last 20 opponent actions
    private HashMap<Integer, ArrayList<Partida>> history;

    protected void setup() {
        state = State.waitConfig;

        // Initialize collections
        money = new ArrayList<>();
        stocks = new ArrayList<>();
        stockPrices = new ArrayList<>();
        inflationRates = new ArrayList<>();

        history = new HashMap<>();

        // Initialize the SOM with chosen parameters
        int inputSize = 3; // changed from 5 to 3
        somStockMarket = new SOM(gridSide, inputSize); // Initialize SOM
        somStockMarket.vResetValues(); // Reset SOM grid

        somPrisonersDilemma = new SOM(gridSide, 20); // Initialize SOM
        somPrisonersDilemma.vResetValues(); // Reset SOM grid

        // default values
        money.add(0f);
        stocks.add(0f);
        stockPrices.add(0f);
        inflationRates.add(0f);
        // Register in the yellow pages as a player
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("Player");
        sd.setName("Game");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        addBehaviour(new Play());
        System.out.println("RandomAgent " + getAID().getName() + " is ready.");

    }

    protected void takeDown() {
        // Deregister from the yellow pages
        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            e.printStackTrace();
        }

        System.out.println("RandomPlayer " + getAID().getName() + " terminating.");
        System.exit(0);
    }

    private enum State {
        waitConfig, waitGame, waitAction, waitResults, waitAccounting
    }

    private void printColored(String text) {
        if (myId % 2 == 0) {
            System.out.println("\u001B[31m" + text + "\u001B[0m"); // Red color for even IDs
        } else {
            System.out.println("\u001B[32m" + text + "\u001B[0m"); // Green color for odd IDs
        }
    }

    private class Play extends CyclicBehaviour {
        @Override
        public void action() {
            msg = blockingReceive();
            if (msg != null) {
                switch (state) {
                    case waitConfig:
                        // If INFORM Id#_#_,_,_ PROCESS SETUP --> go to state 1
                        // Else ERROR
                        if (msg.getContent().startsWith("Id#")) {
                            try {
                                if (validateAndSetParameters(msg)) {
                                    state = State.waitGame;
                                }
                            } catch (NumberFormatException e) {
                                printColored(getAID().getName() + ":" + state.name() + " - Bad message");
                            }
                        } else {
                            printColored(getAID().getName() + ":" + state.name() + " - Unexpected message");
                        }
                        break;
                    case waitGame:
                        // If INFORM NewGame ----> waitAction
                        // If REQUEST RoundOver -> waitAccounting
                        // If INFORM GameOver ---> end the program
                        if (msg.getPerformative() == ACLMessage.INFORM && msg.getContent().startsWith("NewGame#")) {
                            if (processNewGame(msg.getContent())) {
                                state = State.waitAction;
                            } else {
                                printColored(getAID().getName() + ":" + state.name() + " - Bad message");
                            }
                        } else if (msg.getPerformative() == ACLMessage.REQUEST
                                && msg.getContent().startsWith("RoundOver")) {
                            processRoundOver(msg);
                            state = State.waitAccounting;
                        } else if (msg.getPerformative() == ACLMessage.INFORM
                                && msg.getContent().startsWith("GameOver")) {
                            takeDown();
                        } else {
                            printColored(getAID().getName() + ":" + state.name() + " - Unexpected message");
                        }
                        break;

                    case waitAction:
                        // If REQUEST ACTION --> waitResults
                        // Else ERROR
                        if (msg.getPerformative() == ACLMessage.REQUEST && msg.getContent().startsWith("Action")) {
                            processAction(msg);
                            state = State.waitResults;
                        } else {
                            printColored(getAID().getName() + ":" + state.name() + " - Unexpected message");
                        }
                        break;
                    case waitResults:
                        // If INFORM RESULTS --> waitGame
                        // Else ERROR
                        if (msg.getPerformative() == ACLMessage.INFORM && msg.getContent().startsWith("Results#")) {
                            processResults(msg);
                            state = State.waitGame;
                        } else {
                            printColored(getAID().getName() + ":" + state.name() + " - Unexpected message");
                        }
                        break;
                    case waitAccounting:
                        // If INFORM Accounting --> waitGame
                        // Else ERROR
                        if (msg.getPerformative() == ACLMessage.INFORM && msg.getContent().startsWith("Accounting#")) {
                            processAccounting(msg);
                            state = State.waitGame;
                        } else {
                            printColored(getAID().getName() + ":" + state.name() + " - Unexpected message");
                        }
                        break;
                }
            }
        }

        // comprobar ID, ignorar round payoff, almacenar accumulated payoff, inflation,
        // current stocks, current stock value
        private void processRoundOver(ACLMessage msg) {

            // Parse RoundOver message
            String[] parts = msg.getContent().split("#");
            if (parts.length == 7) {
                int playerId = Integer.parseInt(parts[1]);
                if (playerId != myId) {
                    throw new IllegalArgumentException("Player ID does not match");
                }
                @SuppressWarnings("unused")
                Float roundPayoff = Float.parseFloat(parts[2]);
                Float accumulatedPayoff = Float.parseFloat(parts[3]);
                Float inflationRate = Float.parseFloat(parts[4]);
                Float currentStocks = Float.parseFloat(parts[5]);
                Float currentStockValue = Float.parseFloat(parts[6]);

                // Update current values (removing if exists, then adding)
                money.add(accumulatedPayoff);
                stockPrices.add(currentStockValue);
                inflationRates.add(inflationRate);
                stocks.add(currentStocks);

                // Limit the size of the lists
                if (money.size() > MAX_SIZE)
                    money.remove(0);
                if (stocks.size() > MAX_SIZE)
                    stocks.remove(0);
                if (stockPrices.size() > MAX_SIZE)
                    stockPrices.remove(0);
                if (inflationRates.size() > MAX_SIZE)
                    inflationRates.remove(0);

                // Update maximum values dynamically
                if (currentStockValue > maxStockPrice)
                    maxStockPrice = currentStockValue;
                if (inflationRate > maxInflation)
                    maxInflation = inflationRate;

                if (stockPrices.size() >= 2) {
                    float lastPrice = stockPrices.get(stockPrices.size() - 1);
                    float prevPrice = stockPrices.get(stockPrices.size() - 2);
                    currentTrend = lastPrice - prevPrice;
                } else {
                    currentTrend = 0f;
                }

                ACLMessage accountingMsg = new ACLMessage(ACLMessage.INFORM);
                accountingMsg.addReceiver(mainAgent);

                // Normalize input values
                double[] inputVector = new double[3];
                inputVector[0] = currentStockValue / maxStockPrice;
                inputVector[1] = inflationRate / maxInflation;
                inputVector[2] = currentTrend / maxStockPrice;

                // Use SOM Stock Market
                String bmu = somStockMarket.sGetBMU(inputVector, true);
                String[] coords = bmu.split(",");
                int x = Integer.parseInt(coords[0]);
                int y = Integer.parseInt(coords[1]);

                // Improved quadrant-based action mapping
                String bsAction;
                if (x < gridSide / 2 && y < gridSide / 2) {
                    if (y < gridSide / 3) {
                        bsAction = "Buy Large";
                    } else if (y < 2 * gridSide / 3) {
                        bsAction = "Buy Medium";
                    } else {
                        bsAction = "Buy Small";
                    }
                } else if (x < 2 * gridSide / 3) {
                    if (y < gridSide / 3) {
                        bsAction = "Hold Large";
                    } else if (y < 2 * gridSide / 3) {
                        bsAction = "Hold Medium";
                    } else {
                        bsAction = "Hold Small";
                    }
                } else {
                    if (y < gridSide / 3) {
                        bsAction = "Sell Large";
                    } else if (y < 2 * gridSide / 3) {
                        bsAction = "Sell Medium";
                    } else {
                        bsAction = "Sell Small";
                    }
                }

                float currentMoney = money.get(money.size() - 1);
                // Decide fractions to buy or sell
                float buyFraction = 1.0f - (float) currentRound / (2 * R);
                if (buyFraction > 1.0f) {
                    buyFraction = 1.0f;
                }
                float sellFraction = 1.0f - (float) currentRound / (2 * R);
                if (sellFraction > 1.0f) {
                    sellFraction = 1.0f;
                }
                float amount = 0f;

                switch (bsAction) {
                    case "Buy Large":
                        bsAction = "Buy";
                        amount = buyFraction * currentMoney;
                        break;
                    case "Buy Medium":
                        bsAction = "Buy";
                        amount = buyFraction * currentMoney / 10;
                        break;
                    case "Buy Small":
                        bsAction = "Buy";
                        amount = buyFraction * currentMoney / 100;
                        break;
                    case "Sell Large":
                        bsAction = "Sell";
                        amount = sellFraction * currentStocks;
                        break;
                    case "Sell Medium":
                        bsAction = "Sell";
                        amount = sellFraction * currentStocks / 10;
                        break;
                    case "Sell Small":
                        bsAction = "Sell";
                        amount = sellFraction * currentStocks / 100;
                        break;
                    default: // Hold
                        bsAction = "Buy";
                        amount = 0f;
                        break;
                }

                accountingMsg.setContent(bsAction + "#" + amount);
                send(accountingMsg);

                currentRound++;
            } else {
                throw new IllegalArgumentException("Invalid RoundOver message format");
            }
        }

        private void processAction(ACLMessage msg) {
            ACLMessage txMsg = new ACLMessage(ACLMessage.INFORM);
            txMsg.addReceiver(mainAgent);

            // Build a 20-element input vector
            double[] inputVector = new double[20];
            for (int i = 0; i < 20; i++) {
                inputVector[i] = 0.5; // Default
            }
            ArrayList<Partida> oppHistory = history.getOrDefault(opponentId, new ArrayList<>());
            int startIndex = Math.max(0, oppHistory.size() - 20);
            int index = 0;
            for (int i = startIndex; i < oppHistory.size() && index < 20; i++) {
                GameAction oppAction = oppHistory.get(i).accionOponente;
                inputVector[index++] = (oppAction == GameAction.C) ? 1.0 : 0.0;
            }

            String bmu = somPrisonersDilemma.sGetBMU(inputVector, false);
            String[] coords = bmu.split(",");
            int x = Integer.parseInt(coords[0]);

            // Only "C" or "D"
            String action = (x < gridSide / 2) ? "C" : "D";
            txMsg.setContent("Action#" + action);
            send(txMsg);
        }

        // Accounting#ID#Payoff#Assets
        // actualizamos el payoff y los activos si nos deja el main agent
        public void processAccounting(ACLMessage msg) {
            String[] parts = msg.getContent().split("#");
            if (parts.length != 4) {
                throw new IllegalArgumentException(getAID().getName() + ": Invalid accounting message format");
            }

            try {
                int playerId = Integer.parseInt(parts[1]);
                if (playerId != myId) {
                    throw new IllegalArgumentException(getAID().getName() + ": Received accounting for wrong player");
                }

                // If you still need to train the SOM, do so without those fields:
                double[] inputVector = new double[] {
                        stockPrices.get(stockPrices.size() - 1) / maxStockPrice,
                        inflationRates.get(inflationRates.size() - 1) / maxInflation,
                        currentTrend / maxStockPrice
                };
                somStockMarket.sGetBMU(inputVector, true);

            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(getAID().getName() + ": Error parsing accounting values");
            }
        }

        // Results#[ID0,ID1]#[C,D]#[X,Y]
        //
        public void processResults(ACLMessage msg) {
            String[] parts = msg.getContent().split("#");
            if (parts.length != 4) {
                throw new IllegalArgumentException("Invalid Results message format");
            }

            try {
                // Parse player IDs
                String[] playerIds = parts[1].split(",");
                String[] actions = parts[2].split(",");
                String[] payoffs = parts[3].split(",");

                if (playerIds.length != 2 || actions.length != 2 || payoffs.length != 2) {
                    throw new IllegalArgumentException("Invalid Results message components");
                }

                // Parse and validate IDs
                int id1 = Integer.parseInt(playerIds[0]);
                int id2 = Integer.parseInt(playerIds[1]);

                // Find my position (first or second)
                int myIndex = (myId == id1) ? 0 : 1;
                int oppIndex = 1 - myIndex;

                if (opponentId != id1 && opponentId != id2) {
                    throw new IllegalArgumentException("Opponent ID not found in Results message");
                }
                if (myId != id1 && myId != id2) {
                    throw new IllegalArgumentException("Player ID not found in Results message");
                }

                // Store the game outcome
                Partida partida = new Partida(GameAction.valueOf(actions[myIndex]),
                        GameAction.valueOf(actions[oppIndex]));

                // Initialize history for this opponent if needed
                if (!history.containsKey(opponentId)) {
                    history.put(opponentId, new ArrayList<>());
                }
                history.get(opponentId).add(partida);

                double[] inputVector = new double[20];
                for (int i = 0; i < 20; i++) {
                    inputVector[i] = 0.5;
                }
                ArrayList<Partida> oppHistory = history.getOrDefault(opponentId, new ArrayList<>());
                int startIndex = Math.max(0, oppHistory.size() - 20);
                int index = 0;
                for (int i = startIndex; i < oppHistory.size() && index < 20; i++) {
                    GameAction oppAction = oppHistory.get(i).accionOponente;
                    inputVector[index++] = (oppAction == GameAction.C) ? 1.0 : 0.0;
                }

                somPrisonersDilemma.sGetBMU(inputVector, true); // Train SOM with input and reward
            } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
                throw new IllegalArgumentException("Error processing Results: " + e.getMessage());
            }
        }

        // Id#[ID]#[N],[R],[S]
        private boolean validateAndSetParameters(ACLMessage msg) throws NumberFormatException {
            String[] parts = msg.getContent().split("#");
            if (parts.length != 3) {
                return false;
            }
            String[] params = parts[2].split(",");
            if (params.length != 3) {
                return false;
            }
            int tMyId = Integer.parseInt(parts[1]);
            // int tN = Integer.parseInt(params[0]);
            int tR = Integer.parseInt(params[1]);
            // float tS = Float.parseFloat(params[2]);

            // At this point everything should be fine, updating class variables
            mainAgent = msg.getSender();
            // ver equivalent en RL
            // N = tN;
            // stockSellFee = tS;
            R = tR;
            myId = tMyId;
            return true;
        }

        // NewGame#[ID0]#[ID1]
        public boolean processNewGame(String msgContent) {
            String[] parts = msgContent.split("#");
            if (parts.length != 3) {
                return false;
            }
            int id1 = Integer.parseInt(parts[1]);
            int id2 = Integer.parseInt(parts[2]);
            if (id1 == myId) {
                opponentId = id2;
                return true;
            } else if (id2 == myId) {
                opponentId = id1;
                return true;
            }
            return false;
        }
    }

    // Add the SOM class definition
    public class SOM {
        private int iGridSide; // Side of the SOM 2D grid
        private int[][] iNumTimesBMU; // Number of times a cell has been a BMU
        private int[] iBMU_Pos = new int[2]; // BMU position in the grid

        private int iInputSize; // Size of the input vector
        private int iRadio; // BMU radio to modify neurons
        private double dLearnRate = 1.0; // Learning rate for this SOM
        private double dDecLearnRate = 0.999; // Used to reduce the learning rate
        private double[] dBMU_Vector = null; // BMU state
        private double[][][] dGrid; // SOM square grid + vector state per neuron

        /**
         * This is the class constructor that creates the 2D SOM grid
         * 
         * @param iSideAux      the square side
         * @param iInputSizeAux the dimensions for the input data
         * 
         */
        public SOM(int iSideAux, int iInputSizeAux) {
            iInputSize = iInputSizeAux;
            iGridSide = iSideAux;
            iRadio = iGridSide / 2; // Initialize iRadio to gridSide / 2
            dBMU_Vector = new double[iInputSize];
            dGrid = new double[iGridSide][iGridSide][iInputSize];
            iNumTimesBMU = new int[iGridSide][iGridSide];

            vResetValues();
        }

        public void vResetValues() {
            dLearnRate = 1.0;
            iNumTimesBMU = new int[iGridSide][iGridSide];
            iBMU_Pos[0] = -1;
            iBMU_Pos[1] = -1;

            for (int i = 0; i < iGridSide; i++) // Initializing the SOM grid/network
                for (int j = 0; j < iGridSide; j++)
                    for (int k = 0; k < iInputSize; k++)
                        dGrid[i][j][k] = Math.random();
        }

        public double[] dvGetBMU_Vector() {
            return dBMU_Vector;
        }

        public double dGetLearnRate() {
            return dLearnRate;
        }

        public double[] dGetNeuronWeights(int x, int y) {
            return dGrid[x][y];
        }

        /**
         * This is the main method that returns the coordinates of the BMU and trains
         * its neighbors
         * 
         * @param dmInput contains the input vector
         * @param bTrain  training or testing phases
         * 
         */
        public String sGetBMU(double[] dmInput, boolean bTrain) { // Added reward parameter
            int x = 0, y = 0;
            double dNorm, dNormMin = Double.MAX_VALUE;
            String sReturn;

            for (int i = 0; i < iGridSide; i++) // Finding the BMU
                for (int j = 0; j < iGridSide; j++) {
                    dNorm = 0;
                    for (int k = 0; k < iInputSize; k++) // Calculating the norm
                        dNorm += (dmInput[k] - dGrid[i][j][k]) * ((dmInput[k] - dGrid[i][j][k]));

                    if (dNorm < dNormMin) {
                        dNormMin = dNorm;
                        x = i;
                        y = j;
                    }
                } // Leaving the loop with the x,y positions for the BMU

            if (bTrain) {
                int xAux = 0;
                int yAux = 0;

                for (int v = -iRadio; v <= iRadio; v++) // Adjusting the neighborhood
                    for (int h = -iRadio; h <= iRadio; h++) {
                        xAux = x + h;
                        yAux = y + v;

                        if (xAux < 0) // Assuming a torus world
                            xAux += iGridSide;
                        else if (xAux >= iGridSide)
                            xAux -= iGridSide;

                        if (yAux < 0)
                            yAux += iGridSide;
                        else if (yAux >= iGridSide)
                            yAux -= iGridSide;

                        for (int k = 0; k < iInputSize; k++)
                            dGrid[xAux][yAux][k] += dLearnRate * (dmInput[k] - dGrid[xAux][yAux][k])
                                    / (1 + v * v + h * h);
                    }

                // Decay the neighborhood radius over time
                if (dLearnRate < 0.1 && iRadio > 1) {
                    iRadio--;
                }
            }

            sReturn = "" + x + "," + y;
            iBMU_Pos[0] = x;
            iBMU_Pos[1] = y;
            dBMU_Vector = dGrid[x][y].clone();
            iNumTimesBMU[x][y]++;
            dLearnRate *= dDecLearnRate;

            return sReturn;
        }

    }
}
