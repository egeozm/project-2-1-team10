package io.github.cantstop.view.gui.screens;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import io.github.cantstop.view.gui.GuiConstants;
import io.github.cantstop.view.gui.Main;

public class RulesScreen extends BaseScreen {

    public RulesScreen(Main game) {
        super(game);
    }

    @Override
    public void show() {
        super.show();

        Label.LabelStyle headerStyle = new Label.LabelStyle(buttonFont, GuiConstants.textColor);
        Label.LabelStyle bodyStyle = new Label.LabelStyle(font, GuiConstants.textColor);

        Label title = new Label("Rules", headerStyle);
        title.setFontScale(1.2f);

        Table root = new Table();
        root.setFillParent(true);
        root.top();

        // ScrollPane for long text
        Table textTable = new Table();
        textTable.top().left();

        addSection(textTable, "Goal",
                "To be the first player to reach the top of any three columns.",
                headerStyle, bodyStyle);

        addSection(textTable, "Playing",
                "To start your turn, roll all four dice. Look your roll over carefully. Then split your roll in half in any way you wish, and add the two dice in each half. The purpose: to create a pair of numbers.\n"
                        +
                        "Example: Let's say you roll a 1-5-4-6. With this roll, you can create any of the following pairs: 6 and 10 (1+5 and 4+6); or 5 and 11 (1+4 and 5+6); or 9 and 7 (5+4 and 1+6).\n\n"
                        +
                        "The pair of numbers you choose to create represents the two columns into which you must now place markers.\n"
                        +
                        "Example: On this roll of 1-5-4-6, let's say you choose 6 and 10 as your pair. You must now place a marker into the \"6\" column and another marker into the \"10\" column.\n\n"
                        +
                        "In this game you may roll more than once on a single turn. On each additional roll, you also create a pair of numbers in the same way.\n"
                        +
                        "a) Let's say you roll again and create a pair that includes a number you've already chosen. When this happens, move the marker up one space.\n"
                        +
                        "b) Let's say you roll again and decide to create a pair with a new number. If you have another marker left, you must place it into the new marker's column.\n"
                        +
                        "You may continue to roll as long as your last roll allowed you either to place a marker or to move one up. If you prefer, you may stop your turn whenever you wish.\n",
                headerStyle, bodyStyle);

        addSection(textTable, "Placing a Marker",
                "a) If you choose a column that does not already have one of your colored squares in it, place the marker onto the space at the bottom of that column.\n"
                        +
                        "b) If you choose a column that does already have one of your colored squares in it, place the marker onto the space directly above your colored square.\n"
                        +
                        "c) You may place a marker onto a space that's already occupied by an opponent's colored square.\n"
                        +
                        "d) If you can place a marker on your roll, you must.\n\n" +
                        "Blowing It: When your roll will not allow you either to place a marker or to move one up, you've \"blown it\" and must end your turn. Remove all of the markers, but leave all of your colored squares.\n\n"
                        +
                        "Winning a Column: You win a column as soon as you place one of your colored squares onto the number at the top of that column.\n",
                headerStyle, bodyStyle);

        addSection(textTable, "Winning",
                "The winner is the first player to win any three columns.",
                headerStyle, bodyStyle);

        // We don't have a skin for scrollbars, so they might be invisible or default
        // style if we don't provide one.
        // For now let's just use the table directly if it fits, or assume scroll is
        // touch/drag.
        // Actually, without a skin, ScrollPane structure is tricky.
        // Given the low res (560x320), the text is definitely too long.
        // Let's stick to the previous implementation's logic but adapted.

        // Wait, previous implem added everything to 'root' directly. It probably went
        // off screen?
        // Let's use the scroll pane but we need a minimal style.
        // Since we don't have a skin with scrollbars loaded easily without SharedSkin
        // (which I want to avoid),
        // I'll just add the textTable to root and hope the user can scroll or it fits
        // enough.
        // Actually, I can use minimal ScrollPaneStyle with null drawables.

        ScrollPane.ScrollPaneStyle scrollStyle = new ScrollPane.ScrollPaneStyle();
        ScrollPane scroll = new ScrollPane(textTable, scrollStyle);

        root.add(title).padTop(10).padBottom(10).row();
        root.add(scroll).expand().fill().padLeft(20).padRight(20).row();

        TextButton backButton = new TextButton("Back", borderStyle);
        backButton.addListener(new ClickListener() {
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new MenuScreen(game));
            }
        });

        root.add(backButton).size(120, 35).pad(10).bottom().right();

        stage.addActor(root);
    }

    private void addSection(Table table, String header, String body, Label.LabelStyle headerStyle,
            Label.LabelStyle bodyStyle) {
        Label h = new Label(header, headerStyle);
        Label b = new Label(body, bodyStyle);
        b.setWrap(true);

        table.add(h).left().padTop(10).row();
        table.add(b).growX().left().padTop(5).row();
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
