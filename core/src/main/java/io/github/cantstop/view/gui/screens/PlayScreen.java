package io.github.cantstop.view.gui.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Timer;

import io.github.cantstop.controller.*;
import io.github.cantstop.model.*;
import io.github.cantstop.model.ai.RuleBasedPlayer;
import io.github.cantstop.view.gui.*;
import io.github.cantstop.view.gui.PostProcessor;

import java.util.*;

public class PlayScreen implements Screen {

    private final Main game;

    // Backend game model
    private GameState gameState;
    private GameController controller;
    private HumanController humanController;
    private IPlayerController playerRed;
    private IPlayerController playerBlue;

    private final Random rng = new Random();

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

    public PlayScreen(Main game , boolean vsAI) {

        this.game = game;
        this.vsAI = vsAI;

        // initialize backend
        gameState = GameState.initialize(Player.BLUE);
        humanController = new HumanController();
        playerBlue = humanController;
        playerRed = (vsAI) ? new RuleBasedPlayer(rng, 9f) : humanController;
        controller = new GameController(gameState, playerRed, playerBlue);

        // initialize frontend
        font = new BitmapFont();
        font.getData().setScale(0.8f);
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

        Action action = controller.update();
        handleAction(action);
    }

    private void handleAction(Action action) {

        moveButtonRenderer.clearMoveButtons();
        stopButton.setVisible(false);
        rollButton.setVisible(false);

        if (action instanceof WaitForInputAction) {

            if (gameState.getTurnPhase() == TurnPhase.CHOOSE_MOVE) {

                moveButtonRenderer.showMoveButtons(TurnManager.getLegalMoves(gameState, gameState.getLastRoll()), stage);

            } else {

                stopButton.setVisible(true);
                rollButton.setVisible(true);
            }

            return;
        }

        waitingForAnimation = true;

        if (action instanceof RollAction roll) {
            playAnimationFor(roll, () -> commit(action));
        } else if (action instanceof StopAction stop) {
            playAnimationFor(stop, () -> commit(action));
        } else if (action instanceof MoveAction move) {
            playAnimationFor(move, () -> commit(action));
        } else {
            throw new IllegalStateException("Unhandled action: " + action);
        }
    }

    private void commit(Action action) {
        action.apply(gameState);
        waitingForAnimation = false;
        advanceGame();
    }

    private void playAnimationFor(RollAction action, Runnable onDone) {

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

    private void playAnimationFor(StopAction action, Runnable onDone) {

//        diceRenderer.stopAnimation(1f);

        Timer.schedule(new Timer.Task() {
            @Override
            public void run() {

                onDone.run();

            }
        }, 1.5f);
    }

    private void playAnimationFor(MoveAction action, Runnable onDone) {

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
        // ------------------------------------------------------------------------
        //  Keyboard Controls
        // ------------------------------------------------------------------------

//        // SPACE = ROLL
//        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.SPACE)) {
//            if (!gameOver && gameState.getTurnPhase() == TurnPhase.ROLL_OR_STOP && !rolling) {
//                startDiceAnimation();
//            }
//        }
//
//        // ENTER = STOP
//        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.ENTER)) {
//            if (!gameOver && gameState.getTurnPhase() == TurnPhase.ROLL_OR_STOP
//                && gameState.countActiveColumns() > 0 && !rolling) {
//                handleStop();
//            }
//        }
//
//        // ESC = MENU
//        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.ESCAPE)) {
//            game.setScreen(new MenuScreen(game));
//        }

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
//        postProcessor.dispose();
        if (game.assets != null) {
            game.assets.dispose();
        }
    }
}
