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
    private final float X = 280;
    private final float Y = 180;

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

        font.setColor(Color.WHITE);
        font.getData().setScale(4f);

        GlyphLayout layout = new GlyphLayout();
        layout.setText(font, message);

        float x = X - layout.width / 2;

        font.draw(batch, layout, x, Y);
    }

    public boolean isActive() {
        return message != null;
    }
}

