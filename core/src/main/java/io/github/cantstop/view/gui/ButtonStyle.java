package io.github.cantstop.view.gui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;

public class ButtonStyle {

    public static TextButton.TextButtonStyle createBorderButtonStyle(BitmapFont font) {
        Pixmap pm = new Pixmap(16, 16, Pixmap.Format.RGBA8888);
        pm.setColor(GuiConstants.buttonFillColor); pm.fill();
        pm.setColor(GuiConstants.textColor); pm.drawRectangle(0,0,16,16);

        NinePatch patch = new NinePatch(new Texture(pm),1,1,1,1);
        pm.dispose();

        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle();
        style.up = new NinePatchDrawable(patch);
        style.down = new NinePatchDrawable(patch);
        style.font = font;
        style.fontColor = GuiConstants.textColor;
        return style;
    }
}
