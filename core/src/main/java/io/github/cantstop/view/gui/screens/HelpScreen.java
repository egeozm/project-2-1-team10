package io.github.cantstop.view.gui.screens;

import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.cantstop.view.gui.ButtonStyle;
import io.github.cantstop.view.gui.Main;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.graphics.Texture;
import io.github.cantstop.view.gui.SharedSkin;

public class HelpScreen implements Screen {
    private Main game;
    private Stage stage;
    private Texture background;

    private BitmapFont menuFont;
    private TextButton.TextButtonStyle borderStyle;

    public HelpScreen(Main game) {
        this.game = game;
        stage = new Stage(new ScreenViewport());
    }

    public void show() {
        Gdx.input.setInputProcessor(stage);
        Skin skin = SharedSkin.getSkin();
        background = new Texture(Gdx.files.internal("backgrounds/settingsBackground.png"));

        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal("fonts/pixel_font.ttf"));
        FreeTypeFontGenerator.FreeTypeFontParameter params = new FreeTypeFontGenerator.FreeTypeFontParameter();

        params.size = 18;
        params.mono = true;
        params.minFilter = Texture.TextureFilter.Nearest;
        params.magFilter = Texture.TextureFilter.Nearest;
        params.genMipMaps = false;
        params.kerning = false;
        params.borderWidth = 0;
        params.shadowOffsetX = 0;
        params.shadowOffsetY = 0;

        menuFont = generator.generateFont(params);
        generator.dispose();

        borderStyle = ButtonStyle.createBorderButtonStyle(menuFont);
        borderStyle.fontColor = com.badlogic.gdx.graphics.Color.WHITE;
        borderStyle.overFontColor = com.badlogic.gdx.graphics.Color.WHITE;
        borderStyle.downFontColor = com.badlogic.gdx.graphics.Color.WHITE;

        Label label = new Label("How to navigate the menu?(i dont think so)/tips for how to play(strategy-wise ig)/ik iss a bit too much to do this but maybe also credits or sum like that. Also if something doesnt work before we have to present it we can mention it here or sum", SharedSkin.getSkin(), "default");
        label.setPosition(200, 300);
        stage.addActor(label);

        TextButton backButton = new TextButton("Back", borderStyle);

        Table table = new Table();
        table.setFillParent(true);
        table.bottom().right();

        table.add(backButton).size(200, 60).pad(10);



        backButton.addListener(new ClickListener() {
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new MenuScreen(game));
            }
        });

        stage.addActor(table);
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        float screenWidth = Gdx.graphics.getWidth();
        float screenHeight = Gdx.graphics.getHeight();

        // Scale to fill the screen proportionally (may crop edges)
        float scale = Math.max(screenWidth / background.getWidth(), screenHeight / background.getHeight());
        float drawWidth = background.getWidth() * scale;
        float drawHeight = background.getHeight() * scale;
        float x = (screenWidth - drawWidth) / 2f;
        float y = (screenHeight - drawHeight) / 2f;

        game.batch.setProjectionMatrix(stage.getCamera().combined);
        game.batch.begin();
        game.batch.draw(background, x, y, drawWidth, drawHeight);
        game.batch.end();

        stage.act(delta);
        stage.draw();
    }

    @Override public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
        if(width <= 0 || height <= 0) return;
    }

    @Override public void pause() {}

    @Override public void resume() {}

    @Override public void hide() {}

    @Override public void dispose() {
        stage.dispose();
        background.dispose();
        if (menuFont != null) menuFont.dispose();
    }
}

