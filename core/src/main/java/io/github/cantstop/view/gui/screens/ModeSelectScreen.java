package io.github.cantstop.view.gui.screens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import io.github.cantstop.view.gui.GuiConstants;
import io.github.cantstop.view.gui.Main;

public class ModeSelectScreen extends BaseScreen {

    public ModeSelectScreen(Main game) {
        super(game);
    }

    @Override
    public void show() {
        super.show();

        Label title = new Label("Choose How to Play", new Label.LabelStyle(buttonFont, GuiConstants.textColor));
        title.setFontScale(1.2f);

        TextButton pvpButton = new TextButton("PvP", borderStyle);
        TextButton pvaiButton = new TextButton("PvAI", borderStyle);
        TextButton backButton = new TextButton("Back", borderStyle);

        pvpButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.startGame();
            }
        });

        pvaiButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new AiSelectScreen(game));
            }
        });

        backButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new MenuScreen(game));
            }
        });

        Table table = new Table();
        table.setFillParent(true);
        table.center();

        table.add(title).padBottom(30).row();
        table.add(pvpButton).size(160, 40).padBottom(15).row();
        table.add(pvaiButton).size(160, 40).padBottom(15).row();
        table.add(backButton).size(160, 40).padBottom(15).row();

        stage.addActor(table);
    }

    @Override
    protected void renderScreenContent(float delta) {
        if (game.assets.menuBackground != null) {
            float stageWidth = game.viewport.getWorldWidth();
            float stageHeight = game.viewport.getWorldHeight();
            game.batch.setColor(0.6f, 0.6f, 0.7f, 1f); // apply dimming
            game.batch.draw(game.assets.menuBackground, 0, 0, stageWidth, stageHeight);
            game.batch.setColor(Color.WHITE);
        }
    }
}
