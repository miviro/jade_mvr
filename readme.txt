This is an implementation of a GUI and Main Agent model to simulate tournaments of the Steal-Split game between agents.

Additional functionalities:
    - Clicking on the Last Actions cell of an agent will open its game history.
    - Dynamic agent creation and destruction, no need to relaunch the program.
    - Real time stat updates.
    - Clicking the stock cell shows when an agent bought and sold in a graph of the stock.

Folder structure:
jade_mvr/
│
├── lib/
│   └── jade.jar
│
├── bin/
│
└── src/
    ├── agents/
    │   ├── Agent1.java
    │   ├── Agent2.java
    │   └── Agent3.java
    └── jade_mvr/
        ├── MainAgent.java
        └── GUI.java

To compile:
    Windows:
        javac --release 17 -d bin/ -cp lib/* src/agents/*.java src/jade_mvr/*.java
    Linux/Mac:
        javac --release 17 -d bin/ -cp "lib/*" src/agents/*.java src/jade_mvr/*.java

To execute:
    Windows:
        java -classpath "lib/*;bin/" jade.Boot -notmp -gui -agents "MainAgent:src.jade_mvr.MainAgent; "c
    Linux/Mac:
        java -classpath "lib/*:bin/" jade.Boot -notmp -gui -agents "MainAgent:src.jade_mvr.MainAgent; "

Notes:
    - Execute commands in the jade_mvr folder.
    - We only run the MainAgent, which will be in charge of starting and killing new agents as requested by the user.
    - Agents participating in the tournaments should have its .java file placed on the src/agents/ folder.
    - Java 17 is required.
    - LLMs used for some code.
    - Do not run it in VSCode, it will crash with many rounds and/or agents.
    - Sending command output to /dev/null will help with performance.


RL explanation:
Uses StateAction (from moovi) to map states to action values. 
Uses vGetNewActionAutomata_PD or vGetNewActionAutomata_Stock to select respective actions based on learned probabilities from rewards (probabilistic selection).
We reduce the % of money invested as the rounds go by, to minizime risk at the end.
Learning rate decays for stabilizing learning.
PD rewards are calculated based on the outcome of the game against every single player.
Stock market rewards are calculated by stock price changes relative to inflation rates (positive for profit and negative for losses).
Every agent keeps a detailed state of money, stock, historical stock prices, historical inflation rates, history of games played against each player to feed to RL.

NN explanation:
The Neural Network component utilizes a SOM to analyze and adapt to Stock Market and PD.
Uses two separate SOM instances (`somStockMarket` and `somPrisonersDilemma`) process normalized inputs to determine strategic actions.
The SOMs learn patterns over time, enabling the agent to make informed buy/sell decisions in the stock market and choose C or D strategies in the PD based on historical data.
The SOM is a 2D grid of iGridSide. We put random vectors on each neuron to add entropy to the agent.
When a BMU (Best Matching Unit) gets updated, the neurons close to it (iRadio) also get updated. This is decayed through dDecLearnRate. The radio is also decayed.
WE normalize inputs to train with stable values (not a good idea to use a 100000 as money for training).
Different stock market actions depending on how much to buy/sell (Buy/Sell Large/Mid/Small). Same for PD but with two outputs (C or D) SOMs.
Inputs: currentMoney, currentStocks, currentStockPrice, currentInflation, currentStockTrend

PSI_25 explanation:
Very simple agent that has the same skeleton as the other ones. We use simple heuristics for choosing our actions.
For the stock market, we buy if if we had bought on the last round, the benefit would outweight the inflation and stock fee if we sold this round. Else we sell.
We reduce the % of money invested as the rounds go by, to minizime risk at the end.
For the PD game, we check our opponent history in a window of 20 actions:
    If they decided to betray us (we played C and the next turn they played D), we will play D against them as long as that is on the window.
    If we betrayed them, and they did not change their action to play D against us, we will continue betraying since it shows they do not retailiate.
    Else we default to C, with a small chance to flip our action in case we can betray them without consequence.

Miguel Vila Rodríguez, 11-2024