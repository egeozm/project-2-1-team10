package io.github.cantstop.view.gui.screens;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import io.github.cantstop.view.gui.AgentType;
import io.github.cantstop.view.gui.AiConfig;
import io.github.cantstop.view.gui.ButtonStyle;
import io.github.cantstop.view.gui.GuiConstants;
import io.github.cantstop.view.gui.Main;

public class AnnConfigScreen extends BaseScreen {

    public AnnConfigScreen(Main game) {
        super(game);
    }

    @Override
    public void show() {
        super.show();

        Label title = new Label("Configure ANN", new Label.LabelStyle(buttonFont, GuiConstants.textColor));
        title.setFontScale(1.2f);

        Label.LabelStyle labelStyle = new Label.LabelStyle(font, GuiConstants.textColor);
        TextField.TextFieldStyle tfStyle = ButtonStyle.createTextFieldStyle(font);

        Label weightsLabel = new Label("Weights path:", labelStyle);
        TextField weightsField = new TextField("ann_weights_mcts.annw", tfStyle);

        Label thrLabel = new Label("Roll threshold (0-1):", labelStyle);
        TextField thrField = new TextField("0.55", tfStyle);

        TextButton startBtn = new TextButton("Start", borderStyle);
        TextButton backBtn = new TextButton("Back", borderStyle);

        startBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                String w = weightsField.getText().trim();
                String t = thrField.getText().trim();
                // Default path logic preserved from original
                if (w.isEmpty()) {
                    w = "core/src/main/java/io/github/cantstop/model/ai/AI_ANN/ann_weights_mcts.annw";
                }
                float thr = 0.45f;
                try {
                    thr = Float.parseFloat(t);
                } catch (Exception ignored) {
                }
                game.startGame(new AiConfig(AgentType.ANN, 0, w, thr));
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

        table.add(weightsLabel).left().padBottom(5).row();
        table.add(weightsField).width(300).padBottom(15).row();

        table.add(thrLabel).left().padBottom(5).row();
        table.add(thrField).width(150).padBottom(25).row();

        table.add(startBtn).size(160, 40).padBottom(10).row();
        table.add(backBtn).size(160, 40).padBottom(10).row();

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
