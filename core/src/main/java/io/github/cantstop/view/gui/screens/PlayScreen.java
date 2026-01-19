package io.github.cantstop.view.gui.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Timer;
import io.github.cantstop.controller.*;
import io.github.cantstop.model.*;
import io.github.cantstop.model.ai.AI_ANN.AnnPlayer;
import io.github.cantstop.model.ai.AI_MCTS.MCTSPlayer;
import io.github.cantstop.model.ai.Hybrid_Model.HybridModel;
import io.github.cantstop.view.gui.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class PlayScreen extends BaseScreen {

    @SuppressWarnings("unused")
    private final AiConfig config;

    // Backend game model
    private GameState gameState;
    private GameController controller;
    private HumanController humanController;
    private IPlayerController playerRed;
    private IPlayerController playerBlue;

    // RNG split
    private final Random diceRng = new Random();
    private final Random aiRng = new Random();

    private BoardRenderer boardRenderer;
    private DiceRenderer diceRenderer;
    private PopupRenderer popupRenderer;
    private MoveButtonRenderer moveButtonRenderer;
    private boolean vsAI;

    // UI (Buttons are managed here, Stage is in BaseScreen)
    private TextButton rollButton;
    private TextButton stopButton;
    private TextButton menuButton;
    @SuppressWarnings("unused")
    private final List<TextButton> moveButtons = new ArrayList<>();

    private boolean waitingForAnimation = false;
    @SuppressWarnings("unused")
    private boolean gameOver = false;

    private Sound diceRollSound;
    private Sound bustSound;
    private Sound winSound;

    public PlayScreen(Main game) {
        this(game, null);
    }

    public PlayScreen(Main game, AiConfig config) {
        super(game);
        this.config = config;
        this.vsAI = (config != null);

        // initialize backend
        gameState = GameState.initialize(Player.BLUE);
        humanController = new HumanController();
        playerBlue = humanController;

        if (vsAI) {
            playerRed = buildAiController(config);
        } else {
            playerRed = humanController;
        }

        controller = new GameController(gameState, playerRed, playerBlue, diceRng);

        // Sounds
        diceRollSound = Gdx.audio.newSound(Gdx.files.internal("music/dice.mp3"));
        winSound = Gdx.audio.newSound(Gdx.files.internal("music/win.wav"));
        bustSound = Gdx.audio.newSound(Gdx.files.internal("music/bust.mp3"));
    }

    @Override
    public void show() {
        super.show(); // initializes stage, fonts, postProcessor

        // initialize renderers using BaseScreen's font/stage
        boardRenderer = new BoardRenderer(gameState, game.batch, font, game.assets);
        diceRenderer = new DiceRenderer(gameState, game.batch, game.assets);
        popupRenderer = new PopupRenderer(game.batch, font);
        moveButtonRenderer = new MoveButtonRenderer(gameState, this);

        // create buttons
        rollButton = new TextButton("Roll", borderStyle);
        rollButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                handleRollInput();
            }
        });

        stopButton = new TextButton("Stop", borderStyle);
        stopButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                handleStopInput();
            }
        });

        menuButton = new TextButton("Menu", borderStyle);
        menuButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new MenuScreen(game));
            }
        });

        // Add to stage
        rollButton.setBounds(440, 80, 100, 40);
        stopButton.setBounds(440, 30, 100, 40);
        menuButton.setBounds(10, 280, 70, 30);

        stage.addActor(menuButton);
        stage.addActor(rollButton);
        stage.addActor(stopButton);

        stopButton.setVisible(false);
    }

    private IPlayerController buildAiController(AiConfig config) {
        AgentType type = config.type();
        int timeMs = config.timeMs();
        String annW = config.annWeights();
        float annThr = config.annThreshold();

        switch (type) {
            case RULE_BASED -> {
                return new io.github.cantstop.model.ai.RuleBasedPlayer(aiRng, 9f, 1f);
            }
            case MCTS -> {
                int maxIters = 1_000_000;
                int rolloutMax = 10;
                double C = 0.35;
                double dpwK = 25.0;
                double dpwAlpha = 0.5;

                if (timeMs >= 200) { // HARD
                    rolloutMax = 7;
                    C = 0.25;
                    dpwAlpha = 0.3;
                } else if (timeMs >= 50) { // MED
                    rolloutMax = 8;
                } else { // EASY
                    rolloutMax = 8;
                }
                MCTSPlayer mcts = new MCTSPlayer(aiRng, maxIters, C, rolloutMax, dpwK, dpwAlpha, timeMs);
                return new io.github.cantstop.controller.MctsControllerAdapter(mcts);
            }
            case MINIMAX, MINIMAX_ITERATIVE -> {
                throw new IllegalStateException("minimax adapter ");
            }
            case ANN -> {
                String defaultWeights = annW != null && !annW.isBlank()
                        ? annW
                        : "core/src/main/java/io/github/cantstop/model/ai/AI_ANN/ann_weights_mcts.annw";
                float thr = (annThr > 0f && annThr < 1f) ? annThr : 0.45f;
                System.out.println("[GUI] ANN weights=" + defaultWeights + " thr=" + thr);
                return new AnnPlayer(new File(defaultWeights), thr);
            }
            case HYBRID -> {
                return new HybridModel();
            }
        }
        throw new IllegalStateException("Unk AgentType: " + type);
    }

    public void handleRollInput() {
        humanController.chooseRoll();
        advanceGame();
    }

    public void handleStopInput() {
        humanController.chooseStop();
        advanceGame();
    }

    public void handleMoveInput(Move selectedMove) {
        humanController.chooseMove(selectedMove);
        advanceGame();
    }

    private void advanceGame() {
        if (waitingForAnimation)
            return;
        Event event = controller.update();
        handleEvent(event);
    }

    private void handleEvent(Event event) {
        moveButtonRenderer.clearMoveButtons();
        stopButton.setVisible(false);
        rollButton.setVisible(false);

        if (event instanceof WaitForInputEvent) {
            if (gameState.getTurnPhase() == TurnPhase.CHOOSE_MOVE) {
                moveButtonRenderer.showMoveButtons(TurnManager.getLegalMoves(gameState, gameState.getLastRoll()),
                        stage);
            } else {
                boolean canStop = gameState.countActiveColumns() > 0;
                stopButton.setVisible(canStop);
                rollButton.setVisible(true);
            }
            return;
        }

        waitingForAnimation = true;
        if (event instanceof RollEvent roll)
            playAnimationFor(roll, () -> commit(event));
        else if (event instanceof StopEvent stop)
            playAnimationFor(stop, () -> commit(event));
        else if (event instanceof MoveEvent move)
            playAnimationFor(move, () -> commit(event));
        else if (event instanceof GameOverEvent gameOver)
            playAnimationFor(gameOver, () -> commit(event));
        else
            throw new IllegalStateException("Unhandled event: " + event);
    }

    private void commit(Event event) {
        event.apply(gameState);
        waitingForAnimation = false;
        advanceGame();
    }

    private void playAnimationFor(RollEvent event, Runnable onDone) {
        diceRenderer.rollAnimation(1.03f);
        if (diceRollSound != null)
            diceRollSound.play(0.7f);

        Timer.schedule(new Timer.Task() {
            @Override
            public void run() {
                if (event.isBust()) {
                    if (bustSound != null)
                        bustSound.play(0.8f);
                    popupRenderer.showPopup(gameState.getCurrentPlayer() + "\nBUSTED", 1.4f);
                    Timer.schedule(new Timer.Task() {
                        @Override
                        public void run() {
                            onDone.run();
                        }
                    }, 1.4f);
                } else {
                    onDone.run();
                }
            }
        }, 1f);
    }

    private void playAnimationFor(StopEvent event, Runnable onDone) {
        Timer.schedule(new Timer.Task() {
            @Override
            public void run() {
                onDone.run();
            }
        }, 0.3f);
    }

    private void playAnimationFor(MoveEvent event, Runnable onDone) {
        Timer.schedule(new Timer.Task() {
            @Override
            public void run() {
                onDone.run();
            }
        }, 0.3f);
    }

    private void playAnimationFor(GameOverEvent event, Runnable onDone) {
        if (winSound != null)
            winSound.play(0.9f);
        popupRenderer.showPopup(event.getWinner() + "\nWINS", 5f);
    }

    @Override
    protected void renderScreenContent(float delta) {
        // BaseScreen has already begun the batch
        boardRenderer.drawBoard();
        boardRenderer.drawMarkersEtc();
        boardRenderer.drawColumnNumbers();
        boardRenderer.drawCurrentPlayer();
        diceRenderer.update(delta);
        diceRenderer.draw();
        popupRenderer.update(delta);
        popupRenderer.draw();
    }

    @Override
    public void dispose() {
        super.dispose(); // disposes stage, fonts, postProcessor
        // game.assets.dispose(); // Handled by Main
        if (diceRollSound != null)
            diceRollSound.dispose();
        if (winSound != null)
            winSound.dispose();
        if (bustSound != null)
            bustSound.dispose();
    }
}
