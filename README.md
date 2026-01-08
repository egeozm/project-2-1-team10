# Can't Stop Game Implementation

A complete implementation of the Can't Stop board game with multiple AI players and a graphical user interface.

## Overview

This project implements the Can't Stop dice game with:
- Graphical user interface using libGDX
- Multiple AI player implementations (Expectiminimax, MCTS, Rule-based)
- Simulation tools for AI evaluation and testing
- Complete game logic and rule enforcement

## Game Rules

Can't Stop is a dice game where players race to complete three columns on the board. Players roll four dice, pair them into two sums, and advance markers on columns corresponding to those sums. Players can continue rolling or stop to commit their progress. The first player to complete three columns wins.

## Project Structure

```
core/
  src/main/java/io/github/cantstop/
    backend/          # Game logic and AI implementations
      AI_Expectiminimax/  # Expectiminimax algorithm
      AI_MCTS/            # Monte Carlo Tree Search
      AI_RuleBased/       # Rule-based baseline
      Simulations/        # Testing and simulation tools
      MatchHistory/       # Game result storage
    frontend/         # Graphical user interface
    results/          # Simulation results

lwjgl3/              # Desktop platform launcher
assets/              # Game assets (images, sounds, fonts)
```

## Building and Running

### Prerequisites

- Java 17 or higher
- Gradle (wrapper included)

### Build the Project

```bash
./gradlew build
```

### Run the Game

```bash
./gradlew lwjgl3:run
```

### Build Executable JAR

```bash
./gradlew lwjgl3:jar
```

The JAR file will be located at `lwjgl3/build/libs/`.

## AI Implementations

### Expectiminimax

A game-tree search algorithm that handles chance nodes (dice rolls) and decision nodes. Supports both fixed-depth and time-limited iterative deepening search.

**Key Features:**
- Transposition table for caching
- Alpha-beta pruning
- Heuristic evaluation function
- Configurable search depths

**Usage:**
```bash
java -cp "core/build/classes/java/main:lib/*" \
  io.github.cantstop.model.Simulations.SimulationDemoExpectiminimax \
  [games] [rollDepth] [stopDepth] [seed] [verbose] [perMoveMillis]
```

### Monte Carlo Tree Search (MCTS)

A probabilistic search algorithm that uses random simulations to evaluate game states.

**Usage:**
```bash
java -cp "core/build/classes/java/main:lib/*" \
  io.github.cantstop.model.Simulations.SimulationDemoMCTS \
  [games] [iterations] [rolloutMaxRolls] [seed] [verbose]
```

### Rule-Based

A simple heuristic-based player that uses basic game rules and probabilities.

**Usage:**
```bash
java -cp "core/build/classes/java/main:lib/*" \
  io.github.cantstop.model.Simulations.SimulationDemoRuleBased \
  [games] [seed] [verbose]
```

## Simulation Tools

### Interactive Terminal

Run head-to-head matches between different AI configurations:

```bash
java -cp "core/build/classes/java/main:lib/*" \
  io.github.cantstop.model.Simulations.SimulationTerminal
```

This provides an interactive interface to configure agents and run matches.

### Batch Simulations

Run automated simulations for testing and evaluation:

```bash
# Expectiminimax self-play
java -cp "core/build/classes/java/main:lib/*" \
  io.github.cantstop.model.Simulations.SimulationDemoExpectiminimax 50 3 4

# MCTS vs Rule-based
java -cp "core/build/classes/java/main:lib/*" \
  io.github.cantstop.model.Simulations.SimulationTerminal
```

## Configuration

### Expectiminimax Parameters

- **rollDepth**: Search depth for evaluating ROLL decisions (chance nodes)
- **stopDepth**: Search depth for evaluating STOP decisions (deterministic)
- **Time budget**: Milliseconds per move for iterative deepening

Default values: rollDepth=3, stopDepth=4, timeBudget=50ms

### MCTS Parameters

- **Iterations**: Number of MCTS iterations per decision
- **Exploration constant (C)**: UCB1 exploration parameter
- **Rollout depth**: Maximum depth for random rollouts

## Development

### Compile Only

```bash
./gradlew :core:compileJava
```

### Run Tests

```bash
./gradlew test
```

### Clean Build

```bash
./gradlew clean
```

## Project Details

- **Language**: Java 17
- **Framework**: libGDX
- **Build System**: Gradle
- **Platform**: Desktop (LWJGL3)

## License

This project is part of an academic course assignment.
