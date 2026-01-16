package io.github.cantstop.model.ai.Hybrid_Model;

import io.github.cantstop.controller.IPlayerController;
import io.github.cantstop.model.*;
import io.github.cantstop.model.ai.AI_MCTS.*;
import java.io.File;
import java.util.*;

public class HybridModel implements IPlayerController {
    private static final String WEIGHTS_PATH = "core/src/main/java/io/github/cantstop/model/ai/Hybrid_Model/model.weights";

    private final int iterations = 10000;
    private final double explorationC = 0.35;

    private final NeuralNetwork valueNetwork;
    private final Random internalRng = new Random();

    public HybridModel() {
        // Architecture must match the trainer
        this.valueNetwork = new NeuralNetwork(35, 256, 128, 1);
        loadWeights(WEIGHTS_PATH);
    }

    private void loadWeights(String path) {
        try (Scanner scanner = new Scanner(new File(path))) {
            for (Layer layer : valueNetwork.getLayers()) {
                if (!scanner.hasNextLine()) break;
                String[] wData = scanner.nextLine().split(",");
                double[][] weights = new double[layer.numNeurons][layer.numInputs];
                int k = 0;
                for (int i = 0; i < layer.numNeurons; i++) {
                    for (int j = 0; j < layer.numInputs; j++) {
                        weights[i][j] = Double.parseDouble(wData[k++]);
                    }
                }
                if (!scanner.hasNextLine()) break;
                String[] bData = scanner.nextLine().split(",");
                double[] biases = new double[layer.numNeurons];
                for (int i = 0; i < layer.numNeurons; i++) {
                    biases[i] = Double.parseDouble(bData[i]);
                }
                layer.importLayer(weights, biases);
            }
            System.out.println("HybridModel: NN Weights Loaded. Iterations=" + iterations + ", C=" + explorationC);
        } catch (Exception e) {
            System.err.println("HybridModel: Weight loading failed: " + e.getMessage());
        }
    }

    public MctsAction decide(GameState rootState, DiceRoll lastRoll) {
        final Player rootPlayer = rootState.getCurrentPlayer();
        List<MctsAction> legalAtRoot = legalActionsFrom(rootState, lastRoll);

        if (legalAtRoot.isEmpty()) return null;
        if (legalAtRoot.size() == 1) return legalAtRoot.get(0);

        Node root = new Node(null, Node.Type.DECISION, rootPlayer, legalAtRoot);

        for (int i = 0; i < iterations; i++) {
            GameState simState = rootState.copy();
            Node node = root;
            DiceRoll currentRoll = lastRoll;

            while (!isTerminal(simState)) {
                if (node.hasUntried()) {
                    MctsAction a = node.popUntried();
                    StepResult result = applyActionInPlace(simState, a);
                    node = node.addChild(a, Node.Type.DECISION, simState.getCurrentPlayer(),
                        legalActionsFrom(simState, result.lastRoll));
                    break;
                }
                if (node.isLeaf()) break;
                node = selectUCT(node, rootPlayer);
                StepResult result = applyActionInPlace(simState, node.actionFromParent);
                currentRoll = result.lastRoll;
            }

            double reward = evaluateHybridLeaf(simState, rootPlayer);
            backpropagate(node, reward);
        }
        return getBestAction(root);
    }

    private Node selectUCT(Node parent, Player rootPlayer) {
        Node best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        final double lnN = Math.log(Math.max(1, parent.visits));
        final double sign = (parent.playerToMove == rootPlayer) ? 1.0 : -1.0;

        for (Node ch : parent.children.values()) {
            double q = ch.mean();
            double u = explorationC * Math.sqrt(lnN / Math.max(1, ch.visits));
            double score = (sign * q) + u;

            if (score > bestScore) {
                bestScore = score;
                best = ch;
            }
        }
        return best;
    }

    private double evaluateHybridLeaf(GameState state, Player rootPlayer) {
        if (TurnManager.checkWinCondition(state, rootPlayer)) return 1.0;
        if (TurnManager.checkWinCondition(state, rootPlayer.opponent())) return 0.0;

        GameState rolloutState = state.copy();

        double rolloutValue = rolloutFrom(rolloutState, rootPlayer);

        double[] inputs = convertToNNInputs(rolloutState);
        double nnWinProb = valueNetwork.predict(inputs)[0];

        if (rolloutState.getCurrentPlayer() != rootPlayer) {
            nnWinProb = 1.0 - nnWinProb;
        }

        return (0.7 * rolloutValue) + (0.3 * nnWinProb);
    }

    private double rolloutFrom(GameState s, Player root) {
        int maxRolls = 6;
        int rollsCount = 0;

        while (!isTerminal(s) && rollsCount < maxRolls) {
            if (s.getTurnPhase() == TurnPhase.ROLL_OR_STOP) {
                if (s.countActiveColumns() == 3 || rollsCount >= 3) {
                    if (internalRng.nextDouble() < 0.8) {
                        TurnManager.stop(s);
                        break;
                    }
                }

                DiceRoll dr = TurnManager.roll(s, internalRng);
                List<Move> legal = TurnManager.getLegalMoves(s, dr);

                if (legal.isEmpty()) {
                    TurnManager.bust(s);
                    return 0.0;
                }

                TurnManager.noBust(s);

                Move bestMove = legal.get(0);
                double bestMoveScore = Double.NEGATIVE_INFINITY;

                for (Move m : legal) {
                    double currentMoveScore = 0;
                    int activeSums = 0;

                    if (m.sumA() != 0) {
                        currentMoveScore += (6 - Math.abs(m.sumA() - 7));
                        activeSums++;
                    }
                    if (m.sumB() != 0) {
                        currentMoveScore += (6 - Math.abs(m.sumB() - 7));
                        activeSums++;
                    }

                    if (activeSums > 0) currentMoveScore /= activeSums;

                    if (currentMoveScore > bestMoveScore) {
                        bestMoveScore = currentMoveScore;
                        bestMove = m;
                    }
                }

                TurnManager.applyMove(s, bestMove);
                rollsCount++;

            } else if (s.getTurnPhase() == TurnPhase.CHOOSE_MOVE) {
                List<Move> legal = TurnManager.getLegalMoves(s, s.getLastRoll());
                if (legal.isEmpty()) { TurnManager.bust(s); break; }
                TurnManager.applyMove(s, legal.get(0));
            }
        }
        return Heuristics.normalize01(Heuristics.evaluate(s, root));
    }

    private StepResult applyActionInPlace(GameState s, MctsAction a) {
        switch (a.kind) {
            case STOP: TurnManager.stop(s); return new StepResult(null);
            case ROLL:
                DiceRoll dr = TurnManager.roll(s, internalRng);
                if (TurnManager.getLegalMoves(s, dr).isEmpty()) {
                    TurnManager.bust(s);
                    return new StepResult(null);
                }
                TurnManager.noBust(s);
                return new StepResult(dr);
            case MOVE: TurnManager.applyMove(s, a.move); return new StepResult(null);
            default: return new StepResult(null);
        }
    }

    private List<MctsAction> legalActionsFrom(GameState s, DiceRoll lastRoll) {
        if (s.getTurnPhase() == TurnPhase.ROLL_OR_STOP) {
            return s.countActiveColumns() > 0
                ? Arrays.asList(MctsAction.stop(), MctsAction.roll())
                : Collections.singletonList(MctsAction.roll());
        }
        List<Move> moves = TurnManager.getLegalMoves(s, lastRoll);
        List<MctsAction> actions = new ArrayList<>();
        for (Move m : moves) actions.add(MctsAction.move(m));
        return actions;
    }

    private void backpropagate(Node node, double reward) {
        for (Node n = node; n != null; n = n.parent) {
            n.visits++;
            n.valueSum += reward;
        }
    }

    private MctsAction getBestAction(Node root) {
        return root.children.entrySet().stream()
            .max(Comparator.comparingInt(e -> e.getValue().visits))
            .map(Map.Entry::getKey).orElse(MctsAction.roll());
    }

    private boolean isTerminal(GameState s) {
        return TurnManager.checkWinCondition(s, Player.RED) || TurnManager.checkWinCondition(s, Player.BLUE);
    }

    private static class StepResult {
        final DiceRoll lastRoll;
        StepResult(DiceRoll lastRoll) { this.lastRoll = lastRoll; }
    }

    @Override
    public Boolean rollOrStop(GameState state) {
        MctsAction decision = decide(state, null);
        return decision == null || decision.isRoll();
    }

    @Override
    public Move selectMove(GameState state, List<Move> legalMoves) {
        MctsAction decision = decide(state, state.getLastRoll());
        if (decision != null && decision.isMove()) return decision.move;
        return legalMoves.get(0);
    }

    private double[] convertToNNInputs(GameState state) {
        double[] features = new double[35];
        int idx = 0;
        for (int col = 0; col < 11; col++) {
            double maxH = GameConstants.maxHeight(GameConstants.columnToSum(col));
            features[idx++] = state.redPermAtCol(col) / maxH;
        }
        for (int col = 0; col < 11; col++) {
            double maxH = GameConstants.maxHeight(GameConstants.columnToSum(col));
            features[idx++] = state.bluePermAtCol(col) / maxH;
        }
        for (int col = 0; col < 11; col++) {
            double maxH = GameConstants.maxHeight(GameConstants.columnToSum(col));
            features[idx++] = state.tempAtCol(col) / maxH;
        }
        features[33] = (state.getCurrentPlayer() == Player.RED) ? 0.0 : 1.0;
        features[34] = (3.0 - (double) state.countActiveColumns()) / 3.0;
        return features;
    }
}
