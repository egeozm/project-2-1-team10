# ANN Agent Guide (Expectiminimax/MCTS Imitation)

This ANN is a small, fast policy that learns from a search-based teacher (Expectiminimax or MCTS) and then plays on its own (no search at runtime). All training data comes from simulated self-play via `AnnDatasetGenerator`.


## Architecture
- Trunk: 49 inputs → 128 → 64 (ReLU)
- Roll/Stop head: 64 → 1 logit (sigmoid ⇒ P(roll))
- Move head: concat(stateEmbedding64 + moveFeat26 = 90) → 64 → 1 logit per legal move (choose argmax)
- Size: ~tens of thousands of params; inference is millisecond-scale.

## Features (computed from GameState)
- State (49 floats):
  - Per column (sums 2..12): mePerm/maxH, oppPerm/maxH, tempGain/maxH, isLocked
  - Global: activeTempCols/3, bustProb (from BustTable), progressValue (avg temp gain), meColsCompleted/3, oppColsCompleted/3
- Move (26 floats):
  - One-hot sumA (11) + one-hot sumB (11) (zeros if single)
  - isSingle (0/1), opensNewRunnerCount/2, closesColumnNow (0/1), sameColumn (sumA==sumB>0)

## Data generation (teacher = Expectiminimax or MCTS)
`AnnDatasetGenerator` simulates games teacher-vs-teacher and writes:
- `roll_stop*.bin`: state → roll/stop label
− `move_pairs*.bin`: state+move → chosen/not

If `roll_stop.bin`/`move_pairs.bin` already exist in the output dir, a timestamp suffix is added to avoid overwrites.

### Example: Generate 1,000 games with MCTS teacher
```bash
java -cp "core/build/classes/java/main:lib/*" \
  io.github.cantstop.model.ai.AI_ANN.AnnDatasetGenerator \
  ann_data_mcts 1000 123 \
  0 0 0 0 0 0 \
  mcts 1000000 0.25 10 25.0 0.3 200
# args: outDir games seed timed perMoveMs rollDepth stopDepth maxRollDepth maxStopDepth \
#       teacher(mcts|expecti) mctsIterations mctsC mctsRolloutMax mctsDpwK mctsDpwAlpha mctsTimeMs
```

### Example: Generate with Expectiminimax teacher (timed 5ms)
```bash
java -cp "core/build/classes/java/main:lib/*" \
  io.github.cantstop.model.ai.AI_ANN.AnnDatasetGenerator \
  ann_data_exp 2000 42 \
  1 5 3 4 0 0 \
  expecti
# timed=1 perMoveMs=5 rollDepth=3 stopDepth=4 (max depths 0 = unlimited)
```

## Training (binary data; trains both heads)
```bash
java -cp "core/build/classes/java/main:lib/*" \
  io.github.cantstop.model.ai.AI_ANN.AnnTrainer \
  ann_data_mcts/roll_stop.bin ann_data_mcts/move_pairs.bin ann_weights_mcts.annw \
  10 256 0.001 0.00001 42
# args: rollBin moveBin outWeights epochs batch lr l2 seed
```

## Evaluation (headless)
```bash
java -cp "core/build/classes/java/main:lib/*" \
  io.github.cantstop.view.terminal_simulations.SimulationDemoANN \
  500 123 ann_weights_mcts.annw 0.45 rule   # opponent: rule|ann|mcts
```
- `rollThreshold`: sigmoid cutoff for roll vs stop; 0.40–0.50 are reasonable; tune by sweeping.

## Example results (MCTS-trained weights vs Rule-based, 500 games, seed=123)
- thr=0.40 → win rate ≈ 0.70
- thr=0.45 → win rate ≈ 0.68
- thr=0.50 → win rate ≈ 0.69
Against similarly configured MCTS, expect ~50% (the ANN is imitating that policy).

## Components
- Data gen: `core/src/main/java/io/github/cantstop/model/ai/AI_ANN/AnnDatasetGenerator.java`
- Training: `AnnTrainer.java`
- Model/feats: `AnnNetwork.java`, `AnnFeatureExtractor.java`, `AnnConstants.java`
- Runtime agent: `AnnPlayer.java`
- Demo sim: `SimulationDemoANN.java`

## Notes
- Bust probability is computed only in the binary pipeline.
- Perspective is always “current player” (me vs opp) in feature extraction.
- Data files are recreated per run; existing names get a timestamp suffix.
# ANN Agent Guide (Expectiminimax/MCTS Imitation)

This ANN is a small, fast policy that learns from a search-based teacher (Expectiminimax or MCTS) and then plays on its own (no search at runtime). CSV training is not used here; all training data comes from simulated self-play via `AnnDatasetGenerator`.

## Architecture (as implemented)
- Trunk: 49 inputs → 128 → 64 (ReLU)
- Roll/Stop head: 64 → 1 logit (sigmoid ⇒ P(roll))
- Move head: concat(stateEmbedding64 + moveFeat26 = 90) → 64 → 1 logit per legal move (choose argmax)
- Size: ~tens of thousands of params; inference is millisecond-scale.

## Features (deterministic from GameState)
- State (49 floats):
  - Per column (sums 2..12): mePerm/maxH, oppPerm/maxH, tempGain/maxH, isLocked
  - Global: activeTempCols/3, bustProb (from BustTable), progressValue (avg temp gain), meColsCompleted/3, oppColsCompleted/3
- Move (26 floats):
  - One-hot sumA (11) + one-hot sumB (11) (zeros if single)
  - isSingle (0/1), opensNewRunnerCount/2, closesColumnNow (0/1), sameColumn (sumA==sumB>0)

## Data generation (teacher = Expectiminimax or MCTS)
`AnnDatasetGenerator` simulates games teacher-vs-teacher and writes:
- `roll_stop*.bin`: state → roll/stop label
- `move_pairs*.bin`: state+move → chosen/not

If `roll_stop.bin`/`move_pairs.bin` already exist in the output dir, a timestamp suffix is added to avoid overwrites.

### Example: Generate 1,000 games with MCTS teacher
```bash
java -cp "core/build/classes/java/main:lib/*" \
  io.github.cantstop.model.ai.AI_ANN.AnnDatasetGenerator \
  ann_data_mcts 1000 123 \
  0 0 0 0 0 0 \
  mcts 1000000 0.25 10 25.0 0.3 200
# args: outDir games seed timed perMoveMs rollDepth stopDepth maxRollDepth maxStopDepth \
#       teacher(mcts|expecti) mctsIterations mctsC mctsRolloutMax mctsDpwK mctsDpwAlpha mctsTimeMs
```

### Example: Generate with Expectiminimax teacher (timed 5ms)
```bash
java -cp "core/build/classes/java/main:lib/*" \
  io.github.cantstop.model.ai.AI_ANN.AnnDatasetGenerator \
  ann_data_exp 2000 42 \
  1 5 3 4 0 0 \
  expecti
# timed=1 perMoveMs=5 rollDepth=3 stopDepth=4 (max depths 0 = unlimited)
```

## Training (binary data; trains both heads)
```bash
java -cp "core/build/classes/java/main:lib/*" \
  io.github.cantstop.model.ai.AI_ANN.AnnTrainer \
  ann_data_mcts/roll_stop.bin ann_data_mcts/move_pairs.bin ann_weights_mcts.annw \
  10 256 0.001 0.00001 42
# args: rollBin moveBin outWeights epochs batch lr l2 seed
```

## Evaluation (headless)
```bash
java -cp "core/build/classes/java/main:lib/*" \
  io.github.cantstop.view.terminal_simulations.SimulationDemoANN \
  500 123 ann_weights_mcts.annw 0.45 rule   # opponent: rule|ann|mcts
```
- `rollThreshold`: sigmoid cutoff for roll vs stop; 0.40–0.50 are reasonable; tune by sweeping.

## Example results (MCTS-trained weights vs Rule-based, 500 games, seed=123)
- thr=0.40 → win rate ≈ 0.70
- thr=0.45 → win rate ≈ 0.68
- thr=0.50 → win rate ≈ 0.69
Against similarly configured MCTS, expect ~50% (the ANN is imitating that policy).

## Components to know
- Data gen: `core/src/main/java/io/github/cantstop/model/ai/AI_ANN/AnnDatasetGenerator.java`
- Training: `AnnTrainer.java` (full heads, binary data)
- Model/feats: `AnnNetwork.java`, `AnnFeatureExtractor.java`, `AnnConstants.java`
- Runtime agent: `AnnPlayer.java`
- Demo sim: `SimulationDemoANN.java`
- Teacher options: Expectiminimax (timed/depth) or MCTS (iterations, C, rollout, DPW, time)

## Notes and assumptions
- Perspective is always “current player” (me vs opp) in feature extraction.
- Data files are recreated per run; existing names get a timestamp suffix.
