package src.agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;

import java.util.Random;

public class RandomAgent extends Agent {

    private State state;
    private AID mainAgent;
    @SuppressWarnings("unused")
    private int myId, opponentId;
    @SuppressWarnings("unused")
    private int N, R;
    @SuppressWarnings("unused")
    private float S;
    private ACLMessage msg;

    protected void setup() {
        state = State.waitConfig;

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
        // TODO: NUNCA PONER SYSTEM.EXIT(0) AQUI!!!!!!!!!!!!!!!!!!!
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
                            if (validateNewGame(msg.getContent())) {
                                state = State.waitAction;
                            } else {
                                printColored(getAID().getName() + ":" + state.name() + " - Bad message");
                            }
                        } else if (msg.getPerformative() == ACLMessage.REQUEST
                                && msg.getContent().startsWith("RoundOver")) {
                            ACLMessage accountingMsg = new ACLMessage(ACLMessage.INFORM);
                            accountingMsg.addReceiver(mainAgent);
                            String action = new Random().nextBoolean() ? "Buy" : "Sell";
                            accountingMsg.setContent(action + "#" + 1);
                            printColored(getAID().getName() + " sent " + accountingMsg.getContent());
                            send(accountingMsg);

                            state = State.waitAccounting;
                        } else if (msg.getPerformative() == ACLMessage.INFORM
                                && msg.getContent().startsWith("GameOver")) {
                            System.out.println("Game Over " + getAID().getName());
                            doWait();
                        } else {
                            printColored(getAID().getName() + ":" + state.name() + " - Unexpected message");
                        }
                        break;

                    case waitAction:
                        // If REQUEST ACTION --> waitResults
                        // Else ERROR

                        if (msg.getPerformative() == ACLMessage.REQUEST && msg.getContent().startsWith("Action")) {
                            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                            msg.addReceiver(mainAgent);
                            msg.setContent("Action#" + getRandomAction());
                            printColored(getAID().getName() + " sent " + msg.getContent());
                            send(msg);
                            state = State.waitResults;
                        } else {
                            printColored(getAID().getName() + ":" + state.name() + " - Unexpected message");
                        }

                        break;
                    case waitResults:
                        // If INFORM RESULTS --> waitGame
                        // Else ERROR

                        if (msg.getPerformative() == ACLMessage.INFORM && msg.getContent().startsWith("Results#")) {
                            state = State.waitGame;
                        } else {
                            printColored(getAID().getName() + ":" + state.name() + " - Unexpected message");
                        }
                        break;
                    case waitAccounting:
                        // If INFORM Accounting --> waitGame
                        // Else ERROR

                        if (msg.getPerformative() == ACLMessage.INFORM && msg.getContent().startsWith("Accounting#")) {
                            state = State.waitGame;
                        } else {
                            printColored(getAID().getName() + ":" + state.name() + " - Unexpected message");
                        }
                        break;
                }
                printColored(getAID().getName() + " is now in state " + state);
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
            S = tS;
            R = tR;
            myId = tMyId;
            return true;
        }

        // NewGame#[ID0]#[ID1]
        public boolean validateNewGame(String msgContent) {
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
