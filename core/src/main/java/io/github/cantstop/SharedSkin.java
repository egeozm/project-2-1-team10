package io.github.cantstop;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;

public class SharedSkin {
    private static Skin skin;

    public static Skin getSkin() {
        if (skin == null) {
            TextureAtlas atlas = new TextureAtlas(Gdx.files.internal("skin/glassy-ui.atlas"));
            skin = new Skin(Gdx.files.internal("skin/glassy-ui.json"), atlas);

            //make the text smaller to fit it into buttons

            TextButton.TextButtonStyle style = skin.get(TextButton.TextButtonStyle.class);
            style.font.getData().setScale(0.5f);
        }
        return skin;
    }
}
