package io.github.cantstop;


import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.viewport.FitViewport;
import io.github.cantstop.screens.MenuScreen;
import com.badlogic.gdx.audio.Music;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class Main extends Game {

    public FitViewport viewport;
    public SpriteBatch batch;
    public BitmapFont font;
    public Music mainMenuMusic;

    @Override
    public void create() {

        batch = new SpriteBatch();
        // use libGDX's default font
        font = new BitmapFont();
        viewport = new FitViewport(1920, 1080);

        //font has 15pt, but we need to scale it to our viewport by ratio of viewport height to screen height
        font.setUseIntegerPositions(false);
        font.getData().setScale(2 * viewport.getWorldHeight() / Gdx.graphics.getHeight());

        //music
        mainMenuMusic = Gdx.audio.newMusic(Gdx.files.internal("music/mainMenuMusic.mp3"));
        mainMenuMusic.setLooping(true);
        mainMenuMusic.play();

        setScreen(new MenuScreen(this));
    }

    public void startGame() {
        if(mainMenuMusic.isPlaying()){
            mainMenuMusic.stop();
        }
        setScreen(new io.github.cantstop.screens.PlayScreen(this));
    }

    @Override
    public void render() {
        super.render(); // important!
    }

    @Override
    public void resize(int width, int height) {
        // Keep viewport in sync with window size
        viewport.update(width, height, true);

        // Also notify current screen if it needs to adjust anything
        if (getScreen() != null) {
            getScreen().resize(width, height);
        }
    }

    @Override
    public void dispose() {
        batch.dispose();
        font.dispose();
        screen.dispose();
        if(mainMenuMusic != null)
            mainMenuMusic.dispose();
    }
}

