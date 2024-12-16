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
    private static final int STATE_SIZE = Momentum.values().length * MoneyState.values().length
            * StockState.values().length; // Number of discrete states

    private double epsilon = 1.0; // Start with high exploration rate
    private double minEpsilon = 0.01;
    private double epsilonDecay = 0.995;

    private float inflationRate = 0.02f; // Example inflation rate
    private float commissionFee = 0.01f; // Example commission fee

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

    enum Momentum {
        UP, DOWN, STABLE
    }

    private static final double PRICE_CHANGE_THRESHOLD = 0.01; // 1% threshold for significant price change

    // Calculate momentum as additional state feature
    private Momentum calculateMomentum(int t) {
        if (t == 0) {
            return Momentum.STABLE;
        }
        float currentPrice = stockPrices.get(t);
        float prevPrice = stockPrices.get(t - 1);
        float priceChange = (currentPrice - prevPrice) / prevPrice; // Calculate percentage change

        if (Math.abs(priceChange) < PRICE_CHANGE_THRESHOLD) {
            return Momentum.STABLE;
        } else if (priceChange > 0) {
            return Momentum.UP;
        } else {
            return Momentum.DOWN;
        }
    }

    public enum MoneyState {
        CAN_AFFORD,
        CANNOT_AFFORD
    }

    public enum StockState {
        CAN_SELL,
        CANNOT_SELL
    }

    private MoneyState getMoneyState(float currentPrice) {
        return (money >= currentPrice) ? MoneyState.CAN_AFFORD : MoneyState.CANNOT_AFFORD;
    }

    private StockState getStockState() {
        return (numStocks > 0) ? StockState.CAN_SELL : StockState.CANNOT_SELL;
    }

    private int getState(int t, float currentPrice, float prevPrice, float movingAverage) {
        Momentum momentum = calculateMomentum(t);
        MoneyState moneyState = getMoneyState(currentPrice);
        StockState stockState = getStockState();

        // Combine states into single integer
        return momentum.ordinal() * (MoneyState.values().length * StockState.values().length) +
                moneyState.ordinal() * StockState.values().length +
                stockState.ordinal();
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
                return money >= price;
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

    // Execute action and return reward based on the change in total assets
    private double executeAction(Action action, float price, int t) {
        float futurePrice = (t + 1 < stockPrices.size()) ? stockPrices.get(t + 1) : price;

        float initialMoney = money;
        float initialStocks = numStocks;
        float currentTotalAsset = money + numStocks * price;

        // Calculate future assets only for valid actions
        ArrayList<Float> possibleFutureAssets = new ArrayList<>();

        // Always add HOLD scenario
        possibleFutureAssets.add(initialMoney + initialStocks * futurePrice);

        // Only add BUY scenario if we can afford it
        if (initialMoney >= price) {
            float buyFutureAsset = (initialMoney - price) + (initialStocks + 1) * futurePrice;
            possibleFutureAssets.add(buyFutureAsset);
        }

        // Only add SELL scenario if we have stocks
        if (initialStocks > 0) {
            // Add SELL scenario with commission fee
            float sellPrice = price * (1.0f - commissionFee); // Apply commission fee
            possibleFutureAssets.add((initialMoney + sellPrice) + (initialStocks - 1) * futurePrice);
        }

        // Find best possible future asset among valid actions
        float bestFutureAsset = possibleFutureAssets.stream()
                .max(Float::compareTo)
                .orElse(currentTotalAsset);

        // Execute the chosen action (which we know is valid due to chooseAction checks)
        switch (action) {
            case BUY:
                money -= price;
                numStocks += 1.0f;
                break;
            case SELL:
                money += price * (1.0f - commissionFee); // Apply commission fee when selling
                numStocks -= 1.0f;
                break;
            case HOLD:
                break;
        }

        money -= inflationRate * money;

        float actualFutureAsset = money + numStocks * futurePrice;

        // If the chosen action was the best possible, return natural profit
        if (Math.abs(actualFutureAsset - bestFutureAsset) < 0.001) {
            return actualFutureAsset - currentTotalAsset;
        }
        // Otherwise return the difference between actual and best possible valid
        // outcome
        return actualFutureAsset - bestFutureAsset;
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

            // Decay epsilon
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
                            ", can_afford: " + (money > stockPrices.get(t)) +
                            ", can_sell: " + (numStocks > 0) +
                            ", Momentum: " + calculateMomentum(t) +
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
            float price = (float) (50 + 50 * Math.sin(i * 0.05));
            // Round to 2 decimal places
            price = Math.round(price * 100.0f) / 100.0f;
            stockPrices.add(price);
        }

        RLStockExample trader = new RLStockExample(stockPrices);
        trader.train(1000); // Train for 1000 episodes
        trader.test(); // Test the trained agent

        // Print Q-Table
        System.out.println("\nQ-Table:");
        System.out.printf("%-20s %-10s %-15s %-15s %-10s %-10s %-10s\n", "State", "Momentum", "Money", "Stock", "BUY",
                "SELL", "HOLD");
        for (Momentum mom : Momentum.values()) {
            for (MoneyState money : MoneyState.values()) {
                for (StockState stock : StockState.values()) {
                    int state = mom.ordinal() * (MoneyState.values().length * StockState.values().length) +
                            money.ordinal() * StockState.values().length +
                            stock.ordinal();

                    System.out.printf("%-20d %-10s %-10s %-10s", state, mom, String.format("%-15s", money),
                            String.format("%-15s", stock));

                    for (Action action : Action.values()) {
                        System.out.printf("%-10.2f ", trader.Q[state][action.ordinal()]);
                    }
                    System.out.println();
                }
            }
        }
    }
}
