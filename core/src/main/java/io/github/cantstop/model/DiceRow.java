package io.github.cantstop.model;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import java.util.Random;

public class DiceRow {
    int[] diceValues;
    float startX, y, size, gap;
    int pairingIndex;

    public DiceRow(int[] diceValues, float startX, float y, float size, float gap, int pairingIndex) {
        this.diceValues = diceValues;
        this.startX = startX;
        this.y = y;
        this.size = size;
        this.gap = gap;
        this.pairingIndex = pairingIndex;
    }

    public void drawRandom(Batch batch, Texture[] faces, Random rng) {
        for (int i = 0; i < 4; i++) {
            int face = rng.nextInt(6);
            float offsetX = startX + i * (size + 10f);
            if (i >= 2) offsetX += gap; // add extra space between the pairs
            batch.draw(faces[face], offsetX, y, size, size);
        }
    }

    public void draw(Batch batch, Texture[] faces) {
        for (int i = 0; i < 4; i++) {
            int faceIndex = diceValues[i] - 1;
            float offsetX = startX + i * (size + 10f);
            if (i >= 2) offsetX += gap; // add extra space between the pairs
            batch.draw(faces[faceIndex], offsetX, y, size, size);
        }
    }
}
