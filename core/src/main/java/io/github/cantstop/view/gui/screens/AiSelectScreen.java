package io.github.cantstop.view.gui.screens;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import io.github.cantstop.view.gui.Main;
import io.github.cantstop.view.gui.GuiConstants;
import io.github.cantstop.view.gui.AgentType;
import io.github.cantstop.view.gui.AiConfig;

public class AiSelectScreen extends BaseScreen {

    public AiSelectScreen(Main game) {
        super(game);
    }

    @Override
    public void show() {
        super.show();

        Label title = new Label("Choose Opponent", new Label.LabelStyle(buttonFont, GuiConstants.textColor));
        title.setFontScale(1.2f);

        TextButton ruleBtn = new TextButton("Rule-Based", borderStyle);
        TextButton mctsBtn = new TextButton("MCTS", borderStyle);
        TextButton minimaxBtn = new TextButton("ExpectiMiniMax", borderStyle);
        TextButton annBtn = new TextButton("ANN", borderStyle);
        TextButton hybridBtn = new TextButton("Hybrid", borderStyle);
        TextButton backBtn = new TextButton("Back", borderStyle);

        ruleBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.startGame(new AiConfig(AgentType.RULE_BASED, 0));
            }
        });

        mctsBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new DifficultySelectScreen(game, AgentType.MCTS));
            }
        });

        minimaxBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new DifficultySelectScreen(game, AgentType.MINIMAX));
            }
        });

        annBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new AnnConfigScreen(game));
            }
        });

        hybridBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.startGame(new AiConfig(AgentType.HYBRID, 0));
            }
        });

        backBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new ModeSelectScreen(game));
            }
        });

        Table table = new Table();
        table.setFillParent(true);
        table.center();

        table.add(title).padBottom(30).row();
        table.add(ruleBtn).size(180, 40).padBottom(10).row();
        table.add(mctsBtn).size(180, 40).padBottom(10).row();
        table.add(minimaxBtn).size(180, 40).padBottom(10).row();
        table.add(annBtn).size(180, 40).padBottom(10).row();
        table.add(hybridBtn).size(180, 40).padBottom(20).row();
        table.add(backBtn).size(180, 40).padBottom(10).row();

        stage.addActor(table);
    }

    @Override
    protected void renderScreenContent(float delta) {
        if (game.assets.menuBackground != null) {
            float stageWidth = game.viewport.getWorldWidth();
            float stageHeight = game.viewport.getWorldHeight();
            game.batch.draw(game.assets.menuBackground,
                    0, 0,
                    stageWidth, stageHeight);
        }
    }
}
