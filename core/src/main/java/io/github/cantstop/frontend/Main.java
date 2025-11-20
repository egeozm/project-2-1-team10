package io.github.cantstop.frontend;


import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.viewport.FitViewport;
import io.github.cantstop.frontend.screens.MenuScreen;
import com.badlogic.gdx.audio.Music;
import io.github.cantstop.frontend.screens.PlayScreen;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class Main extends Game {

    public FitViewport viewport;
    public SpriteBatch batch;
    public BitmapFont font;
    public GameAssets assets;
    public Music mainMenuMusic;

    @Override
    public void create() {

        batch = new SpriteBatch();
        // use libGDX's default font
        font = new BitmapFont();
        viewport = new FitViewport(560, 320);

        //font has 15pt, but we need to scale it to our viewport by ratio of viewport height to screen height
        font.setUseIntegerPositions(false);
        font.getData().setScale(viewport.getWorldHeight() / Gdx.graphics.getHeight());

        this.assets = new GameAssets();
        assets.loadAll();

        //music
        mainMenuMusic = Gdx.audio.newMusic(Gdx.files.internal("music/mainMenuMusic.mp3"));
        mainMenuMusic.setLooping(true);
        mainMenuMusic.play();

        setScreen(new MenuScreen(this));
    }

    public void startGame(boolean vsAI) {
        if(mainMenuMusic.isPlaying()){
            mainMenuMusic.stop();
        }
        setScreen(new PlayScreen(this, vsAI));
    }

    public void startGame() {
        startGame(false);
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

