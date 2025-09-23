package io.github.cantStop.screens;

import com.badlogic.gdx.graphics.Texture;
import java.util.Random;



public class Die {

    private final Texture[] faceTextures;
    private int value;
    private final Random random;

    public Die(Texture[] textures) {
        faceTextures = textures;
        value = 1;
        random = new Random();
    }

    public void roll() {
        value = random.nextInt(6) + 1; // generates a random value between 1 and 6
    }

    public int getValue() {
        return value;
    }

    public Texture currentFace() {
        return faceTextures[value - 1];
    }
}
