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

public class RL_Agent extends Agent {
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

    // -----------------------------------------------------------
    // 1) Add new RL-related instance variables
    // -----------------------------------------------------------
    final double dDecFactorLR = 0.99;
    final double dMINLearnRate = 0.05;
    boolean bAllActions = false;
    int iNumActions = 2; // For "C" or "D"
    int iNewAction;
    int iNewStockAction; // For "B" or "S" if needed
    int iLastAction;
    int[] iNumTimesAction = new int[iNumActions];
    double[] dPayoffAction = new double[iNumActions];
    double[] dProbAction = new double[iNumActions];
    Vector<StateAction> oVStateActions;
    StateAction oPresentStateAction;
    StateAction oLastStateAction;
    double dLearnRate = 0.5;
    private int iLastStockAction = -1; // store last chosen stock action
    private int consecutiveFailingBuys = 0; // Track consecutive failing buys

    protected void setup() {
        state = State.waitConfig;

        // Initialize collections
        money = new ArrayList<>();
        stocks = new ArrayList<>();
        stockPrices = new ArrayList<>();
        inflationRates = new ArrayList<>();
        history = new HashMap<>();

        // 2) Initialize RL collections
        oVStateActions = new Vector<>();

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

                // 5) Optionally pick buy/sell with RL stats
                vGetNewActionStats();
                String bsAction = (iNewStockAction == 0) ? "Buy" : "Sell";
                iLastStockAction = iNewStockAction; // remember chosen stock action for RL reward

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

            // 3) Use RL to select "C" or "D"
            vGetNewActionAutomata("Opponent" + opponentId, iNumActions, 0.0);
            String action = (iNewAction == 0) ? "C" : "D";
            iLastAction = iNewAction;

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

                // Once payoff and assets are updated, compute stock reward
                if (stockPrices.size() >= 2 && inflationRates.size() >= 1 && iLastStockAction != -1) {
                    float lastPrice = stockPrices.get(stockPrices.size() - 2);
                    float newPrice = stockPrices.get(stockPrices.size() - 1);
                    float inflationRate = inflationRates.get(inflationRates.size() - 1);

                    float priceChange = (newPrice - lastPrice) / 100;

                    double stockReward = 0.0;
                    if (iLastStockAction == 0 && priceChange < inflationRate) {
                        consecutiveFailingBuys++;
                        stockReward = -3.0 * consecutiveFailingBuys; // further stronger penalty
                        // Prevent buying after 3 consecutive failing buys
                        if (consecutiveFailingBuys >= 3) {
                            // Force action to Sell
                            iNewStockAction = 1;
                            stockReward = 2.0; // reward for forced sell
                        }
                    } else if (iLastStockAction == 0) {
                        consecutiveFailingBuys = 0;
                        stockReward = 0.3; // slightly increased positive reward
                    } else if (iLastStockAction == 1 && priceChange < inflationRate) {
                        stockReward = 1.5; // increased reward for selling in a downward environment
                    } else if (iLastStockAction == 1 && priceChange >= inflationRate) {
                        stockReward = 0.5; // slight reward for selling even if not necessary
                    }
                    // pass the result to RL
                    vGetNewActionAutomata("StockState", 2, stockReward);
                    iLastAction = iNewStockAction;
                    iLastStockAction = -1;
                }

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

                // 4) After parsing payoffs[myIndex]
                double reward = Double.parseDouble(payoffs[myIndex]);
                vGetNewActionAutomata("Opponent" + opponentId, iNumActions, reward);
                iLastAction = iNewAction;

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

    // -----------------------------------------------------------
    // 6) Add new helper methods and class
    // -----------------------------------------------------------
    public void vGetNewActionStats() {
        // ...logic adapted from test.notjava...
        // Checking if all actions used
        if (!bAllActions) {
            bAllActions = true;
            for (int i = 0; i < iNumActions; i++) {
                if (iNumTimesAction[i] == 0) {
                    bAllActions = false;
                    break;
                }
            }
        } else {
            double dAuxTot = 0;
            double[] dAvgPayoffAction = new double[iNumActions];
            for (int i = 0; i < iNumActions; i++) {
                dAvgPayoffAction[i] = dPayoffAction[i] / (double) iNumTimesAction[i];
                dAuxTot += dAvgPayoffAction[i];
            }
            for (int i = 0; i < iNumActions; i++) {
                dProbAction[i] = dAvgPayoffAction[i] / dAuxTot;
            }
        }
        double dAux = Math.random();
        double dAuxTot = 0;
        for (int i = 0; i < iNumActions; i++) {
            dAuxTot += dProbAction[i];
            if (dAux <= dAuxTot) {
                iNewStockAction = i;
                break;
            }
        }
    }

    public void vGetNewActionAutomata(String sState, int iNActions, double dReward) {
        boolean bFound = false;
        for (StateAction sa : oVStateActions) {
            if (sa.sState.equals(sState)) {
                oPresentStateAction = sa;
                bFound = true;
                break;
            }
        }
        if (!bFound) {
            oPresentStateAction = new StateAction(sState, iNActions, true);
            oVStateActions.add(oPresentStateAction);
        }
        if (oLastStateAction != null) {
            // Scale rewards: 1.0 for payoff=4 (successful defection), 0.5 for payoff=2
            // (mutual cooperation), 0.0 otherwise
            double adjustedReward = 0.0;
            if (dReward == 4.0) {
                adjustedReward = 1.0;
            } else if (dReward == 2.0) {
                adjustedReward = 0.5;
            }

            if (adjustedReward > 0) {
                for (int i = 0; i < iNActions; i++) {
                    if (i == iLastAction) {
                        oLastStateAction.dValAction[i] += dLearnRate
                                * (adjustedReward - oLastStateAction.dValAction[i]);
                    } else {
                        oLastStateAction.dValAction[i] *= (1.0 - dLearnRate * adjustedReward);
                    }
                }
            } else {
                // If payoff = 0, punish last action more strongly
                for (int i = 0; i < iNActions; i++) {
                    if (i == iLastAction) {
                        oLastStateAction.dValAction[i] *= (1.0 - dLearnRate * 0.8); // Stronger punishment
                    }
                }
            }
        }
        // Adjust action selection to prioritize selling after consecutive failing buys
        if (consecutiveFailingBuys >= 3) {
            iNewAction = 1; // Force Sell
        } else {
            double dValAcc = 0;
            double dValRandom = Math.random();
            for (int i = 0; i < iNActions; i++) {
                dValAcc += oPresentStateAction.dValAction[i];
                if (dValRandom < dValAcc) {
                    iNewAction = i;
                    break;
                }
            }
        }
        oLastStateAction = oPresentStateAction;
        dLearnRate *= dDecFactorLR;
        if (dLearnRate < dMINLearnRate)
            dLearnRate = dMINLearnRate;
    }

    // Helper class for RL state-action values
    public class StateAction {
        public String sState;
        public double[] dValAction;

        public StateAction(String sState, int iNumActions, boolean bInitialize) {
            this.sState = sState;
            this.dValAction = new double[iNumActions];
            if (bInitialize) {
                double initialValue = 1.0 / iNumActions;
                for (int i = 0; i < iNumActions; i++) {
                    this.dValAction[i] = initialValue;
                }
            }
        }
    }
}
