This is an implementation of a GUI and Main Agent model to simulate tournaments of the Steal-Split game between agents.

Additional functionalities:
    - Clicking on the Last Actions cell of an agent will open its game history.
    - Dynamic agent creation and destruction, no need to relaunch the program.
    - Real time stat updates.

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
        java -classpath "lib/*;bin/" jade.Boot -notmp -gui -agents "MainAgent:src.jade_mvr.MainAgent; "
    Linux/Mac:
        java -classpath "lib/*:bin/" jade.Boot -notmp -gui -agents "MainAgent:src.jade_mvr.MainAgent; "

Notes:
    - Execute commands in the jade_mvr folder.
    - We only run the MainAgent, which will be in charge of starting and killing new agents as requested by the user.
    - Agents participating in the tournaments should have its .java file placed on the src/agents/ folder.
    - Java 17 is required.
    - It is important Agents do not run System.exit(0) when they quit, or else the program will quit on Quit Game.

Miguel Vila Rodríguez, 11-2024