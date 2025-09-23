package io.github.cantStop.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

import java.util.Arrays;

public class PlayScreen implements Screen {

    // Board shape: 11 columns (sums 2..12), 3 slots (blue perm, red perm, temp)
    private static final int NUM_COLS  = 11;
    private static final int NUM_SLOTS = 3;

    // Layout constants – tune to match your board art
    private static final float ORIGIN_X = 200f; // column 0 x
    private static final float ORIGIN_Y = 100f; // base row y
    private static final float CELL_W   = 40f;  // x step per column
    private static final float CELL_H   = 40f;  // y step per height

    private static float colX(int col)  { return ORIGIN_X + col * CELL_W; }
    private static float rowY(int step) { return ORIGIN_Y + step * CELL_H; }

    private SpriteBatch batch;

    private int[][] boardState;      // boardState[col][slot] = height or -1 if empty
    private boolean isBlueTurn = true;

    // Assets
    private GameAssets assets;
    private Texture[] diceTextures;
    private Texture board;
    private Texture blueMarker1, blueMarker2, blueCross;
    private Texture redMarker1, redMarker2, redCross;
    private Texture[] markerTextures; // [0]=blue perm, [1]=red perm, [2]=current player's temp

    private Die[] dice;

    private int[] columnWinner; // -1 = nobody, 0 = blue, 1 = red

    private static final int[] MAX_HEIGHT = {
        3, 5, 7, 9, 11, 13, 11, 9, 7, 5, 3
    };

    public PlayScreen(SpriteBatch batch) {
        this.batch = batch;

        // Allocate the board and initialize to -1 (means “nothing here”)
        boardState = new int[NUM_COLS][NUM_SLOTS];
        for (int c = 0; c < NUM_COLS; c++) {
            Arrays.fill(boardState[c], -1);
        }

        columnWinner = new int[NUM_COLS];
        Arrays.fill(columnWinner, -1);
    }

    @Override
    public void show() {
        // Load textures once this screen is shown
        assets = new GameAssets();
        assets.loadAll();

        // Bind local handles
        board        = assets.board;
        blueMarker1  = assets.blueMarker1;
        blueMarker2  = assets.blueMarker2;
        blueCross    = assets.blueCross;
        redMarker1   = assets.redMarker1;
        redMarker2   = assets.redMarker2;
        redCross     = assets.redCross;
        diceTextures = assets.diceTextures;

        // Set initial marker set: slot 0 = blue perm, slot 1 = red perm, slot 2 = current player's temp
        markerTextures = new Texture[] { blueMarker1, redMarker1, blueMarker2 }; // blue starts

        // Now that textures exist, create dice that depend on them
        dice = new Die[4];
        for (int i = 0; i < dice.length; i++) {
            dice[i] = new Die(diceTextures);
        }
    }

    @Override
    public void render(float delta) {
        handleInput();
        update();

        // Clear screen
        Gdx.gl.glClearColor(0.08f, 0.09f, 0.12f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // Draw everything between begin/end
        batch.begin();
        draw();
        batch.end();
    }

    public void handleInput() {

        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
            isBlueTurn = !isBlueTurn; // enter ends turn
            markerTextures[2] = isBlueTurn ? blueMarker2 : redMarker2;
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            for (Die d : dice) d.roll();
        }
    }

    public void update() {
        // Game state updates go here later
    }

    public void draw() {

        // 1) Board first
        batch.draw(board, 0, 0);

        // 2) Markers on top
        for (int col = 0; col < NUM_COLS; col++) {
            for (int slot = 0; slot < NUM_SLOTS; slot++) {
                int height = boardState[col][slot];
                if (height < 0) continue; // skip empty slots

                Texture tex = markerTextures[slot];
                batch.draw(tex, colX(col), rowY(height));
            }
        }

        // 3) Dice
        float diceStartX = 600f; // pick an x far enough from the board
        float diceY      = 50f;  // height where dice sit

        for (int i = 0; i < dice.length; i++) {
            batch.draw(dice[i].currentFace(), diceStartX + i * 70f, diceY, 64f, 64f);
            // 70f spacing, 64x64 size — adjust to your art
        }

        // 4) Crosses for completed columns
        for (int col = 0; col < NUM_COLS; col++) {
            int winner = columnWinner[col];
            if (winner == -1) continue;

            Texture crossTex = (winner == 0) ? blueCross : redCross;
            batch.draw(crossTex, colX(col), rowY(MAX_HEIGHT[col] - 1));
        }

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
        if (assets != null) assets.dispose();
    }
}
