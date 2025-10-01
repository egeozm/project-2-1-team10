package io.github.cantstop.model;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;

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

    /** Draw the 4 dice for this combination row */
    public void draw(Batch batch, Texture[] faces) {
        for (int i = 0; i < 4; i++) {
            int faceIndex = diceValues[i] - 1; // dice values are 1–6
            float offsetX = startX + i * (size + 10f);

            // Add extra space between first pair and second pair
            if (i >= 2) offsetX += gap;

            batch.draw(faces[faceIndex], offsetX, y, size, size);
        }
    }
}
