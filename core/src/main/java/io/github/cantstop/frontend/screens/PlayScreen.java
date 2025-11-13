package io.github.cantstop.frontend.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.Timer;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

import io.github.cantstop.frontend.GameAssets;
import io.github.cantstop.frontend.Main;
import io.github.cantstop.frontend.SharedSkin;
import io.github.cantstop.backend.*;

import java.util.*;

public class PlayScreen implements Screen {

    private final Main game;

    // Layout constants
    private static final float ORIGIN_X = 60f;
    private static final float ORIGIN_Y = 60f;
    private static final float CELL_W = 24f;
    private static final float CELL_H = 18f;
    private static final float MARKER_SIZE = 16f;

    // Backend game model
    private GameState gameState;
    private final Random rng = new Random();

    // Assets
    private GameAssets assets;
    private Texture board;
    private Texture[] diceFaces;
    private Texture redMarker1, redMarker2, redCross;
    private Texture blueMarker1, blueMarker2, blueCross;

    // UI
    private Stage stage;
    private Skin skin;
    private TextButton rollButton;
    private TextButton stopButton;
    private TextButton menuButton;
    private final List<TextButton> moveButtons = new ArrayList<>();
    private Label statusLabel;
    private Label combinationsLabel;
    private BitmapFont font;
    private ShapeRenderer shapeRenderer;

    // Dice animation + result
    private boolean rolling = false;
    private DiceRoll currentRoll = null;
    private int[] rolledDice = null;
    private List<Move> legalMoves = null;
    private List<Move> illegalMoves = new ArrayList<>();

    private boolean gameOver = false;

    public PlayScreen(Main game) {
        this.game = game;

        skin = SharedSkin.getSkin();
        font = new BitmapFont();
        font.getData().setScale(0.8f);
        shapeRenderer = new ShapeRenderer();


        rollButton = new TextButton("Roll", skin);
        rollButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (!gameOver && gameState.getTurnPhase() == TurnPhase.ROLL_OR_STOP) {
                    startDiceAnimation();
                }
            }
        });

        stopButton = new TextButton("Stop", skin);
        stopButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (!gameOver && gameState.getTurnPhase() == TurnPhase.ROLL_OR_STOP
                    && gameState.countActiveColumns() > 0) {
                    handleStop();
                }
            }
        });

        menuButton = new TextButton("Menu", skin);
        menuButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new MenuScreen(game));
            }
        });



    }

    @Override
    public void show() {
        assets = new GameAssets();
        assets.loadAll();

        stage = new Stage(game.viewport);
        Gdx.input.setInputProcessor(stage);

        board = assets.board;
        diceFaces = assets.diceTextures;

        redMarker1 = assets.redMarker1;
        redMarker2 = assets.redMarker2;
        redCross = assets.redCross;

        blueMarker1 = assets.blueMarker1;
        blueMarker2 = assets.blueMarker2;
        blueCross = assets.blueCross;

        Table statusTable = new Table();
        statusTable.setFillParent(true);
        statusTable.bottom().left();
        statusTable.pad(5);

        statusLabel = new Label("", skin);
        statusLabel.setFontScale(0.7f);
        statusTable.add(statusLabel);

        Table buttonTable = new Table();
        buttonTable.setFillParent(true);
        buttonTable.bottom().right();
        buttonTable.pad(5);

        Table menuTable = new Table();
        menuTable.setFillParent(true);
        menuTable.top().left();
        menuTable.pad(5);

        buttonTable.add(rollButton).size(100, 50).pad(8);
        buttonTable.add(stopButton).size(100, 50).pad(8);
        menuTable.add(menuButton).size(100, 50).pad(8);

        stage.addActor(statusTable);
        stage.addActor(buttonTable);
        stage.addActor(menuTable);


        gameState = GameState.initialize(Player.RED);
        updateStatusLabels();
    }

    // Build a canonical key for a move: order doesn't matter (5+8 == 8+5).
    private static String moveKey(Move m) {
        int a = m.sumA(), b = m.sumB();
        if (a == 0 || b == 0) return Integer.toString(a + b); // single move
        int x = Math.min(a, b), y = Math.max(a, b);
        return x + "+" + y;
    }

    // Remove duplicates while preserving the first occurrence and order.
    private static List<Move> dedupeMoves(List<Move> moves) {
        if (moves == null || moves.isEmpty()) return moves;
        Map<String, Move> unique = new LinkedHashMap<>();
        for (Move m : moves) unique.putIfAbsent(moveKey(m), m);
        return new ArrayList<>(unique.values());
    }

    private String formatMoveLabel(Move m) {
        int a = m.sumA(), b = m.sumB();
        if (a == 0 || b == 0) return String.valueOf(a + b); // single
        return a + " + " + b;                               // both
    }


    private void startDiceAnimation() {
        if (rolling || gameOver) return;

        clearMoveButtons();

        if (combinationsLabel != null) {
            combinationsLabel.setVisible(false);
        }

        rolling = true;
        updateStatusLabels();

        currentRoll = DiceRoll.roll(rng);
        rolledDice = null;

        // Stop animation after 1s and show the real dice
        Timer.schedule(new Timer.Task() {
            @Override
            public void run() {

                rolling = false;
                rolledDice = currentRoll.dice();
                handleRollResult();

                if (legalMoves != null && !legalMoves.isEmpty() && combinationsLabel != null) {
                    combinationsLabel.setText("Possible Combinations:");
                    combinationsLabel.setColor(Color.WHITE);
                    combinationsLabel.setVisible(true);
                } else if (legalMoves != null && legalMoves.isEmpty() && combinationsLabel != null) {
                    combinationsLabel.setText("You BUSTEEEEEDDD!");
                    combinationsLabel.setColor(Color.RED);
                    combinationsLabel.setVisible(true);
                }
            }
        }, 1.0f);
    }

    private void handleRollResult() {
        legalMoves = TurnManager.getLegalMoves(gameState, currentRoll);
        illegalMoves = TurnManager.getIllegalMoves(gameState, currentRoll);
        // Kill duplicate sum-pairs before rendering
        legalMoves  = dedupeMoves(legalMoves);
        illegalMoves = dedupeMoves(illegalMoves);

        if (legalMoves.isEmpty()) {
            // bust
            Player bustedPlayer = gameState.getCurrentPlayer(); // save game state of player before busting
            TurnManager.bust(gameState);
            statusLabel.setText(bustedPlayer + " BUSTED!");

            showMoveOptions();

            // 4 seocnds break before switching to next player
            Timer.schedule(new Timer.Task() {
                @Override
                public void run() {
                    currentRoll = null;
                    rolledDice = null;
                    updateStatusLabels();
                }
            }, 4.0f);
        } else {
            TurnManager.noBust(gameState);
            showMoveOptions();
            updateStatusLabels();
        }
    }

    private void showMoveOptions() {
        clearMoveButtons();

        int[][] pairings = currentRoll.pairings();

        float startX = stage.getViewport().getWorldWidth() * 0.78f;
        float startY = stage.getViewport().getWorldHeight() * 0.80f;
        float spacing = 60;

        //intialize it ig
        if (combinationsLabel == null) {
            combinationsLabel = new Label("", skin);
            combinationsLabel.setFontScale(1f);
            combinationsLabel.setPosition(startX - 200, startY + 30);
            stage.addActor(combinationsLabel);
        }


        if (!legalMoves.isEmpty()) {
            for (int i = 0; i < legalMoves.size(); i++) {
                Move move = legalMoves.get(i);
                String btnText = formatMoveLabel(move);
                TextButton moveBtn = new TextButton(btnText, skin);


                moveBtn.setSize(100, 50);
                moveBtn.setPosition(startX, startY - i * spacing);

                final Move selectedMove = move;
                moveBtn.addListener(new ClickListener() {
                    @Override
                    public void clicked(InputEvent event, float x, float y) {
                        handleMoveSelection(selectedMove);

                        //we hide it after we selecet a move;
                        combinationsLabel.setVisible(false);
                    }
                });

                moveButtons.add(moveBtn);
                stage.addActor(moveBtn);
            }
        } else {

            for (int i = 0; i < illegalMoves.size(); i++) {
                Move move = illegalMoves.get(i);

                String btnText = formatMoveLabel(move);
                TextButton moveBtn = new TextButton(btnText, skin);

                moveBtn.setSize(100, 50);
                moveBtn.setPosition(startX, startY - i * spacing);
                moveBtn.setDisabled(true);
                moveBtn.getLabel().setAlignment(com.badlogic.gdx.utils.Align.center);


                moveButtons.add(moveBtn);
                stage.addActor(moveBtn);
            }

        }

    }

    private void handleMoveSelection(Move move) {
        if (gameOver) return;

        TurnManager.applyMove(gameState, move);
        clearMoveButtons();
        currentRoll = null;
        rolledDice = null;
        legalMoves = null;

        if (checkWin()) {
            return;
        }

        updateStatusLabels();
    }

    private void handleStop() {
        if (gameOver || gameState.countActiveColumns() == 0) return;

        TurnManager.stop(gameState);
        clearMoveButtons();
        currentRoll = null;
        rolledDice = null;
        legalMoves = null;

        if (checkWin()) {
            return;
        }

        updateStatusLabels();
    }

    private boolean checkWin() {
        Player winner = null;
        if (TurnManager.checkWinCondition(gameState, Player.RED)) {
            winner = Player.RED;
        } else if (TurnManager.checkWinCondition(gameState, Player.BLUE)) {
            winner = Player.BLUE;
        }

        if (winner != null) {
            gameOver = true;
            statusLabel.setText(winner + " WINS!");
            rollButton.setDisabled(true);
            stopButton.setDisabled(true);
            clearMoveButtons();
            return true;
        }
        return false;
    }

    private void clearMoveButtons() {
        for (TextButton b : moveButtons) {
            if (b.hasParent()) b.remove();
        }
        moveButtons.clear();
    }

    private void updateStatusLabels() {
        if (gameOver) return;

        Player current = gameState.getCurrentPlayer();
        TurnPhase phase = gameState.getTurnPhase();
        int activeColumns = gameState.countActiveColumns();

        int redWins = countCompletedColumns(Player.RED);
        int blueWins = countCompletedColumns(Player.BLUE);

        String status = String.format("%s | R:%d B:%d A:%d",
            current, redWins, blueWins, activeColumns);
        statusLabel.setText(status);

        rollButton.setDisabled(phase != TurnPhase.ROLL_OR_STOP || rolling);
        stopButton.setDisabled(phase != TurnPhase.ROLL_OR_STOP || activeColumns == 0 || rolling);
    }

    private int countCompletedColumns(Player player) {
        int count = 0;
        for (int col = 0; col < GameConstants.NUM_COLS; col++) {
            int sum = GameConstants.columnToSum(col);
            int maxHeight = GameConstants.maxHeight(sum);
            if (gameState.getMarkerHeight(player, col) == maxHeight) {
                count++;
            }
        }
        return count;
    }

    private static float colX(int col) {
        return ORIGIN_X + col * CELL_W + 4f;
    }

    private static float rowY(int step) {
        return ORIGIN_Y + step * CELL_H + 1f;
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(0.1f, 0.1f, 0.15f, 1f);

        // keyboard control for game
        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.SPACE)) {
            if (!gameOver && gameState.getTurnPhase() == TurnPhase.ROLL_OR_STOP && !rolling) {
                startDiceAnimation();
            }
        }
        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.ENTER)) {
            if (!gameOver && gameState.getTurnPhase() == TurnPhase.ROLL_OR_STOP
                && gameState.countActiveColumns() > 0 && !rolling) {
                handleStop();
            }
        }
        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.ESCAPE)) {
            game.setScreen(new MenuScreen(game));
        }

        game.viewport.apply();
        game.batch.setProjectionMatrix(game.viewport.getCamera().combined);
        game.batch.begin();

        game.batch.draw(board, ORIGIN_X, ORIGIN_Y);

        // drawing permanent markers and temp runners (and cross out claimed columns)
        for (int col = 0; col < GameConstants.NUM_COLS; col++) {
            int sum = GameConstants.columnToSum(col);
            int maxHeight = GameConstants.maxHeight(sum);

            // marker heights
            int redH = gameState.getMarkerHeight(Player.RED, col);
            int blueH = gameState.getMarkerHeight(Player.BLUE, col);
            int tempH = gameState.tempAtCol(col);

            // cross out column if it has been claimed by a player
            if (gameState.getMarkerHeight(Player.RED, col) == maxHeight) { // if claimed by RED
                for (int i = 0; i < maxHeight; i++) {
                    game.batch.draw(redCross, colX(col), rowY(i));
                }
            } else if (gameState.getMarkerHeight(Player.BLUE, col) == maxHeight) { // if claimed by BLUE
                for (int i = 0; i < maxHeight; i++) {
                    game.batch.draw(blueCross, colX(col), rowY(i));
                }
            } else { // if unclaimed, draw markers

                float xOffset = 0;
                if (redH == blueH) xOffset = 2; // offset only if they overlap

                // permanent red markers
                if (redH > 0) {
                    game.batch.draw(redMarker1, colX(col) - xOffset, rowY(redH - 1), MARKER_SIZE, MARKER_SIZE);
                }

                // permanent blue markers
                if (blueH > 0) {
                    game.batch.draw(blueMarker1, colX(col) + xOffset, rowY(blueH - 1), MARKER_SIZE, MARKER_SIZE);
                }

                // temporary runners
                if (tempH > 0) {
                    Player current = gameState.getCurrentPlayer();
                    Texture tempMarker = (current == Player.RED) ? redMarker2 : blueMarker2;
                    game.batch.draw(tempMarker, colX(col), rowY(tempH - 1), MARKER_SIZE, MARKER_SIZE);
                }
            }
        }

        // draw column numbers
        font.setColor(Color.WHITE);
        font.getData().setScale(1f);
        GlyphLayout layout = new GlyphLayout();
        for (int col = 0; col < GameConstants.NUM_COLS; col++) {
            int sum = GameConstants.columnToSum(col);
            String text = String.valueOf(sum);
            layout.setText(font, text);

            float x = colX(col) + CELL_W / 2 - layout.width / 2 - 4;
            float y = ORIGIN_Y - 10;

            font.draw(game.batch, layout, x, y);
        }

        // show whose turn it is
        font.setColor(Color.WHITE);
        font.getData().setScale(1f);
        font.draw(game.batch, "Current Player:", 20, 280);

        font.getData().setScale(3f);
        if (gameState.getCurrentPlayer() == Player.RED) {
            font.setColor(160f/255f, 19f/255f, 19f/255f, 1);
            font.draw(game.batch, "RED", 20, 260);
        } else {
            font.setColor(95f/255f, 205f/255f, 228f/255f, 1);
            font.draw(game.batch, "BLUE", 20, 260);
        }

        // drawing the dice on the right side
        if (rolling) {
            float diceY = 200;
            float diceStartX = 350;
            float diceSize = 35f;
            float gap = 10f;
            for (int i = 0; i < 4; i++) {
                int randomFace = rng.nextInt(6);
                game.batch.draw(diceFaces[randomFace],
                    diceStartX + (i % 2) * (diceSize + gap),
                    diceY - (i / 2) * (diceSize + gap),
                    diceSize, diceSize);
            }
        } else if (rolledDice != null && currentRoll != null) {
            // show the rolled dice in a 2x2 grid
            float diceY = 200;
            float diceStartX = 350;
            float diceSize = 35f;
            float gap = 10f;

            for (int i = 0; i < 4; i++) {
                float x = diceStartX + (i % 2) * (diceSize + gap);
                float y = diceY - (i / 2) * (diceSize + gap);
                game.batch.draw(diceFaces[rolledDice[i] - 1], x, y, diceSize, diceSize);
            }
        }

        game.batch.end();

        // black backgrounds for buttons
        shapeRenderer.setProjectionMatrix(stage.getCamera().combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0, 0, 0, 0.8f);

        shapeRenderer.rect(rollButton.getX(), rollButton.getY(),
            rollButton.getWidth(), rollButton.getHeight());
        shapeRenderer.rect(stopButton.getX(), stopButton.getY(),
            stopButton.getWidth(), stopButton.getHeight());
        shapeRenderer.rect(menuButton.getX(), menuButton.getY(),
            menuButton.getWidth(), menuButton.getHeight());

        for (TextButton btn : moveButtons) {
            shapeRenderer.rect(btn.getX(), btn.getY(), btn.getWidth(), btn.getHeight());
        }

        shapeRenderer.end();

        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        game.viewport.update(width, height, true);
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void hide() {
    }

    @Override
    public void dispose() {
        stage.dispose();
        font.dispose();
        shapeRenderer.dispose();
        if (assets != null) {
            assets.dispose();
        }
    }
}
