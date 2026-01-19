package io.github.cantstop.view.gui;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;

public class ButtonStyle {

    public static TextButton.TextButtonStyle createBorderButtonStyle(BitmapFont font) {

        Pixmap pm = new Pixmap(16, 16, Pixmap.Format.RGBA8888);
        pm.setColor(GuiConstants.buttonFillColor);
        pm.fill();
        pm.setColor(GuiConstants.textColor);
        pm.drawRectangle(0, 0, 16, 16);
        Texture baseTex = new Texture(pm);

        // brighter version
        pm.setColor(GuiConstants.buttonFillColor);
        pm.fill();
        pm.setColor(GuiConstants.textColor.cpy().mul(1.4f));
        pm.drawRectangle(0, 0, 16, 16);
        Texture hoverTex = new Texture(pm);

        pm.dispose();

        NinePatchDrawable drawableUp = new NinePatchDrawable(new NinePatch(baseTex, 1, 1, 1, 1));
        drawableUp.setTopHeight(22);
        drawableUp.setBottomHeight(5);

        NinePatchDrawable drawableHover = new NinePatchDrawable(new NinePatch(hoverTex, 1, 1, 1, 1));
        drawableHover.setTopHeight(22);
        drawableHover.setBottomHeight(5);

        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle();
        style.up = drawableUp;
        style.down = style.up;
        style.over = drawableHover;

        style.font = font;
        style.overFontColor = GuiConstants.textColor.cpy().mul(1.4f);
        style.fontColor = GuiConstants.textColor;

        return style;
    }

    public static Slider.SliderStyle createSliderStyle() {
        // Track
        Pixmap pm = new Pixmap(8, 8, Pixmap.Format.RGBA8888);
        pm.setColor(GuiConstants.buttonFillColor);
        pm.fill();
        Texture trackTex = new Texture(pm);
        pm.dispose();

        // Knob
        pm = new Pixmap(10, 20, Pixmap.Format.RGBA8888);
        pm.setColor(GuiConstants.textColor);
        pm.fill();
        Texture knobTex = new Texture(pm);
        pm.dispose();

        Slider.SliderStyle style = new Slider.SliderStyle();
        style.background = new NinePatchDrawable(new NinePatch(trackTex, 1, 1, 1, 1));
        style.knob = new TextureRegionDrawable(knobTex);
        return style;
    }

    public static CheckBox.CheckBoxStyle createCheckBoxStyle(BitmapFont font) {
        // Box off
        Pixmap pm = new Pixmap(16, 16, Pixmap.Format.RGBA8888);
        pm.setColor(GuiConstants.buttonFillColor);
        pm.fill();
        pm.setColor(GuiConstants.textColor);
        pm.drawRectangle(0, 0, 16, 16);
        Texture offTex = new Texture(pm);

        // Box on (filled with text color)
        pm.setColor(GuiConstants.textColor);
        pm.fill();
        Texture onTex = new Texture(pm);
        pm.dispose();

        CheckBox.CheckBoxStyle style = new CheckBox.CheckBoxStyle();
        style.checkboxOff = new TextureRegionDrawable(offTex);
        style.checkboxOn = new TextureRegionDrawable(onTex);
        style.font = font;
        style.fontColor = GuiConstants.textColor;
        return style;

    }

    public static com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldStyle createTextFieldStyle(BitmapFont font) {
        Pixmap pm = new Pixmap(16, 16, Pixmap.Format.RGBA8888);
        pm.setColor(GuiConstants.buttonFillColor);
        pm.fill();
        pm.setColor(GuiConstants.textColor);
        pm.drawRectangle(0, 0, 16, 16);
        Texture bg = new Texture(pm);

        // cursor
        pm.setColor(GuiConstants.textColor);
        pm.fill();
        Texture cursor = new Texture(pm);
        pm.dispose();

        com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldStyle style = new com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldStyle();
        style.background = new NinePatchDrawable(new NinePatch(bg, 1, 1, 1, 1));
        style.font = font;
        style.fontColor = GuiConstants.textColor;
        style.cursor = new NinePatchDrawable(new NinePatch(cursor, 0, 0, 0, 0));
        // cursor needs to be thin, but for pixel art maybe a block is okay?
        // 16x16 cursor is too wide.
        // Let's make a 2x16 cursor.

        return style;
    }

}
