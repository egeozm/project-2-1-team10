package io.github.cantstop.view.gui;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.Color;

public class PopupRenderer {

    private final SpriteBatch batch;
    private final BitmapFont font;

    // popup state
    private String message = null;
    private float remainingTime = 0f;

    // position
    private final float X = 190;
    private final float Y = 130;

    public PopupRenderer(SpriteBatch batch, BitmapFont font) {
        this.batch = batch;
        this.font = font;
    }

    public void showPopup(String text, float durationSeconds) {
        this.message = text;
        this.remainingTime = durationSeconds;
    }

    public void update(float delta) {
        if (remainingTime > 0) {
            remainingTime -= delta;
            if (remainingTime <= 0) {
                message = null; // auto hide
            }
        }
    }

    public void draw() {
        if (message == null) return;

        font.setColor(GuiConstants.textColor);
        font.getData().setScale(6f);

        // Split the message into lines
        String[] lines = message.split("\n");

        float totalHeight = lines.length * font.getLineHeight();
        float y = Y + totalHeight / 2f; // start from top line

        for (String line : lines) {
            GlyphLayout layout = new GlyphLayout(font, line);
            float x = X - layout.width / 2f; // center each line individually
            font.draw(batch, layout, x, y);
            y -= font.getLineHeight(); // move down for next line
        }
    }

    public boolean isActive() {
        return message != null;
    }
}

