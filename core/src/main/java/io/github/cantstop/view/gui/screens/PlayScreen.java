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

import java.util.*;

public class PlayScreen implements Screen {

    private final Main game;
    private final AiConfig config;

    // Backend game model
    private GameState gameState;
    private GameController controller;
    private HumanController humanController;
    private IPlayerController playerRed;
    private IPlayerController playerBlue;

    // RNG split:
    // - diceRng: ONLY for dice rolls in real game (GameController)
    // - aiRng:   ONLY for AI randomness (rollouts, tie-breaks, etc.)
    private final Random diceRng = new Random();
    private final Random aiRng   = new Random();

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
    private final List<TextButton> moveButtons = new ArrayList<>();
    private BitmapFont font;

    private PostProcessor postProcessor;
    private float shaderTime = 0f;

    private boolean waitingForAnimation = false;
    private boolean gameOver = false;

    public PlayScreen(Main game) {
        this(game, null); //pvp
    }

    public PlayScreen(Main game , AiConfig config) {

        this.game = game;
        this.config = config;

        this.vsAI= (config != null);

        // ----------------------------------
        // initialize backend
        // ----------------------------------

        gameState = GameState.initialize(Player.BLUE);
        humanController = new HumanController();

        playerBlue = humanController;

        if (vsAI) {

            // “research-like” params (you can later wire these to settings)
            //int maxIters = 1_000_000;
           // long timeMs = 50;          // GUI-friendly (20–100ms); use 200ms only if you accept lag
          //  int rolloutMax = 10;
          //  double C = 0.35;
           // double dpwK = 25.0;
          //  double dpwAlpha = 0.5;

          //  MCTSPlayer mcts = new MCTSPlayer(
           //     aiRng, maxIters, C, rolloutMax, dpwK, dpwAlpha, timeMs
          //  );

          //  playerRed = new MctsControllerAdapter(mcts);
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
        FreeTypeFontGenerator generator =
            new FreeTypeFontGenerator(Gdx.files.internal("fonts/pixel_font.ttf"));

        FreeTypeFontGenerator.FreeTypeFontParameter params =
            new FreeTypeFontGenerator.FreeTypeFontParameter();

        params.size = 12;              // base size
        params.mono = true;
        params.minFilter = Texture.TextureFilter.Nearest;
        params.magFilter = Texture.TextureFilter.Nearest;
        params.genMipMaps = false;
        params.kerning = false;
        params.borderWidth = 0;
        params.shadowOffsetX = 0;
        params.shadowOffsetY = 0;

        font = generator.generateFont(params);
        generator.dispose();
        font.getData().setScale(1f);

        borderStyle = ButtonStyle.createBorderButtonStyle();

        boardRenderer = new BoardRenderer(gameState, game.batch, font, game.assets);
        diceRenderer = new DiceRenderer(gameState, game.batch, game.assets);
        popupRenderer = new PopupRenderer(game.batch, font);
        moveButtonRenderer = new MoveButtonRenderer(gameState, this);

        postProcessor = new PostProcessor(game.batch, game.viewport);

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
        if (waitingForAnimation) return;

        Event action = controller.update();
        handleAction(action);
    }

    private void handleAction(Event action) {

        moveButtonRenderer.clearMoveButtons();
        stopButton.setVisible(false);
        rollButton.setVisible(false);

        if (action instanceof WaitForInputEvent) {

            if (gameState.getTurnPhase() == TurnPhase.CHOOSE_MOVE) {

                moveButtonRenderer.showMoveButtons(TurnManager.getLegalMoves(gameState, gameState.getLastRoll()), stage);

            } else {
                boolean canStop = gameState.countActiveColumns() > 0;
                stopButton.setVisible(canStop);
                rollButton.setVisible(true);
            }

            return;
        }

        waitingForAnimation = true;

        if (action instanceof RollEvent roll) {
            playAnimationFor(roll, () -> commit(action));
        } else if (action instanceof StopEvent stop) {
            playAnimationFor(stop, () -> commit(action));
        } else if (action instanceof MoveEvent move) {
            playAnimationFor(move, () -> commit(action));
        } else {
            throw new IllegalStateException("Unhandled action: " + action);
        }
    }

    private void commit(Event action) {
        action.apply(gameState);
        waitingForAnimation = false;
        advanceGame();
    }

    private void playAnimationFor(RollEvent action, Runnable onDone) {

        diceRenderer.rollAnimation(1.03f);

        Timer.schedule(new Timer.Task() {
            @Override
            public void run() {

                if (action.isBust()) {
                    popupRenderer.showPopup(gameState.getCurrentPlayer() + "BUSTED", 1.2f);
                    Timer.schedule(new Timer.Task() {
                        @Override
                        public void run() {

                            onDone.run();
                        }
                    }, 1.5f);
                } else {
                    onDone.run();
                }

            }
        }, 1f);
    }

    private void playAnimationFor(StopEvent action, Runnable onDone) {

        //        diceRenderer.stopAnimation(1f);

        Timer.schedule(new Timer.Task() {
            @Override
            public void run() {

                onDone.run();

            }
        }, 1.5f);
    }

    private void playAnimationFor(MoveEvent action, Runnable onDone) {

        //        diceRenderer.MoveAnimation(1f);

        Timer.schedule(new Timer.Task() {
            @Override
            public void run() {

                onDone.run();

            }
        }, 1.5f);
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
    private IPlayerController buildAiController(AiConfig config) {

        AgentType type = config.type();
        int timeMs = config.timeMs();

        switch (type) {

            case RULE_BASED -> {
                return new io.github.cantstop.model.ai.RuleBasedPlayer(aiRng, 5f, 2f);
            }

            case MCTS -> {
                int maxIters = 1_000_000;
                int rolloutMax = 10;
                double C = 0.35;
                double dpwK = 25.0;
                double dpwAlpha = 0.5;

                MCTSPlayer mcts = new MCTSPlayer(
                    aiRng, maxIters, C, rolloutMax, dpwK, dpwAlpha, timeMs
                );

                return new io.github.cantstop.controller.MctsControllerAdapter(mcts);
            }


            case MINIMAX -> {
                //adapter for expectiminimax like the one we use for mcts?
                throw new IllegalStateException("minimax adapter ");
            }
        }

        throw new IllegalStateException("Unk AgentType: " + type);
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
        //        postProcessor.dispose();
        if (game.assets != null) {
            game.assets.dispose();
        }
    }

    /**
     * Adapter: allows MCTSPlayer (which returns MctsAction) to be used as an IPlayerController.
     * GameController still controls dice with diceRng; AI uses only MCTSPlayer + its own aiRng.
     */
    private static final class MctsControllerAdapter implements IPlayerController {

        private final MCTSPlayer mcts;

        private MctsControllerAdapter(MCTSPlayer mcts) {
            this.mcts = Objects.requireNonNull(mcts, "mcts");
        }

        @Override
        public Boolean rollOrStop(GameState state) {
            // true = roll, false = stop
            var act = mcts.decide(state);
            if (act == null) return true; // fallback: roll
            return !act.isStop();
        }

        @Override
        public Move selectMove(GameState state, List<Move> legalMoves) {
            if (legalMoves == null || legalMoves.isEmpty()) return null;

            DiceRoll lastRoll = state.getLastRoll();
            var act = mcts.decide(state, lastRoll);

            if (act != null && act.isMove() && act.move != null) {
                return act.move;
            }
            return legalMoves.get(0);
        }
    }
}
