package io.github.cantstop.frontend;

import com.badlogic.gdx.graphics.Texture;
import java.util.Random;



public class Die {

    private Texture[] faceTextures;
    public Texture currentFace;
    public int value;
    private Random random;

    public Die(Texture[] textures) {
        faceTextures = textures;
        value = 1;
        currentFace = faceTextures[0];
        random = new Random();
    }

    public void roll() {
        value = random.nextInt(6) + 1; // generates a random value between 1 and 6
        currentFace = faceTextures[value - 1];
    }
}
