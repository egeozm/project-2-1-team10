package io.github.cantstop.view.gui.screens;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import io.github.cantstop.view.gui.ButtonStyle;
import io.github.cantstop.view.gui.GuiConstants;
import io.github.cantstop.view.gui.Main;

public class SettingsScreen extends BaseScreen {

    private Table content;
    private CheckBox.CheckBoxStyle checkBoxStyle;
    private Slider.SliderStyle sliderStyle;

    public SettingsScreen(Main game) {
        super(game);
    }

    @Override
    public void show() {
        super.show();

        checkBoxStyle = ButtonStyle.createCheckBoxStyle(font);
        sliderStyle = ButtonStyle.createSliderStyle();

        // Main Layout
        Table root = new Table();
        root.setFillParent(true);
        stage.addActor(root);

        // Header Title
        Label title = new Label("Settings", new Label.LabelStyle(buttonFont, GuiConstants.textColor));
        title.setFontScale(1.5f);
        root.add(title).padTop(20).padBottom(20).colspan(3).row();

        // Navigation Buttons (General, Audio, Visuals)
        Table navTable = new Table();
        TextButton generalButton = new TextButton("General", borderStyle);
        TextButton audioButton = new TextButton("Audio", borderStyle);
        TextButton visualsButton = new TextButton("Visuals", borderStyle);

        navTable.add(generalButton).size(100, 30).pad(5);
        navTable.add(audioButton).size(100, 30).pad(5);
        navTable.add(visualsButton).size(100, 30).pad(5);

        root.add(navTable).padBottom(20).colspan(3).row();

        // Content Area
        content = new Table();
        root.add(content).grow().colspan(3).row();

        // Footer Buttons (Save, Back)
        Table footerTable = new Table();
        TextButton saveButton = new TextButton("Save", borderStyle); // "Save settings" was too long for button?
        TextButton backButton = new TextButton("Back", borderStyle);

        saveButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                // Save logic here (placeholder)
            }
        });

        backButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new MenuScreen(game));
            }
        });

        footerTable.add(saveButton).size(100, 30).pad(10);
        footerTable.add(backButton).size(100, 30).pad(10);

        root.add(footerTable).bottom().padBottom(10).colspan(3);

        // Listeners for Nav
        generalButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                showGeneral();
            }
        });

        audioButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                showAudio();
            }
        });

        visualsButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                showVisuals();
            }
        });

        // Default view
        showGeneral();
    }

    private void showGeneral() {
        content.clear();
        Label.LabelStyle labelStyle = new Label.LabelStyle(buttonFont, GuiConstants.textColor);

        content.add(new Label("Language", labelStyle)).left().padBottom(10).row();
        content.add(new Label("(English only for now)", new Label.LabelStyle(font, GuiConstants.textColor))).left()
                .padBottom(20).row();

        content.add(new Label("Reset defaults", labelStyle)).left().padBottom(5);
        CheckBox resetCheck = new CheckBox("", checkBoxStyle);
        content.add(resetCheck).left().padLeft(10).row();
    }

    private void showAudio() {
        content.clear();

        Label.LabelStyle headerStyle = new Label.LabelStyle(buttonFont, GuiConstants.textColor);

        content.add(new Label("Music Volume", headerStyle)).left().padBottom(5).row();
        Slider musicSlider = new Slider(0, 100, 1, false, sliderStyle);
        musicSlider.setValue(50);
        content.add(musicSlider).width(200).left().padBottom(20).row();

        content.add(new Label("SFX Volume", headerStyle)).left().padBottom(5).row();
        Slider sfxSlider = new Slider(0, 100, 1, false, sliderStyle);
        sfxSlider.setValue(50);
        content.add(sfxSlider).width(200).left().padBottom(20).row();

        content.add(new Label("Mute All", headerStyle)).left();
        CheckBox muteCheck = new CheckBox("", checkBoxStyle);
        content.add(muteCheck).left().padLeft(10).row();
    }

    private void showVisuals() {
        content.clear();
        Label.LabelStyle headerStyle = new Label.LabelStyle(buttonFont, GuiConstants.textColor);
        content.add(new Label("Visual Settings", headerStyle)).left().padBottom(20).row();
        content.add(new Label("Coming soon...", new Label.LabelStyle(font, GuiConstants.textColor))).left();
    }

    @Override
    protected void renderScreenContent(float delta) {
        if (game.assets.settingsBackground != null) {
            float stageWidth = game.viewport.getWorldWidth();
            float stageHeight = game.viewport.getWorldHeight();
            game.batch.draw(game.assets.settingsBackground,
                    0, 0,
                    stageWidth, stageHeight);
        }
    }
}
