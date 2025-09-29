package io.github.cantstop.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.Timer;
import io.github.cantstop.GameAssets;
import io.github.cantstop.Main;
import io.github.cantstop.model.DiceRoll;
import io.github.cantstop.model.DiceRow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class PlayScreen implements Screen {

    private final Main game;

    // Board shape: 11 columns (sums 2..12), 3 slots (blue perm, red perm, temp)
    private static final int NUM_COLS = 11;
    private static final int NUM_SLOTS = 3;

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

    private int[][] boardState;
    private boolean isBlueTurn = true;

    // Assets
    private GameAssets assets;
    private Texture[] diceFaces;
    private Texture board;
    private Texture blueMarker1, blueMarker2, blueCross;
    private Texture redMarker1, redMarker2, redCross;
    private Texture[] markerTextures;

    private int[] columnWinner;
    private static final int[] MAX_HEIGHT = {3, 5, 7, 9, 11, 13, 11, 9, 7, 5, 3};

    // UI
    private Stage stage;
    private Skin skin;
    private TextButton rollButton;
    private final List<TextButton> optionButtons = new ArrayList<>();

    // Dice animation + result
    private boolean rolling = false;
    private DiceRoll currentRoll = null;
    private int[] rolledDice = null; // 4 dice faces
    private final Random rng = new Random();

    // Store dice rows to render aligned with buttons
    private final List<DiceRow> optionDiceRows = new ArrayList<>();

    public PlayScreen(Main game) {
        this.game = game;

        boardState = new int[NUM_COLS][NUM_SLOTS];
        for (int c = 0; c < NUM_COLS; c++) {
            Arrays.fill(boardState[c], -1);
        }

        // Demo markers
        boardState[0][0] = 0;
        boardState[3][1] = 2;
        boardState[5][2] = 4;

        columnWinner = new int[NUM_COLS];
        Arrays.fill(columnWinner, -1);
        columnWinner[8] = 0;

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
    }

    private void startDiceAnimation() {
        clearOptionButtons();
        optionDiceRows.clear();

        if (rollButton.hasParent()) {
            rollButton.remove();
        }

        currentRoll = DiceRoll.roll(rng);
        rolledDice = null;

        // Create temporary rows so we see dice during animation
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

        int[][] combos = currentRoll.pairings();
        int[] d = currentRoll.dice();

        float startY = 300f;     // was 500f → lower it so visible in 400px tall screen
        float rowSpacing = 100f; // tighten spacing
        float diceSize = 64f;
        float gap = 20f;

        int[][] rows = {
            { d[0], d[1], d[2], d[3] },
            { d[0], d[2], d[1], d[3] },
            { d[0], d[3], d[1], d[2] }
        };

        for (int i = 0; i < combos.length; i++) {
            float diceY = startY - i * rowSpacing;
            float diceStartX = 200f; // push left so buttons also fit

            optionDiceRows.add(new DiceRow(rows[i], diceStartX, diceY, diceSize, gap, i));

            final int idx = i;
            int left = combos[i][0];
            int right = combos[i][1];
            TextButton option = new TextButton("Advance on " + left + " & " + right, skin);
            option.setSize(150, 40);
            option.setPosition(diceStartX +  50f, diceY);

            option.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    System.out.println("Player picked combo " + Arrays.toString(combos[idx]));
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

        markerTextures = new Texture[]{blueMarker1, redMarker1, blueMarker2};
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(0f, 0f, 0f, 1f);

        game.viewport.apply();
        game.batch.setProjectionMatrix(game.viewport.getCamera().combined);
        game.batch.begin();

        // --- Board drawing (same as before) ---
        game.batch.draw(board, ORIGIN_X, ORIGIN_Y);
        for (int col = 0; col < NUM_COLS; col++) {
            for (int slot = 0; slot < NUM_SLOTS; slot++) {
                int height = boardState[col][slot];
                if (height < 0) continue;
                Texture tex = markerTextures[slot];
                game.batch.draw(tex, colX(col), rowY(height));
            }
        }
        for (int col = 0; col < NUM_COLS; col++) {
            int winner = columnWinner[col];
            if (winner == -1) continue;
            Texture crossTex = (winner == 0) ? blueCross : redCross;
            for (int i = 0; i < MAX_HEIGHT[col]; i++) {
                game.batch.draw(crossTex, colX(col), rowY(i));
            }
        }

        // --- Dice animation / results ---
        if (rolling) {
            // Animate ALL dice rows with random faces
            for (DiceRow row : optionDiceRows) {
                row.drawRandom(game.batch, diceFaces, rng);
            }
        } else if (rolledDice != null) {
            // Show the actual rolled dice in each row
            for (DiceRow row : optionDiceRows) {
                row.draw(game.batch, diceFaces);
            }
        }

        game.batch.end();

        stage.act(delta);
        stage.draw();
    }


    @Override
    public void resize(int width, int height) {
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
        skin.dispose();
    }
}
