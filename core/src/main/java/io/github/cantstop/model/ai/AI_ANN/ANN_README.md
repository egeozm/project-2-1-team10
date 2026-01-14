# ANN Agent – Design, Data, Training, and Usage

This document summarizes everything implemented for the ANN-based Can’t Stop agent: architecture, feature encoding, data formats, training flows (binary dataset + CSV), runtime integration, and key assumptions.

## Components (code)
- `core/src/main/java/io/github/cantstop/model/ai/AI_ANN/AnnConstants.java` — dimensions and layer sizes.
- `core/src/main/java/io/github/cantstop/model/ai/AI_ANN/AnnFeatureExtractor.java` — deterministic feature builder for state/move.
- `core/src/main/java/io/github/cantstop/model/ai/AI_ANN/AnnNetwork.java` — MLP (trunk + roll head + move head), weight save/load.
- `core/src/main/java/io/github/cantstop/model/ai/AI_ANN/AnnDatasetIO.java` — binary dataset IO (roll/stop + move pairs).
- `core/src/main/java/io/github/cantstop/model/ai/AI_ANN/AnnDatasetGenerator.java` — teacher self-play generator (Expectiminimax/MCTS) → binary data.
- `core/src/main/java/io/github/cantstop/model/ai/AI_ANN/AnnTrainer.java` — trains from binary data (roll head + move head).
- `core/src/main/java/io/github/cantstop/model/ai/AI_ANN/AnnCsvTrainer.java` — trains roll/value head from a CSV (label = eventual win for current player).
- `core/src/main/java/io/github/cantstop/model/ai/AI_ANN/AnnPlayer.java` — runtime ANN agent implementing `IPlayerController`.
- `core/src/main/java/io/github/cantstop/view/terminal_simulations/SimulationDemoANN.java` — non-interactive evaluator for ANN vs baselines.
- `core/src/main/java/io/github/cantstop/view/terminal_simulations/SimulationTerminal.java` — interactive UI now includes ANN option.
- `core/src/main/java/io/github/cantstop/model/match_history/AgentType.java` / `AgentSpec.java` — added `ANN` agent type/config.

## Architecture
- Trunk: `STATE_DIM=49` → 128 → 64 (ReLU).
- Roll/Stop head: 64 → 1 logit (sigmoid for P(roll)).
- Move scorer head: concat(stateEmbedding64, moveFeat26) → 64 → 1 logit per legal move (choose argmax).

## Feature encoding (as implemented)
### State (49 floats)
- For each sum 2..12 (11 columns):
  - `mePerm / maxHeight`
  - `oppPerm / maxHeight`
  - `tempGain = max(tempAtCol - mePerm, 0) / maxHeight`
  - `isLocked` (1 if either player completed the column)
- Globals:
  - `activeTempColumns / 3`
  - `bustProb` (from `BustTable`; set to 0 if unknown in CSV)
  - `progressValue` (avg normalized tempGain across columns)
  - `meColumnsCompleted / 3`
  - `oppColumnsCompleted / 3`

### Move (26 floats)
- One-hot `sumA` (11) + one-hot `sumB` (11) (zeros if single/absent)
- `isSingle` (0/1)
- `opensNewRunnerCount / 2` (0, 0.5, 1.0)
- `closesColumnNow` (0/1)
- `sameColumn` (1 if `sumA == sumB > 0`)

## Data formats
### Binary (preferred for full imitation, includes move labels)
- `roll_stop.bin`:
  - Header magic `ANNR`, version int, stateDim int (49)
  - Repeated records: 49 floats + 1 byte (`roll=1`, `stop=0`)
- `move_pairs.bin`:
  - Header magic `ANNP`, version int, stateDim int (49), moveDim int (26)
  - Repeated records: 49 floats + 26 floats + 1 byte (`chosen=1/0`)
- Readers/writers: `AnnDatasetIO`.
- Generator: `AnnDatasetGenerator` (teacher self-play).
- Trainer: `AnnTrainer` (trunk + roll head + move head, pairwise logistic for moves).

### CSV (roll/value head only; no move supervision)
- Required columns (36 total, header order matters):
  - `red_col_2..red_col_12` (11)
  - `blue_col_2..blue_col_12` (11)
  - `temp_col_2..temp_col_12` (11)
  - `current_player` (0 = RED, 1 = BLUE) — assumption; adjust in code if different.
  - `runners_remaining` (0..3, currently unused in features)
  - `did_win_label` (0/1, target: did current player eventually win)
- Assumptions:
  - Heights already normalized by each column’s max height.
  - temp columns are normalized the same way.
  - Bust probability is not derivable from CSV; set to 0 in `AnnCsvTrainer`.
  - Only the roll/value head is trained from CSV; move head is not updated.
- Trainer: `AnnCsvTrainer`.

## Typical workflows
### 1) Generate binary dataset from teacher (Expectiminimax timed 5 ms)
```bash
cd /Users/egeozm/team_10
java -cp "core/build/classes/java/main:lib/*" \
  io.github.cantstop.model.ai.AI_ANN.AnnDatasetGenerator \
  ann_data 2000 42 1 5 3 4 0 0
# args: outDir, games, seed, timed(1/0), perMoveMs, rollDepth, stopDepth, maxRollDepth, maxStopDepth
```

### 2) Train from binary data (full imitation incl. move head)
```bash
java -cp "core/build/classes/java/main:lib/*" \
  io.github.cantstop.model.ai.AI_ANN.AnnTrainer \
  ann_data/roll_stop.bin ann_data/move_pairs.bin ann_weights.annw \
  10 256 0.001 0.00001 42
# args: rollBin, moveBin, outWeights, epochs, batch, lr, l2, seed
```

### 3) Train from CSV (roll/value head only)
```bash
java -cp "core/build/classes/java/main:lib/*" \
  io.github.cantstop.model.ai.AI_ANN.AnnCsvTrainer \
  core/src/main/java/io/github/cantstop/results/mcts_training_data.csv \
  ann_weights_from_csv.annw \
  5 256 0.001 0.00001 123
# args: csvPath, outWeights, epochs, batch, lr, l2, seed
```

### 4) Evaluate ANN vs Rule-based (non-interactive)
```bash
java -cp "core/build/classes/java/main:lib/*" \
  io.github.cantstop.view.terminal_simulations.SimulationDemoANN \
  200 123 ann_weights.annw 0.45 rule
# args: games, seed, weightsPath, rollThreshold, opponent(rule|ann)
```

### 5) Interactive selection (ANN option)
```bash
java -cp "core/build/classes/java/main:lib/*" \
  io.github.cantstop.view.terminal_simulations.SimulationTerminal
# choose option 5 (ANN), provide weights path and roll threshold when prompted
```

## Key assumptions and gotchas
- **Normalization**: All height features (perm/temp) are pre-normalized by column max height. `AnnDatasetGenerator` does this automatically; CSV must already be normalized.
- **Perspective**: Features are from the current player’s viewpoint (`me`, `opp`). CSV trainer assumes `current_player=0` means RED; flip logic if your CSV differs.
- **Bust probability**: In binary generation, computed via `BustTable`; in CSV training it is set to 0 (no dice info).
- **Move head training**: Requires per-legal-move labels (binary dataset). The CSV flow does not update the move head.
- **Threshold**: Runtime uses a roll threshold on the sigmoid output (e.g., 0.45–0.5); can be tuned at launch.
- **Teacher labeling fix**: `ExpectiminimaxPlayer.chooseAction` was corrected (STOP vs BUST) to avoid bad labels; ensure you’re on this version.

## Files produced
- `ann_data/roll_stop.bin`, `ann_data/move_pairs.bin` — generated datasets (binary).
- `ann_weights.annw` — weights from binary training (full head).
- `ann_weights_from_csv.annw` — weights from CSV training (roll/value head).

## Minimal commands (copy-paste)
- Generate + train (binary):
```bash
java -cp "core/build/classes/java/main:lib/*" io.github.cantstop.model.ai.AI_ANN.AnnDatasetGenerator ann_data 2000 42 1 5 3 4 0 0
java -cp "core/build/classes/java/main:lib/*" io.github.cantstop.model.ai.AI_ANN.AnnTrainer ann_data/roll_stop.bin ann_data/move_pairs.bin ann_weights.annw 10 256 0.001 0.00001 42
```
- Train from CSV:
```bash
java -cp "core/build/classes/java/main:lib/*" io.github.cantstop.model.ai.AI_ANN.AnnCsvTrainer core/src/main/java/io/github/cantstop/results/mcts_training_data.csv ann_weights_from_csv.annw 5 256 0.001 0.00001 123
```
- Quick eval:
```bash
java -cp "core/build/classes/java/main:lib/*" io.github.cantstop.view.terminal_simulations.SimulationDemoANN 200 123 ann_weights.annw 0.45 rule
```

