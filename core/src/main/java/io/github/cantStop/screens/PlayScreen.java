package io.github.cantstop.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

public class PlayScreen implements Screen {

    private int[][] boardState; // for each column saves
    private boolean isBlueTurn; // true if blue player's turn, false if red player's turn
    private Die[] dice;

    private SpriteBatch batch;
    private Texture[] diceTextures;
    private Texture board;
    private Texture blueMarker1;
    private Texture blueMarker2;
    private Texture blueCross;
    private Texture redMarker1;
    private Texture redMarker2;
    private Texture redCross;
    private Texture[] markerTextures;

    // TO DO: Implement an asset manager to load textures

    public PlayScreen(SpriteBatch batch) {
        boardState = new int[11][3];
        isBlueTurn = true;
        this.batch = batch;

        this.dice = new Die[4];
        for (int i = 0; i < dice.length; i++) {
            dice[i] = new Die(diceTextures);
        }
    }

    @Override
    public void show() {

    }

    @Override
    public void render(float delta) {
        handleInput();
        update();
        draw();
    }

    public void handleInput() {

        if (Gdx.input.isKeyPressed(Input.Keys.ENTER)) {
            isBlueTurn = !isBlueTurn; // enter ends turn
            if (isBlueTurn) {
                markerTextures[2] = blueMarker2;
            } else {
                markerTextures[2] = redMarker2;
            }
        }
    }

    public void update() {

    }

    public void draw() {

        // draw the board background
        batch.draw(board, 0, 0);

        // draw the markers in their positions
        for (int i = 0; i < boardState.length; i++) {
            for (int j = 0; j < boardState[i].length; j++) {
                batch.draw(markerTextures[j], 200 + i * 40, 100 + boardState[i][j] * 40);
            }
        } // TO DO: make sure positions line up with board texture

        // TO DO: cross out finished rows
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

    }
}
