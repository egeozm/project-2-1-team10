package io.github.cantstop.screens;

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
import io.github.cantstop.GameAssets;
import io.github.cantstop.Main;
import io.github.cantstop.model.*;
import io.github.cantstop.rules.Rules;
import io.github.cantstop.utensils.ConstantsBE;

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

    private final List<TextButton> optionButtons = new ArrayList<>();

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
    }

    private void switchPlayer() {
        clearOptionButtons();
        optionDiceRows.clear();
        currentRoll = null;
        rolledDice = null;
        Player next;
        if (gameState.toMove() == Player.BLUE) {
            next = Player.RED;
        } else {
            next = Player.BLUE;
        }
        gameState.setToMove(next);
    }

    private void startDiceAnimation() {
        clearOptionButtons();
        optionDiceRows.clear();

        if (rollButton.hasParent()) {
            rollButton.remove();
        }

        currentRoll = DiceRoll.roll(rng);
        rolledDice = null;

        // temporary dice rows (random animation)
        float startY = 500f;
        float rowSpacing = 120f;
        float diceSize = 64f;
        float gap = 20f;
        for (int i = 0; i < 3; i++) {
            float diceY = startY - i * rowSpacing;
            float diceStartX = 600f;
            optionDiceRows.add(new DiceRow(new int[]{1, 2, 3, 4}, diceStartX, diceY, diceSize, gap, i));
        }

        rolling = true;

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
        clearOptionButtons();
        optionDiceRows.clear();

        List<Move> legalMoves = Rules.getLegalMoves(gameState, currentRoll);
        if (legalMoves.isEmpty()) {
            System.out.println("Bust! Switching turn.");
            gameState.setBustPending(true);
            Rules.stop(gameState); // auto stop on bust
            resetForNextRoll();
            return;
        }

        float startY = 300f;
        float rowSpacing = 100f;
        float diceSize = 64f;
        float gap = 20f;

        for (int i = 0; i < legalMoves.size(); i++) {
            Move move = legalMoves.get(i);

            float diceY = startY - i * rowSpacing;
            float diceStartX = 200f;

            optionDiceRows.add(new DiceRow(currentRoll.dice(), diceStartX, diceY, diceSize, gap, i));

            TextButton option = new TextButton("Advance on " + move.sumA() + " & " + move.sumB(), skin);
            option.setSize(200, 40);
            option.setPosition(diceStartX + 50f, diceY);

            option.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    Rules.applyMove(gameState, move);
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
        gameState = GameState.initial(Player.BLUE);
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(0f, 0f, 0f, 1f);

        game.viewport.apply();
        game.batch.setProjectionMatrix(game.viewport.getCamera().combined);
        game.batch.begin();

        // --- Board drawing ---
        game.batch.draw(board, ORIGIN_X, ORIGIN_Y);

        for (int col = 0; col < ConstantsBE.NUM_COLS; col++) {
            ColumnState cs = gameState.columns()[col];

            // --- Permanent markers ---
            int blueHeight = cs.permHeightFor(Player.BLUE);
            int redHeight  = cs.permHeightFor(Player.RED);
            if (blueHeight > 0) game.batch.draw(blueMarker1, colX(col), rowY(blueHeight));
            if (redHeight  > 0) game.batch.draw(redMarker1, colX(col), rowY(redHeight));

            // --- Temp runner for active player ---
            Integer tempHeight = cs.tempHeight();
            if (tempHeight != null) {
                Texture tex = (gameState.toMove() == Player.BLUE) ? blueMarker2 : redMarker2;
                game.batch.draw(tex, colX(col), rowY(tempHeight));
            }
        }

        // --- Dice animation / results ---
        if (rolling) {
            for (DiceRow row : optionDiceRows) {
                row.drawRandom(game.batch, diceFaces, rng);
            }
        } else if (rolledDice != null) {
            for (DiceRow row : optionDiceRows) {
                row.draw(game.batch, diceFaces);
            }
        }

        game.batch.end();

        stage.act(delta);
        stage.draw();
    }


    @Override
    public void resize(int width, int height) {}
    @Override
    public void pause() {}
    @Override
    public void resume() {}
    @Override
    public void hide() {}
    @Override
    public void dispose() {
        stage.dispose();
        skin.dispose();
    }
}
