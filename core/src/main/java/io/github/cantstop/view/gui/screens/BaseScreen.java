package io.github.cantstop.view.gui.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.utils.viewport.Viewport;
import io.github.cantstop.view.gui.ButtonStyle;
import io.github.cantstop.view.gui.Main;
import io.github.cantstop.view.gui.PostProcessor;

public abstract class BaseScreen implements Screen {

    protected final Main game;
    protected Stage stage;
    protected PostProcessor postProcessor;
    protected float shaderTime = 0f;

    // Shared fonts
    protected BitmapFont font;
    public BitmapFont buttonFont;
    protected TextButton.TextButtonStyle borderStyle;

    public BaseScreen(Main game) {
        this.game = game;
    }

    @Override
    public void show() {
        // 1. Setup Stage with the GAME viewport (FitViewport usually)
        // Use game.viewport so we get the same "black bars" behavior everywhere
        stage = new Stage(game.viewport, game.batch);
        Gdx.input.setInputProcessor(stage);

        // 2. Setup PostProcessor
        // (Keep it enabled for all screens to match PlayScreen)
        postProcessor = new PostProcessor(game.batch, game.viewport);

        // 3. Setup Fonts (standardized)
        generateFonts();

        // 4. Setup Styles
        borderStyle = ButtonStyle.createBorderButtonStyle(buttonFont);
    }

    private void generateFonts() {
        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal("fonts/pixel_font.ttf"));
        FreeTypeFontGenerator.FreeTypeFontParameter params = new FreeTypeFontGenerator.FreeTypeFontParameter();

        // -- Small font (for detailed text) --
        params.size = 8;
        params.mono = true;
        params.minFilter = Texture.TextureFilter.Nearest;
        params.magFilter = Texture.TextureFilter.Nearest;
        params.genMipMaps = false;
        params.kerning = false;
        params.borderWidth = 0;
        params.shadowOffsetX = 0;
        params.shadowOffsetY = 0;
        font = generator.generateFont(params);

        // -- Button/Header font --
        params.size = 16;
        buttonFont = generator.generateFont(params);

        generator.dispose();
    }

    @Override
    public void render(float delta) {
        shaderTime += delta;

        // 1. Apply Viewport
        game.viewport.apply();

        // 2. Start PostProcessing
        postProcessor.begin();

        // 3. Clear Screen (inside FBO)
        Gdx.gl.glClearColor(0.1f, 0.1f, 0.15f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // 4. Draw Content
        game.batch.setProjectionMatrix(game.viewport.getCamera().combined);
        game.batch.begin();
        renderScreenContent(delta);
        game.batch.end();

        // 5. Draw UI
        stage.act(delta);
        stage.draw();

        // 6. End PostProcessing (draw FBO to screen with shader)
        postProcessor.end(shaderTime, game.viewport);
    }

    /**
     * Subclasses implement this to draw backgrounds, sprites, etc.
     * The batch is ALREADY begin()-ed here.
     */
    protected abstract void renderScreenContent(float delta);

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
        if (stage != null)
            stage.dispose();
        if (font != null)
            font.dispose();
        if (buttonFont != null)
            buttonFont.dispose();
        if (postProcessor != null)
            postProcessor.dispose();
        // game.assets is managed by Main, do not dispose here
    }
}
