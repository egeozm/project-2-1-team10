package io.github.cantstop.view.gui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.cantstop.model.GameConstants;
import io.github.cantstop.model.GameState;
import io.github.cantstop.model.Player;

public class BoardRenderer {

    private final GameState gameState;
    private final SpriteBatch batch;
    private final BitmapFont font;
    private final GameAssets assets;

    // Layout constants
    private static final float ORIGIN_X = 60f;
    private static final float ORIGIN_Y = 60f;
    private static final float CELL_W = 24f;
    private static final float CELL_H = 18f;
    private static final float MARKER_SIZE = 16f;

    public BoardRenderer(GameState gameState, SpriteBatch batch, BitmapFont font, GameAssets assets) {
        this.gameState = gameState;
        this.batch = batch;
        this.font = font;
        this.assets = assets;
    }

    // Draw board texture
    public void drawBoard() {
        batch.draw(assets.board, ORIGIN_X, ORIGIN_Y);
    }

    // Draw permanent markers and temp runners (and cross out claimed columns)
    public void drawMarkersEtc() {

        for (int col = 0; col < GameConstants.NUM_COLS; col++) {
            int sum = GameConstants.columnToSum(col);
            int maxHeight = GameConstants.maxHeight(sum);

            // marker heights
            int redH = gameState.getMarkerHeight(Player.RED, col);
            int blueH = gameState.getMarkerHeight(Player.BLUE, col);
            int tempH = gameState.tempAtCol(col);

            // cross out column if it has been claimed by a player
            if (gameState.getMarkerHeight(Player.RED, col) == maxHeight) { // if claimed by RED
                for (int i = 0; i < maxHeight; i++) {
                    batch.draw(assets.redCross, colX(col), rowY(i));
                }
            } else if (gameState.getMarkerHeight(Player.BLUE, col) == maxHeight) { // if claimed by BLUE
                for (int i = 0; i < maxHeight; i++) {
                    batch.draw(assets.blueCross, colX(col), rowY(i));
                }
            } else { // if unclaimed, draw markers

                float xOffset = 0;
                if (redH == blueH) xOffset = 2; // offset only if they overlap

                // permanent red markers
                if (redH > 0) {
                    batch.draw(assets.redMarker1, colX(col) - xOffset, rowY(redH - 1), MARKER_SIZE, MARKER_SIZE);
                }

                // permanent blue markers
                if (blueH > 0) {
                    batch.draw(assets.blueMarker1, colX(col) + xOffset, rowY(blueH - 1), MARKER_SIZE, MARKER_SIZE);
                }

                // temporary runners
                if (tempH > 0) {
                    Player current = gameState.getCurrentPlayer();
                    Texture tempMarker = (current == Player.RED) ? assets.redMarker2 : assets.blueMarker2;
                    batch.draw(tempMarker, colX(col), rowY(tempH - 1), MARKER_SIZE, MARKER_SIZE);
                }
            }
        }
    }

    // Draw column numbers
    public void drawColumnNumbers() {

        font.setColor(GuiConstants.textColor);
        font.getData().setScale(1f);
        GlyphLayout layout = new GlyphLayout();

        for (int col = 0; col < GameConstants.NUM_COLS; col++) {

            int sum = GameConstants.columnToSum(col);
            String text = String.valueOf(sum);
            layout.setText(font, text);

            float x = colX(col) + CELL_W / 2 - layout.width / 2 - 4;
            float y = ORIGIN_Y - 14;

            font.draw(batch, layout, x, y);
        }
    }

    // Show whose turn it is
    public void drawCurrentPlayer() {
        font.setColor(GuiConstants.textColor);
        font.getData().setScale(1f);
        font.draw(batch, "Current Player:", 10, 260);

        font.getData().setScale(3f);
        if (gameState.getCurrentPlayer() == Player.RED) {
            font.setColor(GuiConstants.red);
            font.draw(batch, "RED", 10, 224);
        } else {
            font.setColor(GuiConstants.blue);
            font.draw(batch, "BLUE", 10, 224);
        }
    }

    // return x-coordinate for a given column
    private static float colX(int col) {
        return ORIGIN_X + col * CELL_W + 4f;
    }

    // return y-coordinate for a given row
    private static float rowY(int step) {
        return ORIGIN_Y + step * CELL_H + 1f;
    }
}
