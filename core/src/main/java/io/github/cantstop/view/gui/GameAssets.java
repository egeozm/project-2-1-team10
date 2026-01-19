package io.github.cantstop.view.gui;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Disposable;

/** Centralized texture loading via AssetManager. */
public final class GameAssets implements Disposable {

    private final AssetManager am = new AssetManager();

    public Texture logo;
    public Texture menuBackground;
    public Texture settingsBackground;

    public Texture board;
    public Texture blueMarker1, blueMarker2, blueCross;
    public Texture redMarker1, redMarker2, redCross;
    public Texture[] diceTextures = new Texture[6]; // indices 0..5 for faces 1..6

    private Texture getTexture(String path) {
        Texture t = am.get(path, Texture.class);
        t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        return t;
    }

    /** Queue and load everything we need in one go. */
    public void loadAll() {

        // Title
        am.load("cantstop_logo.png", Texture.class);

        // Backgrounds
        am.load("backgrounds/menuBackground.png", Texture.class);
        am.load("backgrounds/settingsBackground.png", Texture.class);

        // Board
        am.load("board.png", Texture.class);

        // Markers
        am.load("markers/blue_marker_1.png", Texture.class);
        am.load("markers/blue_marker_2.png", Texture.class);
        am.load("markers/blue_cross.png", Texture.class);
        am.load("markers/red_marker_1.png", Texture.class);
        am.load("markers/red_marker_2.png", Texture.class);
        am.load("markers/red_cross.png", Texture.class);

        // Dice faces 1..6
        for (int i = 1; i <= 6; i++) {
            am.load("dice/old_dice/d" + i + ".png", Texture.class);
        }

        // Block until loaded
        am.finishLoading();

        logo = getTexture("cantstop_logo.png");
        menuBackground = getTexture("backgrounds/menuBackground.png");
        settingsBackground = getTexture("backgrounds/settingsBackground.png");

        // Resolve handles
        board = getTexture("board.png");
        blueMarker1 = getTexture("markers/blue_marker_1.png");
        blueMarker2 = getTexture("markers/blue_marker_2.png");
        blueCross = getTexture("markers/blue_cross.png");
        redMarker1 = getTexture("markers/red_marker_1.png");
        redMarker2 = getTexture("markers/red_marker_2.png");
        redCross = getTexture("markers/red_cross.png");


        for (int i = 0; i < 6; i++) {
            diceTextures[i] = getTexture("dice/old_dice/d" + (i + 1) + ".png");
        }
    }

    @Override
    public void dispose() {
        am.dispose();
    }

}
