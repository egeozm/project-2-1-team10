package io.github.cantstop.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.ScreenUtils;
import io.github.cantstop.Die;
import io.github.cantstop.GameAssets;
import io.github.cantstop.Main;

import java.util.Arrays;

public class PlayScreen implements Screen {

    private final Main game;

    // Board shape: 11 columns (sums 2..12), 3 slots (blue perm, red perm, temp)
    private static final int NUM_COLS = 11;
    private static final int NUM_SLOTS = 3;

    // Layout constants – tune to match your board art
    private static final float ORIGIN_X = 60f; // column 0 x
    private static final float ORIGIN_Y = 60f; // base row y
    private static final float CELL_W = 24f;  // x step per column
    private static final float CELL_H = 18f;  // y step per height

    private static float colX(int col) {
        return ORIGIN_X + col * CELL_W + 4f;
    }

    private static float rowY(int step) {
        return ORIGIN_Y + step * CELL_H + 1f;
    }

    private int[][] boardState;      // boardState[column][slot] = height or -1 if empty
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

    public PlayScreen(Main game) {
        this.game = game;

        // Allocate the board and initialize to -1 (means “nothing here”)
        boardState = new int[NUM_COLS][NUM_SLOTS];
        for (int c = 0; c < NUM_COLS; c++) {
            Arrays.fill(boardState[c], -1);
        }

        // Temporary demo markers so you can see them
        boardState[0][0] = 0; // column 2, blue perm marker at base
        boardState[3][1] = 2; // column 5, red perm marker at height 2
        boardState[5][2] = 4; // column 7, temp marker at height 4

        columnWinner = new int[NUM_COLS];
        Arrays.fill(columnWinner, -1);
        columnWinner[8] = 0;
    }

    @Override
    public void show() {
        // Load textures once this screen is shown
        assets = new GameAssets();
        assets.loadAll();

        // Bind local handles
        board = assets.board;
        blueMarker1 = assets.blueMarker1;
        blueMarker2 = assets.blueMarker2;
        blueCross = assets.blueCross;
        redMarker1 = assets.redMarker1;
        redMarker2 = assets.redMarker2;
        redCross = assets.redCross;
        diceTextures = assets.diceTextures;

        // Set initial marker set: slot 0 = blue perm, slot 1 = red perm, slot 2 = current player's temp
        markerTextures = new Texture[]{blueMarker1, redMarker1, blueMarker2}; // blue starts

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
        draw();
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

        ScreenUtils.clear(0f, 0f, 0f, 1f); // black background
        game.viewport.apply();
        game.batch.setProjectionMatrix(game.viewport.getCamera().combined);
        game.batch.begin();

        // 1) Board first
        game.batch.draw(board, ORIGIN_X, ORIGIN_Y);

        // 2) Markers on top
        for (int col = 0; col < NUM_COLS; col++) {
            for (int slot = 0; slot < NUM_SLOTS; slot++) {
                int height = boardState[col][slot];
                if (height < 0) continue; // skip empty slots

                Texture tex = markerTextures[slot];
                game.batch.draw(tex, colX(col), rowY(height));
            }
        }

        // 3) Dice
        float diceStartX = 350f; // pick an x far enough from the board
        float diceY = 250f;  // height where dice sit

        for (int i = 0; i < dice.length; i++) {
            game.batch.draw(dice[i].currentFace, diceStartX + i * 40f, diceY, 32f, 32f);
        }

        // 4) Crosses for completed columns
        for (int col = 0; col < NUM_COLS; col++) {
            int winner = columnWinner[col];
            if (winner == -1) continue;

            Texture crossTex = (winner == 0) ? blueCross : redCross;

            for (int i = 0; i < MAX_HEIGHT[col]; i++) {
                game.batch.draw(crossTex, colX(col), rowY(i));
            }
        }

        game.batch.end();
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
        if (assets != null) assets.dispose();
    }
}
