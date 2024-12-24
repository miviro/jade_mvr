package src.agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import java.util.HashMap;

import java.util.ArrayList;
import java.util.Random;
import java.util.Vector;

// import src.SOM; // CHOICE: Remove the import statement for SOM

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
    private int N, R;
    private float stockSellFee;
    private ACLMessage msg;

    // Add these new instance variables
    private ArrayList<Float> money;
    private ArrayList<Float> stocks;
    private ArrayList<Float> stockPrices;
    private ArrayList<Float> inflationRates;
    // Toda la historia de todas las partidas contra cada oponente, incluyendo
    // nuestras y sus acciones
    private HashMap<Integer, ArrayList<Partida>> history;

    // Add new SOM instance variable
    private SOM som; // CHOICE: Instantiate SOM for decision making

    protected void setup() {
        state = State.waitConfig;

        // Initialize collections
        money = new ArrayList<>();
        stocks = new ArrayList<>();
        stockPrices = new ArrayList<>();
        inflationRates = new ArrayList<>();
        history = new HashMap<>();

        // Initialize the SOM with chosen parameters
        int gridSide = 10; // CHOICE: Grid size of 10x10
        int inputSize = 4; // CHOICE: Example input size
        som = new SOM(gridSide, inputSize); // CHOICE: Initialize SOM
        // Optionally reset SOM values if necessary
        som.vResetValues(); // CHOICE: Reset SOM grid

        // Initialize lists with default values to prevent IndexOutOfBoundsException
        money.add(0f); // CHOICE: Initialize money with default value
        stocks.add(0f); // CHOICE: Initialize stocks with default value
        stockPrices.add(0f); // CHOICE: Initialize stockPrices with default value
        inflationRates.add(0f); // CHOICE: Initialize inflationRates with default value

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

    private String getRandomAction() {
        return new Random().nextBoolean() ? "C" : "D";
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
                printColored(
                        getAID().getName() + " received " + msg.getContent() + " from " + msg.getSender().getName()
                                + "\n\t State: " + state); // DELETEME

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
                            System.out.println("Game Over " + getAID().getName());
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
                printColored(getAID().getName() + " is now in state " + state);
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

                ACLMessage accountingMsg = new ACLMessage(ACLMessage.INFORM);
                accountingMsg.addReceiver(mainAgent);

                // Generate input vector for SOM based on current state
                double[] inputVector = new double[] { /* CHOICE: Define input features */
                        money.isEmpty() ? 0 : money.get(money.size() - 1),
                        stocks.isEmpty() ? 0 : stocks.get(stocks.size() - 1),
                        stockPrices.isEmpty() ? 0 : stockPrices.get(stockPrices.size() - 1),
                        inflationRates.isEmpty() ? 0 : inflationRates.get(inflationRates.size() - 1)
                };

                // Use SOM to get BMU and select action
                String bmu = som.sGetBMU(inputVector, true); // CHOICE: Train SOM with input
                String[] coords = bmu.split(",");
                int x = Integer.parseInt(coords[0]);
                int y = Integer.parseInt(coords[1]);

                // Map BMU position to action
                String bsAction;
                if ((x + y) % 2 == 0) { // CHOICE: Simple mapping based on BMU position
                    bsAction = "Buy";
                } else {
                    bsAction = "Sell";
                }

                float currentMoney = money.get(money.size() - 1);
                // Decide fractions to buy or sell
                float buyFraction = 0.1f;
                float sellFraction = 0.1f;
                float amount = 0f;

                if (bsAction.equals("Buy")) {
                    amount = (buyFraction * currentMoney);
                } else {
                    amount = sellFraction * currentStocks;
                }

                accountingMsg.setContent(bsAction + "#" + amount);
                printColored(getAID().getName() + " sent " + accountingMsg.getContent());
                send(accountingMsg);
            } else {
                throw new IllegalArgumentException("Invalid RoundOver message format");
            }
        }

        private void processAction(ACLMessage msg) {
            ACLMessage txMsg = new ACLMessage(ACLMessage.INFORM);
            txMsg.addReceiver(mainAgent);

            // Generate input vector for SOM based on current state
            double[] inputVector = new double[] { /* CHOICE: Define input features */
                    money.isEmpty() ? 0 : money.get(money.size() - 1),
                    stocks.isEmpty() ? 0 : stocks.get(stocks.size() - 1),
                    stockPrices.isEmpty() ? 0 : stockPrices.get(stockPrices.size() - 1),
                    inflationRates.isEmpty() ? 0 : inflationRates.get(inflationRates.size() - 1)
            };

            // Use SOM to get BMU and select action
            String bmu = som.sGetBMU(inputVector, true); // CHOICE: Train SOM with input
            String[] coords = bmu.split(",");
            int x = Integer.parseInt(coords[0]);
            int y = Integer.parseInt(coords[1]);

            // Map BMU position to action
            String action;
            if ((x + y) % 2 == 0) { // CHOICE: Simple mapping based on BMU position
                action = "C";
            } else {
                action = "D";
            }

            txMsg.setContent("Action#" + action);
            printColored(getAID().getName() + " sent " + txMsg.getContent());
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

                float updatedPayoff = Float.parseFloat(parts[2]);
                float updatedAssets = Float.parseFloat(parts[3]);

                // Update current values (removing if exists, then adding)
                // solo guardoamos los estados dspues de la accion
                money.add(updatedPayoff);
                stocks.add(updatedAssets);

                printColored(getAID().getName() + ": Updated payoff=" + updatedPayoff +
                        ", assets=" + updatedAssets);

                // CHOICE: Create input vector for SOM based on updated state
                double[] inputVector = new double[] {
                        updatedPayoff,
                        updatedAssets,
                        stockPrices.isEmpty() ? 0 : stockPrices.get(stockPrices.size() - 1),
                        inflationRates.isEmpty() ? 0 : inflationRates.get(inflationRates.size() - 1)
                };

                // Update SOM with the new input and reward
                som.sGetBMU(inputVector, true); // CHOICE: Train SOM with new input

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

                // CHOICE: Use payoff as reward to train SOM
                double reward = Double.parseDouble(payoffs[myIndex]);
                double normalizedReward = reward / 10.0; // CHOICE: Normalize reward

                // Safety checks to prevent IndexOutOfBoundsException
                double moneyValue = !money.isEmpty() ? money.get(money.size() - 1) : 0.0;
                double stocksValue = !stocks.isEmpty() ? stocks.get(stocks.size() - 1) : 0.0;
                double stockPriceValue = !stockPrices.isEmpty() ? stockPrices.get(stockPrices.size() - 1) : 0.0;
                double inflationRateValue = !inflationRates.isEmpty() ? inflationRates.get(inflationRates.size() - 1)
                        : 0.0;

                double[] inputVector = new double[] {
                        moneyValue,
                        stocksValue,
                        stockPriceValue,
                        inflationRateValue
                };
                som.sGetBMU(inputVector, true); // CHOICE: Train SOM with input and reward

                printColored(String.format("%s: Round result - My action: %s, Opponent(%d): %s, Payoffs: %s,%s",
                        getAID().getName(), actions[myIndex], opponentId, actions[oppIndex],
                        payoffs[myIndex], payoffs[oppIndex]));

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
            int tN = Integer.parseInt(params[0]);
            int tR = Integer.parseInt(params[1]);
            float tS = Float.parseFloat(params[2]);

            // At this point everything should be fine, updating class variables
            mainAgent = msg.getSender();
            N = tN;
            stockSellFee = tS;
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
            iRadio = iGridSide / 10;
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
        public String sGetBMU(double[] dmInput, boolean bTrain) {
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

            }

            sReturn = "" + x + "," + y;
            iBMU_Pos[0] = x;
            iBMU_Pos[1] = y;
            dBMU_Vector = dGrid[x][y].clone();
            iNumTimesBMU[x][y]++;
            dLearnRate *= dDecLearnRate;

            return sReturn;
        }

    } // End of SOM class

}
