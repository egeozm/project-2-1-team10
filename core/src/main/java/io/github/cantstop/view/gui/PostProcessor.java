package io.github.cantstop.view.gui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.viewport.FitViewport;

public class PostProcessor {

    private final FrameBuffer frameBuffer;
    private final ShaderProgram shader;
    private final SpriteBatch batch;

    public PostProcessor(SpriteBatch sharedBatch, FitViewport viewport) {
        this.batch = sharedBatch;

        frameBuffer = new FrameBuffer(
                Pixmap.Format.RGBA8888,
                (int) viewport.getWorldWidth(),
                (int) viewport.getWorldHeight(),
                false);

        frameBuffer.getColorBufferTexture().setFilter(
                Texture.TextureFilter.Nearest,
                Texture.TextureFilter.Nearest);

        ShaderProgram.pedantic = false;
        shader = new ShaderProgram(
                Gdx.files.internal("shaders/vertex.glsl"),
                Gdx.files.internal("shaders/fragment.glsl"));

        if (!shader.isCompiled()) {
            throw new IllegalStateException(shader.getLog());
        }

    }

    public void begin() {
        frameBuffer.begin();
        Gdx.gl.glClearColor(0.1f, 0.1f, 0.15f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
    }

    public void end(float time, FitViewport viewport) {

        shader.bind();
        shader.setUniformf(
                "u_resolution",
                viewport.getWorldWidth() * 8,
                viewport.getWorldHeight() * 8);
        shader.setUniformf("u_time", time);

        frameBuffer.end();

        batch.setShader(shader);
        batch.begin();
        batch.draw(
                frameBuffer.getColorBufferTexture(),
                0, 0,
                viewport.getWorldWidth(),
                viewport.getWorldHeight(),
                0, 0, 1, 1);
        batch.end();
        batch.setShader(null);
    }

    public void dispose() {
        frameBuffer.dispose();
        shader.dispose();
    }
}
