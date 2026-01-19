package io.github.cantstop.view.gui.screens;

import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.cantstop.view.gui.ButtonStyle;
import io.github.cantstop.view.gui.Main;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.graphics.Texture;
import io.github.cantstop.view.gui.SharedSkin;

public class RulesScreen implements Screen {
    private Main game;
    private Stage stage;
    private Texture background;
    private BitmapFont menuFont;
    private TextButton.TextButtonStyle borderStyle;


    public RulesScreen(Main game) {
        this.game = game;
        stage = new Stage(new ScreenViewport());
    }

    public void show() {
        Gdx.input.setInputProcessor(stage);
        Skin skin = SharedSkin.getSkin();
        background = new Texture(Gdx.files.internal("backgrounds/settingsBackground.png"));

        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.internal("fonts/pixel_font.ttf"));
        FreeTypeFontGenerator.FreeTypeFontParameter params = new FreeTypeFontGenerator.FreeTypeFontParameter();
        params.size = 18;
        params.mono = true;
        params.minFilter = Texture.TextureFilter.Nearest;
        params.magFilter = Texture.TextureFilter.Nearest;

        menuFont = generator.generateFont(params);
        generator.dispose();

        borderStyle = ButtonStyle.createBorderButtonStyle(menuFont);
        borderStyle.fontColor = com.badlogic.gdx.graphics.Color.WHITE;
        borderStyle.overFontColor = com.badlogic.gdx.graphics.Color.WHITE;
        borderStyle.downFontColor = com.badlogic.gdx.graphics.Color.WHITE;

        Label title = new Label("Rules", skin, "big");
        title.setFontScale(0.8f);

        Table root = new Table();
        root.setFillParent(true);
        stage.addActor(root);

        root.top();
        root.add(title).expandX().center().padTop(30);
        root.row();

        Label label1 = new Label("Goal", SharedSkin.getSkin(), "big");
        root.add(label1).expand().top().left().padTop(5).padLeft(40);
        root.row();

        Label label11 = new Label("To be the first player to reach the top of any three columns.", SharedSkin.getSkin(), "default");
        root.add(label11).expand().top().left().padTop(5).padLeft(40);
        root.row();

        Label label2 = new Label("Playing", SharedSkin.getSkin(), "big");
        root.add(label2).expand().top().left().padTop(5).padLeft(40);
        root.row();

        Label label21 = new Label("To start your turn, roll all four dice. Look your roll over carefully. Then split your roll in half in any way you wish, and add the two dice in each half. The purpose: to create a pair of numbers.\n" +
            "Example: Let’s say you roll a 1-5-4-6. With this roll, you can create any of the following pairs: 6 and 10 (1+5 and 4+6); or 5 and 11 (1+4 and 5+6); or 9 and 7 (5+4 and 1+6).\n" +
            "\n" +
            "The pair of numbers you choose to create represents the two columns into which you must now place markers.\n" +
            "Example: On this roll of 1-5-4-6, let’s say you choose 6 and 10 as your pair. You must now place a marker into the \"6\" column and another marker into the \"10\" column. When first placing a marker into a particular\n" + "column, always place it onto the space at the bottom of that column.\n" +
            "\n" +
            "In this game you may roll more than once on a single turn. On each additional roll, you also create a pair of numbers in the same way.\n" +
            "a) Let’s say you roll again and create a pair that includes a number you’ve already chosen. When this happens, move the marker up one space in that number’s column.\n" +
            "b) Let’s say you roll again and decide to create a pair with a new number. If you have another marker left, you must place it into the new marker’s column.\n" +
            "You may continue to roll as long as your last roll allowed you either to place a marker or to move one up. If you prefer, you may stop your turn whenever you wish.\n" + "To stop, simply replace each marker with one of your colored squares.", SharedSkin.getSkin(), "default");
        root.add(label21).expand().top().left().padTop(5).padLeft(40);
        root.row();

        Label label3 = new Label("Placing a Marker", SharedSkin.getSkin(), "big");
        root.add(label3).expand().top().left().padTop(5).padLeft(40);
        root.row();

        Label label33 = new Label("a) If you choose a column that does not already have one of your colored squares in it, place the marker onto the space at the bottom of that column.\n" +
            "b) If you choose a column that does already have one of your colored squares in it, place the marker onto the space directly above your colored square.\n" +
            "c) You may place a marker onto a space that’s already occupied by an opponent’s colored square.\n" +
            "d) If you can place a marker on your roll, you must.\n" +
            "Example: Let’s say you’ve already placed markers into the \"3\" and \"6\" columns and you roll a 2-4-5-5. If you want to move up the marker in column \"6\",\n" +" you must place the third marker into column \"10\". Otherwise you must place the third marker either into column \"7\" or \"9\".\n" +
            "\n" +
            "Blowing It. When your roll will not allow you either to place a marker or to move one up, you’ve \"blown it\" and must end your turn. Remove all of the markers \n" + "that you’ve placed, but leave all of your colored squares that are already on the board.\n" +
            "\n" +
            "Remember: As soon as you’ve placed all three markers on your turn, each additional roll on that turn must allow you to move up at least one of the markers.\n" +" Otherwise you’ve \"blown it\" and your turn ends.\n" +
            "\n" +
            "Winning a Column. You win a column as soon as you place one of your colored squares onto the number at the top of that column. If any of your opponents \n" +"already has a colored square in a column that you win, he or she must remove that square immediately.\n" +
            "\n" +
            "a) A marker on the number at the top of a column does not mean you’ve won that column.\n" +
            "Example: Let’s say you’ve placed markers into columns \"3\", \"6\" and \"8\", and that you’ve just moved the marker in column \"6\" to the number at the top.\n" +" You could stop your turn now and win that column by replacing each of the markers with one of your colored squares. You decide, however, to roll again -- hoping to win columns \"3\" and \"8\", too.\n" +" You roll a 2-4-5-5. Tough luck. You have no more markers to place, and you can’t move the marker in column \"6\" any higher than it already is. So you’ve \"blown it\" \n" +" and must end your turn by removing all of the markers that you’ve placed. In other words, you do not win column \"6\".\n" +
            "\n" +
            "b) You may not place a marker into a column that someone has already won--even if you’ve won that column yourself.\n" +
            "Example: Let’s say that columns \"6\", \"8\" and \"10\" are already won, and you roll a 2-4-4-6. You’ve \"blown it\"-- even if you still have markers to place.", SharedSkin.getSkin(), "default");
        root.add(label33).expand().top().left().padTop(5).padLeft(40);
        root.row();

        Label label4 = new Label("Winning", SharedSkin.getSkin(), "big");
        root.add(label4).expand().top().left().padTop(5).padLeft(40);
        root.row();

        Label label44 = new Label("The winner is the first player to win any three columns.", SharedSkin.getSkin(), "default");
        root.add(label44).expand().top().left().padTop(5).padLeft(40);
        root.row();


        TextButton backButton = new TextButton("Back", borderStyle);

        Table table = new Table();
        table.setFillParent(true);
        table.bottom().right();

        table.add(backButton).size(200, 60).pad(10);

        stage.addActor(table);

        backButton.addListener(new ClickListener() {
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new MenuScreen(game));
            }
        });
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        game.batch.setShader(null);

        float screenWidth = Gdx.graphics.getWidth();
        float screenHeight = Gdx.graphics.getHeight();

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
        if (width <= 0 || height <= 0) return;
        stage.getViewport().update(width, height, true);
    }

    @Override public void pause() {}

    @Override public void resume() {}

    @Override public void hide() {}

    @Override public void dispose() {
        stage.dispose();
        background.dispose();
    }
}

