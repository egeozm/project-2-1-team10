package io.github.cantstop.view.gui.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import io.github.cantstop.view.gui.GuiConstants;
import io.github.cantstop.view.gui.Main;

public class MenuScreen extends BaseScreen {

    public MenuScreen(Main game) {
        super(game);
    }

    @Override
    public void show() {
        super.show();

//        // Title Style
//        Label.LabelStyle titleStyle = new Label.LabelStyle(buttonFont, GuiConstants.textColor);
//        Label title = new Label("Can't Stop !", titleStyle);
//        title.setFontScale(2.5f); // Make it big

        // Buttons
        TextButton playButton = new TextButton("Play", borderStyle);
        TextButton rulesButton = new TextButton("Rules", borderStyle);
        TextButton settingsButton = new TextButton("Settings", borderStyle);
        TextButton exitButton = new TextButton("Exit", borderStyle);
        TextButton helpButton = new TextButton("Help", borderStyle);

        playButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new ModeSelectScreen(game));
            }
        });

        settingsButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new SettingsScreen(game));
            }
        });

        rulesButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new RulesScreen(game));
            }
        });

        helpButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new HelpScreen(game));
            }
        });

        exitButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                Gdx.app.exit();
            }
        });

        // Layout
        Table table = new Table();
        table.setFillParent(true);
        table.center();
        table.padTop(100f);

//        table.add(title).padBottom(10).row();
        table.add(playButton).size(160, 30).padBottom(10).row();
        table.add(rulesButton).size(160, 30).padBottom(10).row();
        table.add(settingsButton).size(160, 30).padBottom(10).row();
        table.add(helpButton).size(160, 30).padBottom(10).row();
        table.add(exitButton).size(160, 30).padBottom(10).row();

        stage.addActor(table);
    }

    @Override
    protected void renderScreenContent(float delta) {

        float stageWidth = game.viewport.getWorldWidth();
        float stageHeight = game.viewport.getWorldHeight();
        // Draw background, scaling to fill
        if (game.assets.menuBackground != null) {

            // Draw background to cover the viewport
            game.batch.setColor(0.6f, 0.6f, 0.7f, 1f); // apply dimming
            game.batch.draw(game.assets.menuBackground, 0, 0, stageWidth, stageHeight);
            game.batch.setColor(Color.WHITE);
        }

        game.batch.draw(game.assets.logo, stageWidth/2f - game.assets.logo.getWidth()*3f/2f, 220f, game.assets.logo.getWidth()*3f, game.assets.logo.getHeight()*3f);
    }
}
