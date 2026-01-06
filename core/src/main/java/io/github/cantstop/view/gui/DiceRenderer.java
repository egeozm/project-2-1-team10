package io.github.cantstop.view.gui;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.cantstop.model.GameState;

import java.util.Random;

public class DiceRenderer {

    private final GameState gameState;
    private final SpriteBatch batch;
    private final GameAssets assets;

    private final Random random;

    private boolean isRolling = false;
    private float remainingRollTime = 0f;


    // Layout constants
    private static final float ORIGIN_X = 300f;
    private static final float ORIGIN_Y = 220f;
    private static final float DIE_SIZE = 48f;
    private static final float GAP_WIDTH = 12f;

    public DiceRenderer(GameState gameState, SpriteBatch batch, GameAssets assets) {
        this.gameState = gameState;
        this.batch = batch;
        this.assets = assets;
        this.random = new Random();
    }

    public void draw() {
        for (int i = 0; i < 4; i++) {

            int faceIndex;

            if (isRolling) {
                faceIndex = random.nextInt(6);
            } else {
                if (gameState.getLastRoll() == null) { return; }
                faceIndex = gameState.getLastRoll().toArray()[i] - 1; // matches dice values to index in the texture array
            }

            float x = ORIGIN_X + (i % 2) * (DIE_SIZE + GAP_WIDTH);
            float y = ORIGIN_Y - (i / 2) * (DIE_SIZE + GAP_WIDTH);

            batch.draw(assets.diceTextures[faceIndex], x, y, DIE_SIZE, DIE_SIZE);
        }
    }

    public void rollAnimation(float durationSeconds) {
        isRolling = true;
        this.remainingRollTime = durationSeconds;
    }

    public void update(float delta) {
        if (remainingRollTime > 0) {
            remainingRollTime -= delta;
            if (remainingRollTime <= 0) {
                isRolling = false;
            }
        }
    }

    public boolean isRolling() {
        return isRolling;
    }
}
