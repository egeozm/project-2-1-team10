package io.github.cantstop.model.ai.Hybrid_Model;

import io.github.cantstop.controller.IPlayerController;
import io.github.cantstop.model.*;
import io.github.cantstop.model.ai.AI_MCTS.MctsAction;
import io.github.cantstop.model.ai.AI_MCTS.Node;
import java.io.File;
import java.util.*;

public class HybridModel implements IPlayerController {
    private static final String WEIGHTS_PATH = "core/src/main/java/io/github/cantstop/model/ai/Hybrid_Model/model.weights";

    private static final int DEFAULT_ITERATIONS = 20000;
    private static final double DEFAULT_EXPLORATION = 3;

    private final NeuralNetwork valueNetwork;
    private final int iterations;
    private final double explorationC;
    private final Random internalRng = new Random();

    public HybridModel() {
        this(DEFAULT_ITERATIONS, DEFAULT_EXPLORATION);
    }

    public HybridModel(int iterations, double explorationC) {
        this.iterations = iterations;
        this.explorationC = explorationC;
        // Architecture needs to match trainer
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
            System.out.println("AlphaCantStop: Neural Network (3 layers / 6 lines) loaded.");
        } catch (Exception e) {
            System.err.println("AlphaCantStop: Error loading weights: " + e.getMessage());
        }
    }

    public MctsAction decide(GameState rootState, DiceRoll lastRoll) {
        Player rootPlayer = rootState.getCurrentPlayer();
        List<MctsAction> legalAtRoot = getLegalActions(rootState, lastRoll);

        if (legalAtRoot.isEmpty()) return null;
        if (legalAtRoot.size() == 1) return legalAtRoot.get(0);

        Node root = new Node(null, Node.Type.DECISION, rootPlayer, legalAtRoot);

        for (int i = 0; i < iterations; i++) {
            GameState simState = rootState.copy();
            Node leaf = select(root, simState);
            double reward = evaluateLeaf(simState, rootPlayer);
            backpropagate(leaf, reward);
        }
        return getBestAction(root);
    }

    private Node select(Node node, GameState state) {
        while (!node.isLeaf() || !node.untried.isEmpty()) {
            if (!node.untried.isEmpty()) {
                MctsAction action = node.popUntried();
                applyAction(state, action);
                return node.addChild(action, Node.Type.DECISION, state.getCurrentPlayer(), getLegalActions(state, state.getLastRoll()));
            }
            MctsAction bestAction = findUCTMove(node);
            if (bestAction == null) break;
            applyAction(state, bestAction);
            node = node.children.get(bestAction);
        }
        return node;
    }

    private MctsAction findUCTMove(Node node) {
        MctsAction best = null;
        double bestVal = Double.NEGATIVE_INFINITY;
        for (Map.Entry<MctsAction, Node> entry : node.children.entrySet()) {
            double winRate = entry.getValue().valueSum / entry.getValue().visits;

            double uct = winRate + explorationC * Math.sqrt(Math.log(node.visits) / entry.getValue().visits);

            if (uct > bestVal) {
                bestVal = uct;
                best = entry.getKey();
            }
        }
        return best;
    }

    private double evaluateLeaf(GameState state, Player perspective) {
        if (TurnManager.checkWinCondition(state, perspective)) return 1.0;
        if (TurnManager.checkWinCondition(state, perspective.opponent())) return 0.0;

        double winProb = valueNetwork.predict(convertToNNInputs(state))[0];

        if (state.getCurrentPlayer() == perspective) {
            return winProb;
        } else {
            return 1.0 - winProb;
        }
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

    private void backpropagate(Node node, double reward) {
        while (node != null) {
            node.visits++;
            node.valueSum += reward;
            node = node.parent;
        }
    }

    private MctsAction getBestAction(Node root) {
        return root.children.entrySet().stream()
            .max(Comparator.comparingInt(e -> e.getValue().visits))
            .map(Map.Entry::getKey).orElse(null);
    }

    private List<MctsAction> getLegalActions(GameState state, DiceRoll roll) {
        List<MctsAction> actions = new ArrayList<>();
        if (state.getTurnPhase() == TurnPhase.ROLL_OR_STOP) {
            actions.add(MctsAction.roll());
            if (state.countActiveColumns() > 0) actions.add(MctsAction.stop());
        } else if (state.getTurnPhase() == TurnPhase.CHOOSE_MOVE && roll != null) {
            for (Move m : TurnManager.getLegalMoves(state, roll)) {
                actions.add(MctsAction.move(m));
            }
        }
        return actions;
    }

    private void applyAction(GameState state, MctsAction action) {
        if (action.isStop()) {
            TurnManager.stop(state);
        } else if (action.isMove()) {
            TurnManager.applyMove(state, action.move);
        } else if (action.isRoll()) {
            DiceRoll r = DiceRoll.roll(internalRng);
            state.setLastRoll(r);
            if (TurnManager.isBust(state, r)) {
                TurnManager.bust(state);
            } else {
                TurnManager.noBust(state);
            }
        }
    }

    @Override
    public Boolean rollOrStop(GameState state) {
        MctsAction decision = decide(state, null);
        return decision == null || decision.isRoll();
    }

    @Override
    public Move selectMove(GameState state, List<Move> legalMoves) {
        if (legalMoves == null || legalMoves.isEmpty()) return null;

        MctsAction decision = decide(state, state.getLastRoll());

        if (decision != null && decision.isMove()) {
            return decision.move;
        }
        return legalMoves.get(0);
    }
}
