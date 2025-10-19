package io.github.cantstop.frontend.screens;

import com.badlogic.gdx.Screen;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.cantstop.frontend.Main;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import io.github.cantstop.frontend.SharedSkin;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;


public class SettingsScreen implements Screen {
    private Main game;
    private Stage stage;
    private Texture background;
    private Table content;

    public SettingsScreen(Main game) {
        this.game = game;
        this.stage = new Stage(new ScreenViewport());
    }
    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
        Skin skin = SharedSkin.getSkin();
        background = new Texture(Gdx.files.internal("backgrounds/settingsBackground.png"));

        content = new Table();
        content.setFillParent(true);
        content.top().padTop(200).padRight(300); //idk why it s inversed


        Label label = new Label("what do u expect bro", SharedSkin.getSkin() , "default");
        label.setPosition(200, 300);

        TextButton backButton = new TextButton("Back", skin);
        TextButton savesettingsButton = new TextButton("Save settings", skin);

        savesettingsButton.getLabel().setFontScale(0.4f);

        Table table2 = new Table();
        table2.setFillParent(true);
        table2.bottom().right();

        table2.add(backButton).size(200, 60).pad(10);

        Table table3 = new Table();
        table3.setFillParent(true);
        table3.bottom().left();

        table3.add(savesettingsButton).size(200, 60).pad(10);



        TextButton audioButton = new TextButton("Audio", skin);
        TextButton generalButton = new TextButton("General", skin);
        TextButton visualsButton = new TextButton("Visuals", skin);
        //maybe for visuals we can add colorblind mode or sum which technically it is really easy to do we just change the colors for the board/pawns
        //or maybe create more board designs if we have time
        //or different pawn designs or sum

        Table table = new Table();
        table.setFillParent(true);
        table.top();

        table.add(label).padBottom(50).row();
        table.add(generalButton).size(200,60).pad(20);
        table.add(audioButton).size(200,60).pad(20);
        table.add(visualsButton).size(200,60).pad(20);


        backButton.addListener(new ClickListener() {
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new MenuScreen(game));
            }
        });
        generalButton.addListener(new ClickListener() {
            public void clicked(InputEvent event, float x, float y) {
                content.clear();

                content.add(new Label("Language", skin ,"big")).left();
                content.row().padTop(50);

                content.add(new Label("Reset to default settings", skin , "big")).left();
                CheckBox resetDefSettingsCheck = new CheckBox("" , skin);
                content.add(resetDefSettingsCheck).left().padLeft(20);

            }
        });

        audioButton.addListener(new ClickListener() {
            public void clicked(InputEvent event, float x, float y) {
                content.clear();

                content.add(new Label("Music volume", skin ,"big")).left();
                content.row().padTop(50);
                Slider musicSlider = new Slider(0,100,0.5f,false,skin);
                content.add(musicSlider).width(200);
                content.row().padTop(50);

                content.add(new Label("Sound Effects Volume", skin , "big")).left();
                content.row().padTop(50);
                Slider sfxSlider = new Slider(0,100,0.5f,false,skin);
                content.add(sfxSlider).width(200);
                content.row().padTop(50);

                content.add(new Label("Mute all", skin , "big")).left();
                CheckBox muteAllCheck = new CheckBox("", skin );
                content.add(muteAllCheck).left();
                content.row();

            }
        });

        visualsButton.addListener(new ClickListener() {
            public void clicked(InputEvent event, float x, float y) {
                content.clear();

                content.add(new Label("Music volume", skin ,"big")).left();
                content.row().padTop(50);
                Slider musicSlider = new Slider(0,100,0.5f,false,skin);
                content.add(musicSlider).width(200);
                content.row().padTop(50);

                content.add(new Label("Sound Effects Volume", skin , "big")).left();
                content.row().padTop(50);
                Slider sfxSlider = new Slider(0,100,0.5f,false,skin);
                content.add(sfxSlider).width(200);

            }
        });

        stage.addActor(table);
        stage.addActor(table2);
        stage.addActor(table3);
        stage.addActor(content);
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
    }
}

