package io.github.cantstop.view.gui.screens;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import io.github.cantstop.view.gui.GuiConstants;
import io.github.cantstop.view.gui.Main;

public class HelpScreen extends BaseScreen {

    public HelpScreen(Main game) {
        super(game);
    }

    @Override
    public void show() {
        super.show();

        Table root = new Table();
        root.setFillParent(true);
        stage.addActor(root);

        Label title = new Label("Help API", new Label.LabelStyle(buttonFont, GuiConstants.textColor));
        title.setFontScale(1.5f);
        root.add(title).padTop(20).padBottom(30).row();

        String helpText = "How to navigate?\nUse the buttons.\n\nTips:\n- Secure columns early.\n- Don't be too greedy!\n\nCredits:\n- Team 10";
        Label content = new Label(helpText, new Label.LabelStyle(font, GuiConstants.textColor));
        content.setWrap(true);
        root.add(content).width(400).center().padBottom(40).row();

        TextButton backButton = new TextButton("Back", borderStyle);
        backButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new MenuScreen(game));
            }
        });

        root.add(backButton).size(120, 35).bottom();
    }

    @Override
    protected void renderScreenContent(float delta) {
        if (game.assets.settingsBackground != null) {
            float stageWidth = game.viewport.getWorldWidth();
            float stageHeight = game.viewport.getWorldHeight();
            game.batch.draw(game.assets.settingsBackground,
                    0, 0,
                    stageWidth, stageHeight);
        }
    }
}
