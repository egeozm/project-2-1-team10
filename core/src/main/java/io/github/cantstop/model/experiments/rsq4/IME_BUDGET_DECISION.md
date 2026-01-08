## ✅ Chosen Time Budget: 50ms

**Decision: Use 50ms time budget for all depth optimization experiments.**

### Rationale

After testing 25ms, 50ms, and 100ms time budgets with 5 games per configuration:

| Time Budget | Best Config | Win Rate | Avg Time | Notes |
|------------|-------------|----------|----------|-------|
| 25ms | rollDepth=2, stopDepth=6 | 100%* | 1,530 ms | Fast but high variance |
| **50ms** | **rollDepth=2, stopDepth=3** | **80%** | **2,929 ms** | **Best balance** ✅ |
| 100ms | rollDepth=2, stopDepth=3 | 80% | 6,287 ms | Similar quality, 2x slower |

*Note: 100% win rates with small samples are likely variance - expect ~50-60% with more games.

### Why 50ms?

1. **Performance**: Similar win rates to 100ms (80% in best configs)
2. **Speed**: 2x faster than 100ms (~3 sec/game vs ~6 sec/game)
3. **Consistency**: More reliable results than 25ms
4. **Efficiency**: Optimal balance for large-scale testing

### Time Savings

For 20 games per configuration:
- **50ms**: ~1 minute per config, ~9 minutes for 9 configs
- **100ms**: ~2 minutes per config, ~18 minutes for 9 configs
- **Savings**: ~9 minutes per test run

### Usage

**All future depth optimization should use 50ms:**
ash
# Standard command for depth optimization
java -cp "core/build/classes/java/main:lib/*" io.github.cantstop.model.Simulations.DepthOptimizer [games] [minRoll] [maxRoll] [minStop] [maxStop] 42 50**Example:**ash
# Test depths 2-4 for roll, 3-5 for stop, 15 games each
java -cp "core/build/classes/java/main:lib/*" io.github.cantstop.model.Simulations.DepthOptimizer 15 2 4 3 5 42 50This provides the best balance of speed and quality for systematic depth optimization.
