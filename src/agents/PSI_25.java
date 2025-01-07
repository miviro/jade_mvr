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

public class PSI_25 extends Agent {
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
    private float stockSellFee;
    private int currentRound;
    private ACLMessage msg;

    // Add these new instance variables
    private ArrayList<Float> money;
    private ArrayList<Float> stocks;
    private ArrayList<Float> stockPrices;
    private ArrayList<Float> inflationRates;
    // Toda la historia de todas las partidas contra cada oponente, incluyendo
    // nuestras y sus acciones
    private HashMap<Integer, ArrayList<Partida>> history;

    protected void setup() {
        state = State.waitConfig;

        // Initialize collections
        money = new ArrayList<>();
        stocks = new ArrayList<>();
        stockPrices = new ArrayList<>();
        inflationRates = new ArrayList<>();
        history = new HashMap<>();

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
                Float roundPayoff = Float.valueOf(parts[2]);
                Float accumulatedPayoff = Float.valueOf(parts[3]);
                Float inflationRate = Float.valueOf(parts[4]);
                Float currentStocks = Float.valueOf(parts[5]);
                Float currentStockValue = Float.valueOf(parts[6]);

                // Update current values (removing if exists, then adding)
                money.add(accumulatedPayoff);
                stockPrices.add(currentStockValue);
                inflationRates.add(inflationRate);
                stocks.add(currentStocks);

                ACLMessage accountingMsg = new ACLMessage(ACLMessage.INFORM);
                accountingMsg.addReceiver(mainAgent);

                // if the %difference between last stockPrices and second to last stockPrices is
                // positive and greater than %current inflation, Buy with all our money. Else
                // sell everything
                float lastPrice = 0;
                float prevPrice = 0;
                try {
                    lastPrice = stockPrices.get(stockPrices.size() - 1);
                    prevPrice = stockPrices.get(stockPrices.size() - 2);
                } catch (Exception e) {
                    lastPrice = 1;
                    prevPrice = 1; // Avoid division by zero
                }
                float diffPercent = ((lastPrice - prevPrice) / prevPrice) * 100;
                float currentInflation = inflationRates.get(inflationRates.size() - 1);

                float buyFraction = 1.0f - (float) currentRound / R;
                float sellFraction = 1.0f - (float) currentRound / R;
                float buyAmount = money.get(money.size() - 1) * buyFraction;
                float sellAmount = stocks.get(stocks.size() - 1) * sellFraction;

                if ((diffPercent + stockSellFee) > currentInflation && diffPercent > 0) {
                    // be more aggressive at the beggining
                    accountingMsg.setContent("Buy#" + buyAmount);
                } else {
                    // same as buy
                    accountingMsg.setContent("Sell#" + sellAmount);
                }

                send(accountingMsg);

                currentRound++;
            } else {
                throw new IllegalArgumentException("Invalid RoundOver message format");
            }
        }

        private void processAction(ACLMessage msg) {
            ACLMessage txMsg = new ACLMessage(ACLMessage.INFORM);
            txMsg.addReceiver(mainAgent);

            try {
                ArrayList<Partida> opponentHistory = history.get(opponentId);
                int windowSize = 20;
                int startIndex = Math.max(0, opponentHistory.size() - windowSize);
                GameAction nextAction;

                // Check for "D with no consequences" and "C then opponent D"
                boolean continueD = false;
                boolean switchToD = false;
                for (int i = startIndex; i < opponentHistory.size() - 1; i++) {
                    Partida current = opponentHistory.get(i);
                    Partida next = opponentHistory.get(i + 1);
                    if (current.accionPropia == GameAction.D && next.accionOponente == GameAction.C) {
                        continueD = true;
                        break;
                    }
                    if (current.accionPropia == GameAction.C && next.accionOponente == GameAction.D) {
                        switchToD = true;
                        break;
                    }
                }

                if (continueD) {
                    nextAction = GameAction.D;
                } else if (switchToD) {
                    nextAction = GameAction.D;
                } else {
                    // Otherwise rely on our last action or default to C, with a 5% chance to flip
                    Partida lastPartida = opponentHistory.isEmpty()
                            ? null
                            : opponentHistory.get(opponentHistory.size() - 1);
                    if (lastPartida != null) {
                        nextAction = lastPartida.accionPropia;
                        if (Math.random() < 0.05) {
                            nextAction = (nextAction == GameAction.C) ? GameAction.D : GameAction.C;
                        }
                    } else {
                        nextAction = GameAction.C;
                    }
                }

                txMsg.setContent("Action#" + nextAction.name());
            } catch (Exception e) {
                // default to cooperate
                txMsg.setContent("Action#C");
            }
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
            int tR = Integer.parseInt(params[1]);
            float tS = Float.parseFloat(params[2]);

            // At this point everything should be fine, updating class variables
            mainAgent = msg.getSender();
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
}
