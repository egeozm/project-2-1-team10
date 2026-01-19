package io.github.cantstop.view.gui.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import io.github.cantstop.view.gui.*;
import com.badlogic.gdx.audio.Music;

/** First screen of the application. Displayed after the application is created. */
public class MenuScreen implements Screen {

    private Main game;
    private Stage stage;
    private Skin skin;
    private Texture background;
    private Music mainMenuMusic;
    private BitmapFont menuFont;
    private TextButton.TextButtonStyle borderStyle;



    public MenuScreen(Main game) {
        this.game = game;
    }

    @Override
    public void show() {

        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);
        skin = SharedSkin.getSkin();
        background = new Texture(Gdx.files.internal("backgrounds/menuBackground.png"));

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


        Label title = new Label("Can't Stop !", skin, "big");
        title.setFontScale(0.8f);
        title.setPosition(
            (stage.getWidth()-title.getWidth())/2f,
            stage.getHeight() - 100
        );

        TextButton playButton = new TextButton("Play", borderStyle);
        TextButton rulesButton = new TextButton("Rules", borderStyle);
        TextButton settingsButton = new TextButton("Settings", borderStyle);
        TextButton exitButton = new TextButton("Exit", borderStyle);
        TextButton helpButton = new TextButton("Help", borderStyle);




        playButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y){
                game.setScreen(new ModeSelectScreen(game));
            }
        });

        settingsButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y){
                game.setScreen(new SettingsScreen(game));
            }
        });

        rulesButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y){
                game.setScreen(new RulesScreen(game));
            }
        });

        helpButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y){
                game.setScreen(new HelpScreen(game));
            }
        });

        exitButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y){
                Gdx.app.exit();
            }
        });

        Table table = new Table();
        table.setFillParent(true);
        table.center();

        table.add(title).padBottom(50).row();
        table.add(playButton).size(200,60).padBottom(20).row();
        table.add(rulesButton).size(200,60).padBottom(20).row();
        table.add(settingsButton).size(200,60).padBottom(20).row();
        table.add(helpButton).size(200,60).padBottom(20).row();
        table.add(exitButton).size(200,60).padBottom(20).row();


        stage.addActor(table);
        // Prepare your screen here.
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        float stageWidth = stage.getViewport().getWorldWidth();
        float stageHeight = stage.getViewport().getWorldHeight();
        float scale = Math.max(stageWidth / background.getWidth(), stageHeight / background.getHeight());
        float drawWidth = background.getWidth() * scale;
        float drawHeight = background.getHeight() * scale;
        float x = (stageWidth - drawWidth) / 2f;
        float y = (stageHeight - drawHeight) / 2f;

        game.batch.setProjectionMatrix(stage.getCamera().combined);
        game.batch.begin();
        game.batch.draw(background, x, y, drawWidth, drawHeight);
        game.batch.end();

// Draw UI
        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
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
        stage.dispose();
        skin.dispose();
        background.dispose();
        if (menuFont != null) menuFont.dispose();
    }
}
