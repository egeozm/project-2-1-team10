package io.github.cantstop.frontend.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.Timer;
import io.github.cantstop.frontend.DiceRow;
import io.github.cantstop.frontend.GameAssets;
import io.github.cantstop.frontend.Instructions;
import io.github.cantstop.frontend.Main;
import io.github.cantstop.backend.*;
import io.github.cantstop.backend.TurnManager;
import io.github.cantstop.backend.GameConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class PlayScreen implements Screen {

    private final Main game;

    // Layout constants
    private static final float ORIGIN_X = 60f;
    private static final float ORIGIN_Y = 60f;
    private static final float CELL_W = 24f;
    private static final float CELL_H = 18f;

    private static float colX(int col) {
        return ORIGIN_X + col * CELL_W + 4f;
    }

    private static float rowY(int step) {
        return ORIGIN_Y + step * CELL_H + 1f;
    }

    // Backend game model
    private GameState gameState;

    // Assets
    private GameAssets assets;
    private Texture[] diceFaces;
    private Texture board;
    private Texture blueMarker1, blueMarker2, blueCross;
    private Texture redMarker1, redMarker2, redCross;

    // UI
    private Stage stage;
    private Skin skin;
    private TextButton rollButton;
    private TextButton passButton;
    private TextButton instructionsButton;

    private final List<TextButton> optionButtons = new ArrayList<>();

    private Instructions instructions;
    private boolean showInstructions = false;

    // Dice animation + result
    private boolean rolling = false;
    private DiceRoll currentRoll = null;
    private int[] rolledDice = null; // 4 dice faces
    private final Random rng = new Random();
    private final List<DiceRow> optionDiceRows = new ArrayList<>();

    public PlayScreen(Main game) {
        this.game = game;

        stage = new Stage();
        skin = new Skin(Gdx.files.internal("uiskin.json"));
        Gdx.input.setInputProcessor(stage);

        instructions = new Instructions(blueCross); // placeholder texture

        rollButton = new TextButton("Roll", skin);
        rollButton.setSize(120, 50);
        rollButton.setPosition(500, 200);
        rollButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                startDiceAnimation();
            }
        });
        stage.addActor(rollButton);

        passButton = new TextButton("Pass", skin);
        passButton.setSize(120, 50);
        passButton.setPosition(500, 120);
        passButton.addListener(new ClickListener() {
            @Override public void clicked(InputEvent event, float x, float y) {
                switchPlayer();
            } });
        stage.addActor(passButton);

        instructionsButton = new TextButton("Instructions", skin);
        instructionsButton.setSize(120, 50);
        instructionsButton.setPosition(500, 60);
        instructionsButton.addListener(new ClickListener() {
            @Override public void clicked(InputEvent event, float x, float y) {
                showInstructions = !showInstructions;
            } });
        stage.addActor(instructionsButton);
    }

    private void switchPlayer() {
        clearOptionButtons();
        optionDiceRows.clear();
        currentRoll = null;
        rolledDice = null;
        Player next = (gameState.getCurrentPlayer() == Player.BLUE) ? Player.RED : Player.BLUE;
        gameState.setCurrentPlayer(next);
    }

    private void startDiceAnimation() {
        clearOptionButtons();
        optionDiceRows.clear();

        if (rollButton.hasParent()) {
            rollButton.remove();
        }

        currentRoll = DiceRoll.roll(rng);
        rolledDice = null;
        rolling = true;

        // Stop animation after 1s and show the real dice
        Timer.schedule(new Timer.Task() {
            @Override
            public void run() {
                rolling = false;
                rolledDice = currentRoll.dice();
                showOptions();
            }
        }, 1.0f);
    }

    private void showOptions() {

        List<Move> legalMoves = TurnManager.getLegalMoves(gameState, currentRoll);
        optionDiceRows.clear();
        if (legalMoves.isEmpty()) {
            System.out.println("Bust! Switching turn.");
            gameState.setBustPending(true);
            TurnManager.stop(gameState); // auto stop on bust
            resetForNextRoll();
            return;
        }

        float startY = 200;
        float rowSpacing = 100f;
        float diceSize = 32;
        float gap = 20;

        for (int i = 0; i < legalMoves.size(); i++) {
            Move move = legalMoves.get(i);

            float diceY = startY - i * rowSpacing;
            float diceStartX = 200f;

            int[] pairingDice = currentRoll.getPairing(move.pairingIndex());
            // Each legal move gets its own DiceRow (3 rows max)
            optionDiceRows.add(new DiceRow(pairingDice, diceStartX, diceY, diceSize, gap, i));


            TextButton option = new TextButton("Advance on " + move.sumA() + " & " + move.sumB(), skin);
            option.setSize(200, 40);
            option.setPosition(diceStartX + 200f, diceY);

            option.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    TurnManager.applyMove(gameState, move);
                    resetForNextRoll();
                }
            });

            optionButtons.add(option);
            stage.addActor(option);
        }
    }

    private void clearOptionButtons() {
        for (TextButton b : optionButtons) {
            if (b.hasParent()) b.remove();
        }
        optionButtons.clear();
    }

    private void resetForNextRoll() {
        clearOptionButtons();
        optionDiceRows.clear();
        if (!rollButton.hasParent()) {
            stage.addActor(rollButton);
        }
        currentRoll = null;
        rolledDice = null;
    }

    @Override
    public void show() {
        assets = new GameAssets();
        assets.loadAll();

        board = assets.board;
        blueMarker1 = assets.blueMarker1;
        blueMarker2 = assets.blueMarker2;
        blueCross = assets.blueCross;
        redMarker1 = assets.redMarker1;
        redMarker2 = assets.redMarker2;
        redCross = assets.redCross;
        diceFaces = assets.diceTextures;

        // fresh game
        gameState = GameState.initialize(Player.BLUE);
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(0f, 0f, 0f, 1f);

        game.viewport.apply();
        game.batch.setProjectionMatrix(game.viewport.getCamera().combined);
        game.batch.begin();

        // --- Board drawing ---
        game.batch.draw(board, ORIGIN_X, ORIGIN_Y);

        for (int col = 0; col < GameConstants.NUM_COLS; col++) {
            game.font.draw(game.batch, Integer.toString(col+2), colX(col)+4f, rowY(GameConstants.MAX_COLUMN_HEIGHTS[col])-4f);
        }

        for (int col = 0; col < GameConstants.NUM_COLS; col++) {
            ColumnState cs = gameState.columns()[col];

            // --- Permanent markers ---
            int blueHeight = cs.permHeightFor(Player.BLUE);
            int redHeight  = cs.permHeightFor(Player.RED);
            if (blueHeight > 0) game.batch.draw(blueMarker1, colX(col), rowY(blueHeight));
            if (redHeight  > 0) game.batch.draw(redMarker1, colX(col), rowY(redHeight));

            // --- Temp runner for active player ---
            Integer tempHeight = cs.tempHeight();
            if (tempHeight != null) {
                Texture tex = (gameState.getCurrentPlayer() == Player.BLUE) ? blueMarker2 : redMarker2;
                game.batch.draw(tex, colX(col), rowY(tempHeight));
            }
        }

        // --- Dice drawing ---
        if (rolling) {
            // Animate just 4 dice in one row
            float diceY = 100;
            float diceStartX = 200;
            float diceSize = 64f;
            float gap = 20f;
            for (int i = 0; i < 4; i++) {
                int randomFace = rng.nextInt(6);
                game.batch.draw(diceFaces[randomFace], diceStartX + i * (diceSize + gap), diceY, diceSize, diceSize);
            }
        } else if (rolledDice != null) {
            // Show combinations in rows
            for (DiceRow row : optionDiceRows) {
                row.draw(game.batch, diceFaces);
            }
        }

        if (showInstructions) {
            instructions.draw(game.batch, game.font);
        }

        game.batch.end();

        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override public void pause() {}
    @Override public void resume() {}
    @Override public void hide() {}
    @Override
    public void dispose() {
        stage.dispose();
        skin.dispose();
    }
}
