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
    private static final double ALPHA = 0.1; // Increased learning rate
    private static final double GAMMA = 0.9; // Adjusted discount factor
    private static final int STATE_SIZE = CycleState.values().length; // cycle states

    private double epsilon = 1.0; // Start with high exploration rate
    private double minEpsilon = 0.01;
    private double epsilonDecay = 0.9999;

    private float inflationRate = 0.01f; // Example inflation rate
    private float commissionFee = 0.0001f; // Example commission fee

    // Q-Table
    private double[][] Q;

    // Stock data
    private ArrayList<Float> stockPrices;

    // Agent's money and stocks
    private float money;
    private float numStocks;

    private Random random;

    // Add a new parameter for the reward lookahead window
    private static final int REWARD_LOOKAHEAD = 5; // Adjust as needed

    public RLStockExample(ArrayList<Float> stockPrices) {
        this.stockPrices = stockPrices;
        this.Q = new double[STATE_SIZE][Action.values().length];
        this.money = 1000.0f; // Starting with $1000
        this.numStocks = 0.0f;
        this.random = new Random();
    }

    public enum CycleState {
        ACCUMULATION, // Bottoming, good to start buying
        MARKUP, // Uptrend, hold or take profits
        DISTRIBUTION, // Topping, good to start selling
        MARKDOWN // Downtrend, hold cash
    }

    private static final int TREND_WINDOW = 10;
    private static final float TREND_THRESHOLD = 0.02f;
    private static final float VOLATILITY_THRESHOLD = 0.015f;
    private static final int MAX_STOCKS = 3; // Limit position size

    private CycleState detectCycleState(int t) {
        if (t < TREND_WINDOW) {
            return CycleState.ACCUMULATION;
        }

        float currentPrice = stockPrices.get(t);
        float[] returns = new float[TREND_WINDOW];
        float sumReturns = 0;
        float sumVolatility = 0;

        // Calculate returns and volatility
        for (int i = 0; i < TREND_WINDOW; i++) {
            float prev = stockPrices.get(t - i - 1);
            float curr = stockPrices.get(t - i);
            returns[i] = (curr - prev) / prev;
            sumReturns += returns[i];
            sumVolatility += Math.abs(returns[i]);
        }

        float avgReturn = sumReturns / TREND_WINDOW;
        float volatility = sumVolatility / TREND_WINDOW;

        // Detect market phase
        if (avgReturn > TREND_THRESHOLD) {
            return (volatility > VOLATILITY_THRESHOLD) ? CycleState.DISTRIBUTION : CycleState.MARKUP;
        } else if (avgReturn < -TREND_THRESHOLD) {
            return (volatility > VOLATILITY_THRESHOLD) ? CycleState.ACCUMULATION : CycleState.MARKDOWN;
        } else {
            return (avgReturn > 0) ? CycleState.MARKUP : CycleState.MARKDOWN;
        }
    }

    private int getState(int t, float currentPrice, float prevPrice, float movingAverage) {
        CycleState cycleState = detectCycleState(t);
        return cycleState.ordinal();
    }

    // Choose action using epsilon-greedy policy with validity check
    private Action chooseAction(int state, float currentPrice) {
        if (random.nextDouble() < epsilon) {
            // Exploration with valid actions only
            ArrayList<Action> validActions = new ArrayList<>();
            for (Action a : Action.values()) {
                if (isValidAction(a, currentPrice)) {
                    validActions.add(a);
                }
            }
            return validActions.get(random.nextInt(validActions.size()));
        } else {
            // Exploitation with valid actions only
            double maxQ = Double.NEGATIVE_INFINITY;
            Action bestAction = Action.HOLD;
            for (Action action : Action.values()) {
                if (isValidAction(action, currentPrice) && Q[state][action.ordinal()] > maxQ) {
                    maxQ = Q[state][action.ordinal()];
                    bestAction = action;
                }
            }
            return bestAction;
        }
    }

    // Helper method to check if an action is valid
    private boolean isValidAction(Action action, float price) {
        switch (action) {
            case BUY:
                return money >= price && numStocks < MAX_STOCKS;
            case SELL:
                return numStocks > 0;
            case HOLD:
                return true;
            default:
                return false;
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

    // Modify the executeAction method
    private double executeAction(Action action, float price, int t) {
        // Update money and numStocks based on action
        switch (action) {
            case BUY:
                money -= price;
                numStocks += 1.0f;
                break;
            case SELL:
                money += price * (1.0f - commissionFee);
                numStocks -= 1.0f;
                break;
            case HOLD:
                break;
        }

        // Adjust money for inflation after action
        money -= inflationRate * money;

        float currentTotalAsset = money + numStocks * price;

        // Calculate future net worth over the lookahead window
        int lookaheadEnd = Math.min(t + REWARD_LOOKAHEAD, stockPrices.size() - 1);
        float futurePrice = stockPrices.get(lookaheadEnd);
        float futureTotalAsset = money + numStocks * futurePrice;

        // Calculate reward as the change in net worth over the lookahead window
        double reward = futureTotalAsset - currentTotalAsset;

        return reward;
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
                Action action = chooseAction(state, currentPrice);

                // Execute action and get reward based on future price
                double reward = executeAction(action, currentPrice, t);
                totalReward += reward;

                float nextPrice = stockPrices.get(t + 1);
                float nextMovingAverage = calculateMovingAverage(t + 1);
                int nextState = getState(t + 1, nextPrice, currentPrice, nextMovingAverage);

                updateQ(state, action, reward, nextState);
            }

            // Decay epsilon more slowly to encourage exploration
            epsilon = Math.max(minEpsilon, epsilon * epsilonDecay);

            // Optionally, print episode summary
            System.out.println("Episode " + (ep + 1) + ": Total Reward = " + totalReward + ", Epsilon = " + epsilon);
        }
    }

    private String formatFloat(float value) {
        return String.format("%06.2f", value);
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
                if (isValidAction(a, currentPrice) && Q[state][a.ordinal()] > maxQ) {
                    maxQ = Q[state][a.ordinal()];
                    action = a;
                }
            }
            System.out.println(
                    "Day " + t + ": Price = " + formatFloat(currentPrice) +
                            ", Cycle = " + CycleState.values()[state] +
                            ", Action = " + action +
                            ", Money = " + String.format("%-15s", formatFloat(money)) +
                            ", Stocks = " + String.format("%-15s", formatFloat(numStocks)));
            double reward = executeAction(action, currentPrice, t);
            totalReward += reward;
        }

        System.out.println("Test Run: Total Reward = " + formatFloat((float) totalReward));
        System.out.println("Final Money: $" + String.format("%-15s", formatFloat(money)));
        System.out.println("Final Stocks: " + String.format("%-15s", formatFloat(numStocks)));
        System.out.println("Net worth: $"
                + String.format("%-15s", formatFloat(money + numStocks * stockPrices.get(stockPrices.size() - 1))));
    }

    public static void main(String[] args) {
        // Example stock prices
        ArrayList<Float> stockPrices = new ArrayList<>();
        // Simple synthetic data: sine wave with noise
        for (int i = 0; i < 1000; i++) {
            float price = (float) (50 + 45 * Math.sin(i * 0.05));
            // Round to 2 decimal places
            price = Math.round(price * 100.0f) / 100.0f;
            stockPrices.add(price);
        }

        RLStockExample trader = new RLStockExample(stockPrices);
        trader.train(20000); // Train for 1000 episodes
        trader.test(); // Test the trained agent

        // Print Q-Table
        System.out.println("\nQ-Table:");
        System.out.printf("%-10s %-15s %-10s %-10s %-10s\n",
                "State", "Cycle", "BUY", "SELL", "HOLD");

        for (CycleState cycle : CycleState.values()) {
            int state = cycle.ordinal();
            System.out.printf("%-10d %-15s",
                    state, cycle);

            for (Action action : Action.values()) {
                System.out.printf("%-10.2f ", trader.Q[state][action.ordinal()]);
            }
            System.out.println();
        }
    }
}
