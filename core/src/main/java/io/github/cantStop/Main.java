package io.github.cantstop;

import com.badlogic.gdx.Game;
import io.github.cantstop.screens.MenuScreen;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class Main extends Game {
    @Override
    public void create() {
        setScreen(new MenuScreen());
    }
}
