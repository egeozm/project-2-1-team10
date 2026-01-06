package io.github.cantstop.view.gui;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

public class Instructions {

    private final String instructions = """
            How to Play

            Roll the dice: Press SPACE.

            Choose sums: Select how to pair the 4 dice using mouse or arrow keys + ENTER.

            Advance markers: Your temporary markers move up the chosen columns (up to 3 columns per turn).

            Keep going or stop:

                Roll again (SPACE) to try and make more progress.

                End turn (ENTER) to save your markers as permanent.

            Bust: If your roll can't advance any marker, you lose all progress this turn.

            Goal: Be the first to claim 3 columns by reaching the top.""";

    private Texture background;

    public Instructions(Texture background) {
        this.background = background;
    }

    public void draw(SpriteBatch batch, BitmapFont font) {

        // TO DO: draw a rectangle behind the text
        font.draw(batch, instructions, 50, 300);
    }
}


