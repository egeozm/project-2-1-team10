package io.github.cantstop.view.gui.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

import io.github.cantstop.view.gui.*;

public class AiSelectScreen implements Screen {

    private final Main game;
    private Stage stage;
    private Skin skin;
    private Texture background;
    private BitmapFont menuFont;
    private TextButton.TextButtonStyle borderStyle;

    public AiSelectScreen(Main game) {
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

        Label title = new Label("Choose Opponent", skin, "big");
        title.setFontScale(0.8f);

        TextButton ruleBtn = new TextButton("Rule-Based", borderStyle);
        TextButton mctsBtn = new TextButton("MCTS", borderStyle);
        TextButton minimaxBtn = new TextButton("ExpectiMiniMax", borderStyle);
        TextButton annBtn = new TextButton("ANN", borderStyle);
        TextButton hybridBtn = new TextButton("Hybrid", borderStyle);
        TextButton backBtn = new TextButton("Back", borderStyle);


        ruleBtn.addListener(new ClickListener() {
            @Override public void clicked(InputEvent event, float x, float y) {
                game.startGame(new AiConfig(AgentType.RULE_BASED, 0));
            }
        });

        mctsBtn.addListener(new ClickListener() {
            @Override public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new DifficultySelectScreen(game, AgentType.MCTS));
            }
        });

        minimaxBtn.addListener(new ClickListener() {
            @Override public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new DifficultySelectScreen(game, AgentType.MINIMAX));
            }
        });

        annBtn.addListener(new ClickListener() {
            @Override public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new AnnConfigScreen(game));
            }
        });

        hybridBtn.addListener(new ClickListener() {
            @Override public void clicked(InputEvent event, float x, float y) {
                game.startGame(new AiConfig(AgentType.HYBRID, 0)); // no difficulty needed
            }
        });

        backBtn.addListener(new ClickListener() {
            @Override public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new ModeSelectScreen(game));
            }
        });

        Table table = new Table();
        table.setFillParent(true);
        table.center();

        table.add(title).padBottom(40).row();
        table.add(ruleBtn).size(220, 60).padBottom(20).row();
        table.add(mctsBtn).size(220, 60).padBottom(20).row();
        table.add(minimaxBtn).size(220, 60).padBottom(60).row();
        table.add(annBtn).size(220, 60).padBottom(20).row();
        table.add(hybridBtn).size(220, 60).padBottom(60).row();
        table.add(backBtn).size(220, 60).padBottom(20).row();

        stage.addActor(table);
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        game.batch.setProjectionMatrix(stage.getCamera().combined);
        game.batch.begin();

        float stageWidth = stage.getViewport().getWorldWidth();
        float stageHeight = stage.getViewport().getWorldHeight();
        float scale = Math.max(stageWidth / background.getWidth(), stageHeight / background.getHeight());
        float drawWidth = background.getWidth() * scale;
        float drawHeight = background.getHeight() * scale;
        float x = (stageWidth - drawWidth) / 2f;
        float y = (stageHeight - drawHeight) / 2f;

        game.batch.draw(background, x, y, drawWidth, drawHeight);
        game.batch.end();

        stage.act(delta);
        stage.draw();
    }

    @Override public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }
    @Override public void pause() {

    }
    @Override public void resume() {

    }
    @Override public void hide() {

    }

    @Override
    public void dispose() {
        stage.dispose();
        background.dispose();
        if (menuFont != null) menuFont.dispose();
    }
}
