package io.github.cantstop.view.gui.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;

import io.github.cantstop.view.gui.Main;
import io.github.cantstop.view.gui.SharedSkin;

public class ModeSelectScreen implements Screen {

    private Stage stage;
    private Skin skin;
    private final Main game;
    private Texture background;

    public ModeSelectScreen(Main game) {
        this.game = game;
    }

    public void show(){
        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);

        skin = SharedSkin.getSkin();
        background = new Texture(Gdx.files.internal("backgrounds/menuBackground.png"));

        TextButton pvpButton = new TextButton("PvP", skin);
        TextButton pvaiButton = new TextButton("PvAI", skin);
        TextButton backButton = new TextButton("Back", skin);

        pvpButton.addListener(new ClickListener() {
            public void clicked(InputEvent event, float x, float y) {
                game.startGame(false);
            }
        });
        pvaiButton.addListener(new ClickListener() {
            public void clicked(InputEvent event, float x, float y) {
                game.startGame(true);
            }
        });
        backButton.addListener(new ClickListener() {
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new MenuScreen(game));
            }
        });

        Label title = new Label("Choose How to Play", skin, "big");
        title.setFontScale(0.8f);

        Table table = new Table();
        table.setFillParent(true);
        table.center();

        table.add(title).padBottom(40).row();
        table.add(pvpButton).size(220,60).padBottom(20).row();
        table.add(pvaiButton).size(220,60).padBottom(90).row();
        table.add(backButton).size(220,60).padBottom(20).row();

        stage.addActor(table);

    }
    public void render(float delta){
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
    public void resize(int w, int h) { stage.getViewport().update(w, h, true); }
    public void pause() {}
    public void resume() {}
    public void hide() {}
    public void dispose() {
        stage.dispose();
        background.dispose();
    }

}
