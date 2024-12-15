import java.util.ArrayList;
import java.util.Random;

public class RLStockExample {
    // Actions
    enum Action {
        BUY, SELL, HOLD
    }

    // Calculate moving average
    private float calculateMovingAverage(int t) {
        int windowSize = 5; // Example window size
        int start = Math.max(0, t - windowSize + 1);
        float sum = 0.0f;
        for (int i = start; i <= t; i++) {
            sum += stockPrices.get(i);
        }
        return sum / (t - start + 1);
    }

    // Parameters
    private static final double ALPHA = 0.01; // Decreased learning rate
    private static final double GAMMA = 0.95; // Increased discount factor
    private static final int STATE_SIZE = 3; // Number of discrete states

    private double epsilon = 1.0; // Start with high exploration rate
    private double minEpsilon = 0.01;
    private double epsilonDecay = 0.999;

    // Q-Table
    private double[][] Q;

    // Stock data
    private ArrayList<Float> stockPrices;

    // Agent's money and stocks
    private float money;
    private float numStocks;

    private Random random;

    public RLStockExample(ArrayList<Float> stockPrices) {
        this.stockPrices = stockPrices;
        this.Q = new double[STATE_SIZE][Action.values().length];
        this.money = 1000.0f; // Starting with $1000
        this.numStocks = 0.0f;
        this.random = new Random();
    }

    // Calculate momentum as additional state feature
    private float calculateMomentum(int t) {
        if (t == 0)
            return 0.0f;
        return stockPrices.get(t) - stockPrices.get(t - 1);
    }

    // Discretize the stock price into states using momentum
    private int getState(int t, float currentPrice, float prevPrice, float movingAverage) {
        float momentum = calculateMomentum(t);
        if (momentum > 0) {
            return 0; // Bullish trend
        } else if (momentum < 0) {
            return 1; // Bearish trend
        } else {
            return 2; // Sideways
        }
    }

    // Choose action using epsilon-greedy policy
    private Action chooseAction(int state) {
        if (random.nextDouble() < epsilon) {
            // Exploration
            return Action.values()[random.nextInt(Action.values().length)];
        } else {
            // Exploitation
            double maxQ = Double.NEGATIVE_INFINITY;
            Action bestAction = Action.HOLD;
            for (Action action : Action.values()) {
                if (Q[state][action.ordinal()] > maxQ) {
                    maxQ = Q[state][action.ordinal()];
                    bestAction = action;
                }
            }
            return bestAction;
        }
    }

    // Update Q-Table
    private void updateQ(int state, Action action, double reward, int nextState) {
        double maxNextQ = Double.NEGATIVE_INFINITY;
        for (Action a : Action.values()) {
            if (Q[nextState][a.ordinal()] > maxNextQ) {
                maxNextQ = Q[nextState][a.ordinal()];
            }
        }
        Q[state][action.ordinal()] = Q[state][action.ordinal()]
                + ALPHA * (reward + GAMMA * maxNextQ - Q[state][action.ordinal()]);
    }

    // Execute action and return reward based on the change in total assets
    private double executeAction(Action action, float price, int t) {
        float futurePrice = (t + 1 < stockPrices.size()) ? stockPrices.get(t + 1) : price;

        switch (action) {
            case BUY:
                if (money >= price) {
                    money -= price;
                    numStocks += 1.0f;
                } else {
                    return -1.0; // Penalty for invalid action
                }
                break;
            case SELL:
                if (numStocks > 0) {
                    money += price;
                    numStocks -= 1.0f;
                } else {
                    return -1.0;
                }
                break;
            case HOLD:
                // No transaction
                break;
        }

        // Calculate the change in total assets
        float currentTotalAsset = money + numStocks * price;
        float futureTotalAsset = money + numStocks * futurePrice;

        // Reward is the difference between future and current total assets
        return futureTotalAsset - currentTotalAsset;
    }

    public void train(int episodes) {
        for (int ep = 0; ep < episodes; ep++) {
            // Reset for each episode
            money = 1000.0f;
            numStocks = 0.0f;
            double totalReward = 0.0;

            for (int t = 0; t < stockPrices.size() - 1; t++) {
                float movingAverage = calculateMovingAverage(t);
                float currentPrice = stockPrices.get(t);
                float prevPrice = stockPrices.get((t == 0) ? 0 : t - 1);
                int state = getState(t, currentPrice, prevPrice, movingAverage);
                Action action = chooseAction(state);

                // Execute action and get reward based on future price
                double reward = executeAction(action, currentPrice, t);
                totalReward += reward;

                float nextPrice = stockPrices.get(t + 1);
                float nextMovingAverage = calculateMovingAverage(t + 1);
                int nextState = getState(t + 1, nextPrice, currentPrice, nextMovingAverage);

                updateQ(state, action, reward, nextState);
            }

            // Decay epsilon
            epsilon = Math.max(minEpsilon, epsilon * epsilonDecay);

            // Optionally, print episode summary
            System.out.println("Episode " + (ep + 1) + ": Total Reward = " + totalReward + ", Epsilon = " + epsilon);
        }
    }

    public void test() {
        // Testing the trained agent
        money = 1000.0f;
        numStocks = 0.0f;
        double totalReward = 0.0;

        for (int t = 0; t < stockPrices.size() - 1; t++) {
            float currentPrice = stockPrices.get(t);
            float movingAverage = calculateMovingAverage(t);
            float prevPrice = stockPrices.get((t == 0) ? 0 : t - 1);
            int state = getState(t, currentPrice, prevPrice, movingAverage);
            // Choose best action (no exploration)
            Action action = Action.HOLD;
            double maxQ = Double.NEGATIVE_INFINITY;
            for (Action a : Action.values()) {
                if (Q[state][a.ordinal()] > maxQ) {
                    maxQ = Q[state][a.ordinal()];
                    action = a;
                }
            }
            System.out.println("Day " + t + ": Price = " + currentPrice + ", Action = " + action);
            double reward = executeAction(action, currentPrice, t);
            totalReward += reward;
        }

        System.out.println("Test Run: Total Reward = " + totalReward);
        System.out.println("Final Money: $" + money);
        System.out.println("Final Stocks: " + numStocks);

        System.out.println("Net worth: $" + (money + numStocks * stockPrices.get(stockPrices.size() - 1)));
    }

    public static void main(String[] args) {
        // Example stock prices
        ArrayList<Float> stockPrices = new ArrayList<>();
        // Simple synthetic data: sine wave with noise
        for (int i = 0; i < 1000; i++) {
            stockPrices.add((float) (20 + 10 * Math.sin(i * 0.1)));
        }

        RLStockExample trader = new RLStockExample(stockPrices);
        trader.train(1000); // Train for 1000 episodes
        trader.test(); // Test the trained agent

        // Optionally, print Q-Table
        System.out.println("Q-Table:");
        for (int i = 0; i < STATE_SIZE; i++) {
            switch (i) {
                case 0:
                    System.out.print("Subiendo: ");
                    break;
                case 1:
                    System.out.print("Bajando: ");
                    break;
                case 2:
                    System.out.print("Estable: ");
                    break;
            }
            for (Action action : Action.values()) {
                System.out.print(action + "=" + trader.Q[i][action.ordinal()] + " ");
            }
            System.out.println();
        }
    }
}
