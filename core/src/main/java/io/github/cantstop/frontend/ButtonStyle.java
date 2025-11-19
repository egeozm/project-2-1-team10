package io.github.cantstop.frontend;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;

public class ButtonStyle {

    public static TextButton.TextButtonStyle createBorderButtonStyle() {
        Pixmap pm = new Pixmap(16, 16, Pixmap.Format.RGBA8888);
        pm.setColor(0,0,0,0); pm.fill();
        pm.setColor(Color.WHITE); pm.drawRectangle(0,0,16,16);

        NinePatch patch = new NinePatch(new Texture(pm),1,1,1,1);
        pm.dispose();

        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle();
        style.up = new NinePatchDrawable(patch);
        style.down = new NinePatchDrawable(patch);
        style.font = new BitmapFont();
        style.fontColor = Color.WHITE;
        return style;
    }
}
