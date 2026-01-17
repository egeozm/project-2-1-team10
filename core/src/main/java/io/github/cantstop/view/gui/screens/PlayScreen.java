package io.github.cantstop.view.gui.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Timer;

import io.github.cantstop.controller.*;
import io.github.cantstop.model.*;
import io.github.cantstop.model.ai.AI_MCTS.MCTSPlayer;
import io.github.cantstop.view.gui.*;
import io.github.cantstop.view.gui.PostProcessor;
import io.github.cantstop.view.gui.AiConfig;
import io.github.cantstop.view.gui.AgentType;
import io.github.cantstop.model.ai.AI_ANN.AnnPlayer;
import io.github.cantstop.model.ai.Hybrid_Model.HybridModel;

import java.util.*;
import java.io.File;

public class PlayScreen implements Screen {

    private final Main game;
    @SuppressWarnings("unused")
    private final AiConfig config;

    // Backend game model
    private GameState gameState;
    private GameController controller;
    private HumanController humanController;
    private IPlayerController playerRed;
    private IPlayerController playerBlue;

    // RNG split:
    // - diceRng: ONLY for dice rolls in real game (GameController)
    // - aiRng: ONLY for AI randomness (rollouts, tie-breaks, etc.)
    private final Random diceRng = new Random();
    private final Random aiRng = new Random();

    private BoardRenderer boardRenderer;
    private DiceRenderer diceRenderer;
    private PopupRenderer popupRenderer;
    private MoveButtonRenderer moveButtonRenderer;
    private boolean vsAI;

    // UI
    private Stage stage;
    private TextButton.TextButtonStyle borderStyle;
    private TextButton rollButton;
    private TextButton stopButton;
    private TextButton menuButton;
    // moveButtons kept for potential UI extensions; currently cleared each update.
    @SuppressWarnings("unused")
    private final List<TextButton> moveButtons = new ArrayList<>();
    public BitmapFont font;
    public BitmapFont buttonFont;

    private PostProcessor postProcessor;
    private float shaderTime = 0f;

    private boolean waitingForAnimation = false;
    // gameOver flag reserved for future use (e.g., disabling inputs).
    @SuppressWarnings("unused")
    private boolean gameOver = false;

    public PlayScreen(Main game) {
        this(game, null); // pvp
    }

    public PlayScreen(Main game, AiConfig config) {

        this.game = game;
        this.config = config;

        this.vsAI = (config != null);

        // ----------------------------------
        // initialize backend
        // ----------------------------------

        gameState = GameState.initialize(Player.BLUE);
        humanController = new HumanController();

        playerBlue = humanController;

        if (vsAI) {

            // “research-like” params (you can later wire these to settings)
            // int maxIters = 1_000_000;
            // long timeMs = 50; // GUI-friendly (20–100ms); use 200ms only if you accept
            // lag
            // int rolloutMax = 10;
            // double C = 0.35;
            // double dpwK = 25.0;
            // double dpwAlpha = 0.5;

            // MCTSPlayer mcts = new MCTSPlayer(
            // aiRng, maxIters, C, rolloutMax, dpwK, dpwAlpha, timeMs
            // );

            // playerRed = new MctsControllerAdapter(mcts);
            playerRed = buildAiController(config);

        } else {
            playerRed = humanController;
        }

        // IMPORTANT: pass diceRng to GameController (dice only)
        controller = new GameController(gameState, playerRed, playerBlue, diceRng);

        // ----------------------------------
        // initialize frontend
        // ----------------------------------

        // initialize font
        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal("fonts/pixel_font.ttf"));

        FreeTypeFontGenerator.FreeTypeFontParameter params = new FreeTypeFontGenerator.FreeTypeFontParameter();

        params.size = 8; // recommended sizes are 12 and 24
        params.mono = true;
        params.minFilter = Texture.TextureFilter.Nearest;
        params.magFilter = Texture.TextureFilter.Nearest;
        params.genMipMaps = false;
        params.kerning = false;
        params.borderWidth = 0;
        params.shadowOffsetX = 0;
        params.shadowOffsetY = 0;

        font = generator.generateFont(params);

        params.size = 16;
        buttonFont = generator.generateFont(params);

        generator.dispose();

        borderStyle = ButtonStyle.createBorderButtonStyle(buttonFont);

        boardRenderer = new BoardRenderer(gameState, game.batch, font, game.assets);
        diceRenderer = new DiceRenderer(gameState, game.batch, game.assets);
        popupRenderer = new PopupRenderer(game.batch, font);
        moveButtonRenderer = new MoveButtonRenderer(gameState, this);

        postProcessor = new PostProcessor(game.batch, game.viewport);

        // create buttons
        rollButton = new TextButton("Roll", borderStyle);
        rollButton.padTop(20f);
        rollButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                handleRollInput();
            }
        });

        stopButton = new TextButton("Stop", borderStyle);
        stopButton.padTop(20f);
        stopButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                handleStopInput();
            }
        });

        menuButton = new TextButton("Menu", borderStyle);
        menuButton.padTop(20f);
        menuButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new MenuScreen(game));
            }
        });
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

                MCTSPlayer mcts = new MCTSPlayer(
                        aiRng, maxIters, C, rolloutMax, dpwK, dpwAlpha, timeMs);

                return new io.github.cantstop.controller.MctsControllerAdapter(mcts);
            }

            case MINIMAX, MINIMAX_ITERATIVE -> {
                // adapter for expectiminimax like the one we use for mcts?
                throw new IllegalStateException("minimax adapter ");
            }

            case ANN -> {
                String defaultWeights = annW != null && !annW.isBlank()
                    ? annW
                    : "core/src/main/java/io/github/cantstop/model/ai/AI_ANN/ann_weights_mcts.annw";
                float thr = (annThr > 0f && annThr < 1f) ? annThr : 0.45f;
                return new AnnPlayer(new File(defaultWeights), thr);
            }

            case HYBRID -> {
                return new HybridModel(); // implements IPlayerController
            }
        }

        throw new IllegalStateException("Unk AgentType: " + type);
    }

    @Override
    public void show() {

        stage = new Stage(game.viewport);
        Gdx.input.setInputProcessor(stage);

        rollButton.setBounds(440, 80, 100, 40); // x, y, width, height
        stopButton.setBounds(440, 30, 100, 40); // x, y, width, height
        menuButton.setBounds(10, 280, 70, 30); // x, y, width, height

        stage.addActor(menuButton);
        stage.addActor(rollButton);
        stage.addActor(stopButton);

        stopButton.setVisible(false);
    }

    // triggered when roll button is clicked
    public void handleRollInput() {
        humanController.chooseRoll();
        advanceGame();
    }

    // triggered when stop button is clicked
    public void handleStopInput() {
        humanController.chooseStop();
        advanceGame();
    }

    // triggered when a move button is clicked
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

        if (event instanceof RollEvent roll) {
            playAnimationFor(roll, () -> commit(event));
        } else if (event instanceof StopEvent stop) {
            playAnimationFor(stop, () -> commit(event));
        } else if (event instanceof MoveEvent move) {
            playAnimationFor(move, () -> commit(event));
        } else if (event instanceof GameOverEvent gameOver) {
            playAnimationFor(gameOver, () -> commit(event));
        } else {
            throw new IllegalStateException("Unhandled event: " + event);
        }
    }

    private void commit(Event event) {
        event.apply(gameState);
        waitingForAnimation = false;
        advanceGame();
    }

    private void playAnimationFor(RollEvent event, Runnable onDone) {

        diceRenderer.rollAnimation(1.03f);

        Timer.schedule(new Timer.Task() {
            @Override
            public void run() {

                if (event.isBust()) {
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

        // diceRenderer.stopAnimation(1f);

        Timer.schedule(new Timer.Task() {
            @Override
            public void run() {

                onDone.run();

            }
        }, 0.3f);
    }

    private void playAnimationFor(MoveEvent event, Runnable onDone) {

        // diceRenderer.MoveAnimation(1f);

        Timer.schedule(new Timer.Task() {
            @Override
            public void run() {

                onDone.run();

            }
        }, 0.3f);
    }

    private void playAnimationFor(GameOverEvent event, Runnable onDone) {

        popupRenderer.showPopup(event.getWinner() + "\nWINS", 5f);

    }

    @Override
    public void render(float delta) {

        shaderTime += delta;

        game.viewport.apply();

        postProcessor.begin();

        game.batch.setProjectionMatrix(game.viewport.getCamera().combined);
        game.batch.begin();

        boardRenderer.drawBoard();
        boardRenderer.drawMarkersEtc();
        boardRenderer.drawColumnNumbers();
        boardRenderer.drawCurrentPlayer();
        diceRenderer.update(delta);
        diceRenderer.draw();
        popupRenderer.update(delta);
        popupRenderer.draw();

        game.batch.end();

        stage.act(delta);
        stage.draw();

        postProcessor.end(shaderTime, game.viewport);
    }

    @Override
    public void resize(int width, int height) {
        game.viewport.update(width, height, true);
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void hide() {
    }

    @Override
    public void dispose() {
        stage.dispose();
        font.dispose();
        // postProcessor.dispose();
        if (game.assets != null) {
            game.assets.dispose();
        }
    }
}
