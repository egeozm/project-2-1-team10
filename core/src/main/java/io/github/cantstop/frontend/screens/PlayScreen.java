package io.github.cantstop.frontend.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.Timer;

import io.github.cantstop.frontend.*;
import io.github.cantstop.backend.*;
import io.github.cantstop.frontend.shaders.PostProcessor;

import java.util.*;

public class PlayScreen implements Screen {

    private final Main game;

    // Backend game model
    private GameState gameState;
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

    private boolean gameOver = false;

    public PlayScreen(Main game , boolean vsAI) {
        this.game = game;
        this.vsAI = vsAI;
        gameState = GameState.initialize(Player.RED);

        postProcessor = new PostProcessor(game.batch, game.viewport);

        font = new BitmapFont();
        font.getData().setScale(0.8f);
        borderStyle = ButtonStyle.createBorderButtonStyle();

        boardRenderer = new BoardRenderer(gameState, game.batch, font, game.assets);
        diceRenderer = new DiceRenderer(gameState, game.batch, game.assets);
        popupRenderer = new PopupRenderer(game.batch, font);
        moveButtonRenderer = new MoveButtonRenderer(gameState, this);

        rollButton = new TextButton("Roll", borderStyle);
        rollButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                handleRoll();
            }
        });

        stopButton = new TextButton("Stop", borderStyle);
        stopButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (gameState.countActiveColumns() > 0) {
                    handleStop();
                }
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
    public void handleRoll() {

        gameState.setLastRoll(TurnManager.roll(gameState, rng));

        diceRenderer.rollAnimation(1f);

        rollButton.setVisible(false);
        stopButton.setVisible(false);

        Timer.schedule(new Timer.Task() {
            @Override
            public void run() {

                List<Move> legalMoves = TurnManager.getLegalMoves(gameState, gameState.getLastRoll());

                if (legalMoves.isEmpty()) {
                    // bust
                    popupRenderer.showPopup(gameState.getCurrentPlayer().toString() + " BUSTED", 2f);
                    TurnManager.bust(gameState);

                    Timer.schedule(new Timer.Task() {
                        @Override
                        public void run() {
                            rollButton.setVisible(true);
                        }
                    }, 1f);

                } else {
                    TurnManager.noBust(gameState);
                }

                moveButtonRenderer.showMoveButtons(legalMoves, stage);
            }
        }, 1f);
    }

    // triggered when stop button is clicked
    public void handleStop() {

        TurnManager.stop(gameState);
        stopButton.setVisible(false);

        if (TurnManager.checkWinCondition(gameState, gameState.getCurrentPlayer().opponent())) {
            gameOver = true;
            rollButton.setVisible(false);
        } else {
            popupRenderer.showPopup(gameState.getCurrentPlayer().toString() + "'S TURN", 1.2f);
        }
    }

    // triggered when a move is selected
    public void handleMove(Move selectedMove) {
        TurnManager.applyMove(gameState, selectedMove);
        moveButtonRenderer.clearMoveButtons();
        rollButton.setVisible(true);
        stopButton.setVisible(true);
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

//        ScreenUtils.clear(0.1f, 0.1f, 0.15f, 1f);

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
