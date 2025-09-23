package io.github.cantStop.screens;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;


/** First screen of the application. Displayed after the application is created. */
public class MenuScreen implements Screen {

    private final Game game;
    private final SpriteBatch batch;
    private final BitmapFont font;

    public MenuScreen(Game game) {
        this.game = game;
        this.batch = new SpriteBatch();
        this.font = new BitmapFont(); // LibGDX default font
    }

    @Override
    public void show() {
        // Prepare your screen here.
    }

    @Override
    public void render(float delta) {
        // clear screen
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        batch.begin();
        font.draw(batch, "Cant Stop", 100, 300);
        font.draw(batch, "Press ENTER to start", 100, 250);
        batch.end();

        // input: ENTER -> switch to PlayScreen
        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
            game.setScreen(new PlayScreen(new SpriteBatch()));
            dispose();
        }
    }

    @Override
    public void resize(int width, int height) {
        // If the window is minimized on a desktop (LWJGL3) platform, width and height are 0, which causes problems.
        // In that case, we don't resize anything, and wait for the window to be a normal size before updating.
        if(width <= 0 || height <= 0) return;

        // Resize your screen here. The parameters represent the new window size.
    }

    @Override
    public void pause() {
        // Invoked when your application is paused.
    }

    @Override
    public void resume() {
        // Invoked when your application is resumed after pause.
    }

    @Override
    public void hide() {
        // This method is called when another screen replaces this one.
    }

    @Override
    public void dispose() {
        batch.dispose();
        font.dispose();
    }
}
