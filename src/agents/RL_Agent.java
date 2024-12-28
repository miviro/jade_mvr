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
import java.util.Vector;

public class RL_Agent extends Agent {
    enum GameAction {
        C, D
    }

    private State state;
    private AID mainAgent;
    private int myId, opponentId;
    private int R;
    private int currentRound;
    // Tampoco nos hace falta, ya la aprende por la diff con MainAgent
    // private float stockSellFee;
    private ACLMessage msg;

    // Add these new instance variables
    private ArrayList<Float> money;
    private ArrayList<Float> stocks;
    private ArrayList<Float> stockPrices;
    private ArrayList<Float> inflationRates;

    final double dDecFactorLR = 0.9999;
    final double dMINLearnRate = 0.05;
    boolean bAllActions = false;
    int iNumActions = 2; // For "C" or "D"
    int iNewAction;
    int iNewStockAction; // For "Buy" or "Sell" if needed
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

    // Separate “last-action” variables
    private int iLastPdAction;
    private int iNewPdAction;

    // Separate StateAction references
    private StateAction oLastStateAction_PD;
    private StateAction oLastStateAction_Stock;

    // Separate RL collections for PD and Stock
    private Vector<StateAction> oVStateActions_PD;
    private Vector<StateAction> oVStateActions_Stock;

    // Separate learning rates
    private double dLearnRate_PD = 0.5;
    private double dLearnRate_Stock = 0.5;

    // Separate action count arrays
    private int[] iNumTimesAction_PD = new int[iNumActions];
    private int[] iNumTimesAction_Stock = new int[iNumActions];

    // Add these new instance variables
    private boolean bAllActions_PD = false;
    private boolean bAllActions_Stock = false;
    private double[] dProbAction_PD = new double[iNumActions];
    private double[] dProbAction_Stock = new double[iNumActions];
    private double[] dPayoffAction_PD = new double[iNumActions];
    private double[] dPayoffAction_Stock = new double[iNumActions];

    protected void setup() {
        state = State.waitConfig;

        // Initialize collections
        money = new ArrayList<>();
        stocks = new ArrayList<>();
        stockPrices = new ArrayList<>();
        inflationRates = new ArrayList<>();

        // 2) Initialize RL collections
        oVStateActions = new Vector<>();

        // Initialize separate RL collections
        oVStateActions_PD = new Vector<>();
        oVStateActions_Stock = new Vector<>();

        // Initialize StateAction references
        oLastStateAction_PD = null;
        oLastStateAction_Stock = null;

        // Initialize newly added arrays
        dProbAction_PD = new double[iNumActions];
        dProbAction_Stock = new double[iNumActions];
        dPayoffAction_PD = new double[iNumActions];
        dPayoffAction_Stock = new double[iNumActions];

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

        System.out.println("RL_Agent " + getAID().getName() + " terminating.");
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

                ACLMessage accountingMsg = new ACLMessage(ACLMessage.INFORM);
                accountingMsg.addReceiver(mainAgent);

                vGetNewActionStats();
                String bsAction = (iNewStockAction == 0) ? "Buy" : "Sell";
                iLastStockAction = iNewStockAction; // remember chosen stock action for RL reward

                float currentMoney = money.get(money.size() - 1);
                // Decide fractions to buy or sell
                // jugar mas agresivo al principio
                float buyFraction = 1.0f - (float) currentRound / R;
                float sellFraction = 1.0f - (float) currentRound / R;
                float amount = 0f;

                if (bsAction.equals("Buy")) {
                    amount = (buyFraction * currentMoney);
                } else {
                    amount = sellFraction * currentStocks;
                }

                accountingMsg.setContent(bsAction + "#" + amount);
                send(accountingMsg);

                // aadir ronda
                currentRound++;
            } else {
                throw new IllegalArgumentException("Invalid RoundOver message format");
            }
        }

        private void processAction(ACLMessage msg) {
            ACLMessage txMsg = new ACLMessage(ACLMessage.INFORM);
            txMsg.addReceiver(mainAgent);

            // PD action
            String action = (iNewPdAction == 0) ? "C" : "D";
            iLastPdAction = iNewPdAction;

            txMsg.setContent("Action#" + action);
            send(txMsg);
        }

        // Separate RL methods for PD
        public void vGetNewActionAutomata_PD(String sState, int iNActions, double dReward) {
            boolean bFound = false;
            for (StateAction sa : oVStateActions_PD) {
                if (sa.sState.equals(sState)) {
                    oPresentStateAction = sa;
                    bFound = true;
                    break;
                }
            }
            if (!bFound) {
                oPresentStateAction = new StateAction(sState, iNActions, true);
                oVStateActions_PD.add(oPresentStateAction);
            }
            if (oLastStateAction_PD != null) {
                // Scale rewards for PD
                double adjustedReward = 0.0;
                if (dReward == 4.0) {
                    adjustedReward = 1.0;
                } else if (dReward == 2.0) {
                    adjustedReward = 0.5;
                }

                if (adjustedReward > 0) {
                    for (int i = 0; i < iNActions; i++) {
                        if (i == iLastPdAction) {
                            oLastStateAction_PD.dValAction[i] += dLearnRate_PD
                                    * (adjustedReward - oLastStateAction_PD.dValAction[i]);
                        } else {
                            oLastStateAction_PD.dValAction[i] *= (1.0 - dLearnRate_PD * adjustedReward);
                        }
                    }
                } else {
                    // If payoff = 0, punish last action more strongly
                    for (int i = 0; i < iNActions; i++) {
                        if (i == iLastPdAction) {
                            oLastStateAction_PD.dValAction[i] *= (1.0 - dLearnRate_PD * 0.8); // Stronger punishment
                        }
                    }
                }
            }
            // Action selection for PD
            double dValAcc = 0;
            double dValRandom = Math.random();
            for (int i = 0; i < iNActions; i++) {
                dValAcc += oPresentStateAction.dValAction[i];
                if (dValRandom < dValAcc) {
                    iNewPdAction = i;
                    break;
                }
            }
            oLastStateAction_PD = oPresentStateAction;
            dLearnRate_PD *= dDecFactorLR;
            if (dLearnRate_PD < dMINLearnRate)
                dLearnRate_PD = dMINLearnRate;

            // Increment PD action count
            iNumTimesAction_PD[iNewPdAction]++;
        }

        // Separate RL methods for Stock
        public void vGetNewActionAutomata_Stock(String sState, int iNActions, double dReward) {
            if (consecutiveFailingBuys >= 3) {
                iNewStockAction = 1; // Force Sell
                consecutiveFailingBuys = 0; // Reset counter
                // Update Q-value for forced action
                boolean bFound = false;
                for (StateAction sa : oVStateActions_Stock) {
                    if (sa.sState.equals(sState)) {
                        oPresentStateAction = sa;
                        bFound = true;
                        break;
                    }
                }
                if (!bFound) {
                    oPresentStateAction = new StateAction(sState, iNActions, true);
                    oVStateActions_Stock.add(oPresentStateAction);
                }
                if (oLastStateAction_Stock != null) {
                    double adjustedReward = dReward; // Assuming stockReward is already scaled appropriately

                    if (adjustedReward > 0) {
                        for (int i = 0; i < iNActions; i++) {
                            if (i == iLastStockAction) {
                                oLastStateAction_Stock.dValAction[i] += dLearnRate_Stock
                                        * (adjustedReward - oLastStateAction_Stock.dValAction[i]);
                            } else {
                                oLastStateAction_Stock.dValAction[i] *= (1.0 - dLearnRate_Stock * adjustedReward);
                            }
                        }
                    } else {
                        // If reward <= 0, punish last action more strongly
                        for (int i = 0; i < iNActions; i++) {
                            if (i == iLastStockAction) {
                                oLastStateAction_Stock.dValAction[i] *= (1.0 - dLearnRate_Stock * 0.8); // Stronger
                                                                                                        // punishment
                            }
                        }
                    }
                }

                // Select forced action
                // No random selection needed
                oLastStateAction_Stock = oPresentStateAction;
                dLearnRate_Stock *= dDecFactorLR;
                if (dLearnRate_Stock < dMINLearnRate)
                    dLearnRate_Stock = dMINLearnRate;

                // Increment Stock action count
                iNumTimesAction_Stock[iNewStockAction]++;
                return; // Exit to enforce forced sell
            }

            // Normal action selection
            boolean bFound = false;
            for (StateAction sa : oVStateActions_Stock) {
                if (sa.sState.equals(sState)) {
                    oPresentStateAction = sa;
                    bFound = true;
                    break;
                }
            }
            if (!bFound) {
                oPresentStateAction = new StateAction(sState, iNActions, true);
                oVStateActions_Stock.add(oPresentStateAction);
            }
            if (oLastStateAction_Stock != null) {
                // Scale rewards for Stock
                double adjustedReward = dReward; // Assuming stockReward is already scaled appropriately

                if (adjustedReward > 0) {
                    for (int i = 0; i < iNActions; i++) {
                        if (i == iLastStockAction) {
                            oLastStateAction_Stock.dValAction[i] += dLearnRate_Stock
                                    * (adjustedReward - oLastStateAction_Stock.dValAction[i]);
                        } else {
                            oLastStateAction_Stock.dValAction[i] *= (1.0 - dLearnRate_Stock * adjustedReward);
                        }
                    }
                } else {
                    // If reward <= 0, punish last action more strongly
                    for (int i = 0; i < iNActions; i++) {
                        if (i == iLastStockAction) {
                            oLastStateAction_Stock.dValAction[i] *= (1.0 - dLearnRate_Stock * 0.8); // Stronger
                                                                                                    // punishment
                        }
                    }
                }
            }

            // Action selection
            double dValAcc = 0;
            double dValRandom = Math.random();
            for (int i = 0; i < iNActions; i++) {
                dValAcc += oPresentStateAction.dValAction[i];
                if (dValRandom < dValAcc) {
                    iNewStockAction = i;
                    break;
                }
            }
            oLastStateAction_Stock = oPresentStateAction;
            dLearnRate_Stock *= dDecFactorLR;
            if (dLearnRate_Stock < dMINLearnRate)
                dLearnRate_Stock = dMINLearnRate;

            // Increment Stock action count
            iNumTimesAction_Stock[iNewStockAction]++;
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
                    // necesario?
                    dPayoffAction_Stock[iLastStockAction] += stockReward;
                    // pass the result to RL
                    vGetNewActionAutomata_Stock("StockState", 2, stockReward);
                    iLastStockAction = iNewStockAction; // Corrected assignment
                }

            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(getAID().getName() + ": Error parsing accounting values");
            }
        }

        // Results#[ID0,ID1]#[C,D]#[X,Y]
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

                // 4) After parsing payoffs[myIndex]
                double reward = Double.parseDouble(payoffs[myIndex]);
                dPayoffAction_PD[iLastPdAction] += reward;
                vGetNewActionAutomata_PD("Opponent" + opponentId, iNumActions, reward);
                iLastPdAction = iNewPdAction; // Corrected assignment
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
            // N nos da igual
            // int tN = Integer.parseInt(params[0]);
            int tR = Integer.parseInt(params[1]);
            // float tS = Float.parseFloat(params[2]);

            // At this point everything should be fine, updating class variables
            mainAgent = msg.getSender();
            // N nos da igual
            // N = tN;
            // No nos hace falta, ver declaracion
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

        public void vGetNewActionStats() {
            // Separate action stats for PD and Stock
            // PD actions
            if (!bAllActions_PD) {
                bAllActions_PD = true;
                for (int i = 0; i < iNumActions; i++) {
                    if (iNumTimesAction_PD[i] == 0) {
                        bAllActions_PD = false;
                        break;
                    }
                }
            } else {
                double dAuxTot_PD = 0;
                double[] dAvgPayoffAction_PD = new double[iNumActions];
                for (int i = 0; i < iNumActions; i++) {
                    dAvgPayoffAction_PD[i] = dPayoffAction_PD[i] / (double) iNumTimesAction_PD[i];
                    dAuxTot_PD += dAvgPayoffAction_PD[i];
                }
                for (int i = 0; i < iNumActions; i++) {
                    dProbAction_PD[i] = dAvgPayoffAction_PD[i] / dAuxTot_PD;
                }
            }

            // Stock actions
            if (!bAllActions_Stock) {
                bAllActions_Stock = true;
                for (int i = 0; i < iNumActions; i++) {
                    if (iNumTimesAction_Stock[i] == 0) {
                        bAllActions_Stock = false;
                        break;
                    }
                }
            } else {
                double dAuxTot_Stock = 0;
                double[] dAvgPayoffAction_Stock = new double[iNumActions];
                for (int i = 0; i < iNumActions; i++) {
                    dAvgPayoffAction_Stock[i] = dPayoffAction_Stock[i] / (double) iNumTimesAction_Stock[i];
                    dAuxTot_Stock += dAvgPayoffAction_Stock[i];
                }
                for (int i = 0; i < iNumActions; i++) {
                    dProbAction_Stock[i] = dAvgPayoffAction_Stock[i] / dAuxTot_Stock;
                }
            }

            // PD action selection
            if (bAllActions_PD) {
                double dAux_PD = Math.random();
                double dAuxTot_PD = 0;
                for (int i = 0; i < iNumActions; i++) {
                    dAuxTot_PD += dProbAction_PD[i];
                    if (dAux_PD <= dAuxTot_PD) {
                        iNewPdAction = i;
                        break;
                    }
                }
            }

            // Stock action selection
            if (bAllActions_Stock) {
                double dAux_Stock = Math.random();
                double dAuxTot_Stock = 0;
                for (int i = 0; i < iNumActions; i++) {
                    dAuxTot_Stock += dProbAction_Stock[i];
                    if (dAux_Stock <= dAuxTot_Stock) {
                        iNewStockAction = i;
                        break;
                    }
                }
            }
        }
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
