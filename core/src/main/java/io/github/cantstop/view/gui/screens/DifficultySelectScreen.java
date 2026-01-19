package io.github.cantstop.view.gui.screens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import io.github.cantstop.view.gui.AgentType;
import io.github.cantstop.view.gui.AiConfig;
import io.github.cantstop.view.gui.GuiConstants;
import io.github.cantstop.view.gui.Main;

public class DifficultySelectScreen extends BaseScreen {

    private final AgentType agentType;

    public DifficultySelectScreen(Main game, AgentType agentType) {
        super(game);
        this.agentType = agentType;
    }

    @Override
    public void show() {
        if (agentType == AgentType.ANN || agentType == AgentType.HYBRID) {
            game.startGame(new AiConfig(agentType, 0));
            return;
        }

        super.show();

        Label title = new Label("Select Difficulty", new Label.LabelStyle(buttonFont, GuiConstants.textColor));
        title.setFontScale(1.2f);

        TextButton easyBtn = new TextButton("Easy", borderStyle);
        TextButton mediumBtn = new TextButton("Medium", borderStyle);
        TextButton hardBtn = new TextButton("Hard", borderStyle);
        TextButton backBtn = new TextButton("Back", borderStyle);

        easyBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.startGame(new AiConfig(agentType, 10));
            }
        });

        mediumBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.startGame(new AiConfig(agentType, 50));
            }
        });

        hardBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.startGame(new AiConfig(agentType, 200));
            }
        });

        backBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new AiSelectScreen(game));
            }
        });

        Table table = new Table();
        table.setFillParent(true);
        table.center();

        table.add(title).padBottom(30).row();
        table.add(easyBtn).size(160, 40).padBottom(15).row();
        table.add(mediumBtn).size(160, 40).padBottom(15).row();
        table.add(hardBtn).size(160, 40).padBottom(30).row();
        table.add(backBtn).size(160, 40).padBottom(15).row();

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
