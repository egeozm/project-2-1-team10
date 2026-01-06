# Finding Optimal Depths for ExpectiminimaxPlayer

## Overview

The `ExpectiminimaxPlayer` has two depth parameters:
- **rollDepth**: Depth for evaluating ROLL decisions (chance nodes over dice outcomes)
- **stopDepth**: Depth for evaluating STOP decisions (deterministic depth after committing temp runners)

## Understanding the Trade-offs

### Depth vs Performance
- **Higher depth** = Better decision quality, but exponentially slower
- **Lower depth** = Faster decisions, but may miss deeper strategic considerations

### Roll Depth vs Stop Depth
- **Roll depth** affects decisions during active turns (when rolling dice)
- **Stop depth** affects decisions when considering whether to stop and commit runners
- Typically: `stopDepth > rollDepth` because after stopping, the game state is more predictable

## Methods for Finding Optimal Depths

### Method 1: Systematic Grid Search (Recommended)

Use the `DepthOptimizer` tool to test all combinations:

```bash
# Basic usage: test depths 2-5 for roll, 3-6 for stop, 20 games each
java io.github.cantstop.model.Simulations.DepthOptimizer 20 2 5 3 6

# More comprehensive: test 1-4 for both, 50 games each
java io.github.cantstop.model.Simulations.DepthOptimizer 50 1 4 1 4

# With specific seed for reproducibility
java io.github.cantstop.model.Simulations.DepthOptimizer 50 2 5 3 6 42

# Test with time budget (iterative deepening instead of fixed depth)
java io.github.cantstop.model.Simulations.DepthOptimizer 30 1 4 1 4 42 100
```

**Interpretation:**
- Look for configurations with **win rate close to 50%** (balanced self-play)
- Consider **average time per game** - deeper isn't always better if it's too slow
- The tool shows top 3 configurations and best balanced (performance/time)

### Method 2: Manual Testing

Use `SimulationDemoExpectiminimax` to test specific configurations:

```bash
# Test rollDepth=3, stopDepth=4, 50 games
java io.github.cantstop.model.Simulations.SimulationDemoExpectiminimax 50 3 4

# Test rollDepth=4, stopDepth=5, 100 games, seed=42
java io.github.cantstop.model.Simulations.SimulationDemoExpectiminimax 100 4 5 42 false
```

### Method 3: Time-Based Optimization

Instead of fixed depths, use iterative deepening with time budgets:

```bash
# Test different time budgets (50ms, 100ms, 200ms, 500ms)
# Compare win rates and actual depths reached
```

## Recommended Approach

### Step 1: Initial Exploration
```bash
# Quick scan: test wide range with fewer games
java io.github.cantstop.model.Simulations.DepthOptimizer 10 1 5 1 6
```

### Step 2: Focused Testing
```bash
# Based on Step 1 results, test promising region with more games
java io.github.cantstop.model.Simulations.DepthOptimizer 50 2 4 3 5
```

### Step 3: Fine-tuning
```bash
# Test specific promising configurations with many games
java io.github.cantstop.model.Simulations.DepthOptimizer 100 3 3 4 4
```

### Step 4: Validation
```bash
# Run final configuration against baseline (e.g., MCTS or RuleBased)
# Use SimulationTerminal for head-to-head comparisons
```

## Expected Results

### Typical Optimal Ranges:
- **rollDepth**: 2-4 (chance nodes are expensive, diminishing returns after 3-4)
- **stopDepth**: 3-6 (deterministic search is faster, can go deeper)

### Performance Characteristics:
- **Depth 1-2**: Very fast, but weak play
- **Depth 3-4**: Good balance for most scenarios
- **Depth 5+**: Strong but slow, may not be worth the time cost

### Time Considerations:
- Fixed depth: Predictable time per move
- Iterative deepening: Adapts to time budget, often reaches depth 2-4 within 100ms

## Factors to Consider

1. **Opponent Strength**: If playing against weaker opponents, lower depths may suffice
2. **Time Constraints**: Real-time play may require lower depths
3. **Computational Resources**: More CPU allows deeper search
4. **Game Phase**: Early game may benefit from deeper search, late game may be faster

## Example Workflow

```bash
# 1. Quick exploration
java io.github.cantstop.model.Simulations.DepthOptimizer 20 1 5 1 6 > results1.txt

# 2. Analyze results, identify promising region (e.g., rollDepth 2-3, stopDepth 3-5)

# 3. Focused testing
java io.github.cantstop.model.Simulations.DepthOptimizer 50 2 3 3 5 > results2.txt

# 4. Fine-tune best candidates
java io.github.cantstop.model.Simulations.DepthOptimizer 100 3 3 4 4 > results3.txt

# 5. Validate against other AIs
java io.github.cantstop.model.Simulations.SimulationTerminal
# Configure one agent with optimal depths, another with MCTS/RuleBased
```

## Tips

1. **Use same seed** across runs for fair comparison
2. **Run multiple times** with different seeds to check consistency
3. **Consider time budget** - if you have 100ms per move, test with that constraint
4. **Watch for diminishing returns** - if depth 4 performs similarly to depth 5, prefer 4
5. **Balance depth ratio** - stopDepth should typically be 1-2 higher than rollDepth

## Current Defaults

The current defaults are:
- `DEFAULT_DEPTH_ROLL_PHASE = 3`
- `DEFAULT_DEPTH_AFTER_STOP = 4`

These are reasonable starting points, but may not be optimal for your specific use case or hardware.

