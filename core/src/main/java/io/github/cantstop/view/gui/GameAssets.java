package io.github.cantstop.view.gui;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Disposable;

/** Centralized texture loading via AssetManager. */
public final class GameAssets implements Disposable {
    private final AssetManager am = new AssetManager();

    // Expose what PlayScreen needs
    public Texture board;
    public Texture blueMarker1, blueMarker2, blueCross;
    public Texture redMarker1,  redMarker2,  redCross;
    public Texture[] diceTextures; // indices 0..5 for faces 1..6

    private Texture getTexture(String path) {
        Texture t = am.get(path, Texture.class);
        t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        return t;
    }

    /** Queue and load everything we need for PlayScreen in one go. */
    public void loadAll() {
        // Board
        am.load("board.png", Texture.class);

        // Markers
        am.load("markers/blue_marker_1.png", Texture.class);
        am.load("markers/blue_marker_2.png", Texture.class);
        am.load("markers/blue_cross.png", Texture.class);
        am.load("markers/red_marker_1.png",  Texture.class);
        am.load("markers/red_marker_2.png",  Texture.class);
        am.load("markers/red_cross.png", Texture.class);

        // Dice faces 1..6
        for (int i = 1; i <= 6; i++) {
            am.load("dice/new_dice/d" + i + ".png", Texture.class);
        }

        // Block until loaded. If you want async, swap to am.update() loop later.
        am.finishLoading();

        // Resolve handles
        board       = getTexture("board.png");
        blueMarker1 = getTexture("markers/blue_marker_1.png");
        blueMarker2 = getTexture("markers/blue_marker_2.png");
        blueCross   = getTexture("markers/blue_cross.png");
        redMarker1  = getTexture("markers/red_marker_1.png");
        redMarker2  = getTexture("markers/red_marker_2.png");
        redCross    = getTexture("markers/red_cross.png");

        diceTextures = new Texture[6];
        for (int i = 0; i < 6; i++) {
            diceTextures[i] = getTexture("dice/new_dice/d" + (i + 1) + ".png");
        }
    }

    @Override public void dispose() {
        am.dispose(); // disposes all loaded textures safely
    }

}
