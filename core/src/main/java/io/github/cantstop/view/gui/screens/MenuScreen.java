package io.github.cantstop.view.gui.screens;

import com.badlogic.gdx.Gdx;
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

        // Title Style
        Label.LabelStyle titleStyle = new Label.LabelStyle(buttonFont, GuiConstants.textColor);
        Label title = new Label("Can't Stop !", titleStyle);
        title.setFontScale(2.5f); // Make it big

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

        table.add(title).padBottom(40).row();
        table.add(playButton).size(120, 35).padBottom(10).row();
        table.add(rulesButton).size(120, 35).padBottom(10).row();
        table.add(settingsButton).size(120, 35).padBottom(10).row();
        table.add(helpButton).size(120, 35).padBottom(10).row();
        table.add(exitButton).size(120, 35).padBottom(10).row();

        stage.addActor(table);
    }

    @Override
    protected void renderScreenContent(float delta) {
        // Draw background, scaling to fill
        if (game.assets.menuBackground != null) {
            float stageWidth = game.viewport.getWorldWidth();
            float stageHeight = game.viewport.getWorldHeight();

            // Draw background to cover the viewport
            game.batch.draw(game.assets.menuBackground,
                    0, 0,
                    stageWidth, stageHeight);
        }
    }
}
