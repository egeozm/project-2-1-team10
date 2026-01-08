package io.github.cantstop.view.gui;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import io.github.cantstop.model.GameState;
import io.github.cantstop.model.Move;
import io.github.cantstop.view.gui.screens.PlayScreen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MoveButtonRenderer {

    private final GameState gameState;
    private final PlayScreen playScreen;

    private final TextButton.TextButtonStyle borderStyle;

    TextButton[] moveButtons;

    // Layout constants
    private static final float ORIGIN_X = 440f;
    private static final float ORIGIN_Y = 230f;
    private static final float GAP_SIZE = 40f;
    private static final float BUTTON_WIDTH = 100f;
    private static final float BUTTON_HEIGHT = 34f;

    public MoveButtonRenderer(GameState gameState, PlayScreen playScreen) {
        this.gameState = gameState;
        this.playScreen = playScreen;
        borderStyle = ButtonStyle.createBorderButtonStyle();
    }

    // Draw board texture
    public void showMoveButtons(List<Move> moves, Stage stage) {

        moves = dedupeMoves(moves);

        int numberOfMoves = moves.size();

        String[] moveLabels = new String[numberOfMoves];
        moveButtons = new TextButton[numberOfMoves];

        for (int i = 0; i < numberOfMoves; i++) {
            moveLabels[i] = formatMove(moves.get(i));

            moveButtons[i] = new TextButton(moveLabels[i], borderStyle);

            moveButtons[i].setSize(BUTTON_WIDTH, BUTTON_HEIGHT);
            moveButtons[i].setPosition(ORIGIN_X, ORIGIN_Y - i * GAP_SIZE);

            final Move selectedMove = moves.get(i);

            moveButtons[i].addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    playScreen.handleMoveInput(selectedMove);
                }
            });

            stage.addActor(moveButtons[i]);
        }
    }

    // remove the buttons from the stage
    public void clearMoveButtons() {
        if (moveButtons == null) return;

        for (TextButton b : moveButtons) {
            if (b != null && b.hasParent()) {
                b.remove();
            }
        }

        moveButtons = null;
    }

    // Remove duplicates while preserving the first occurrence and order.
    private List<Move> dedupeMoves(List<Move> moves) {
        if (moves == null || moves.isEmpty()) return moves;
        Map<String, Move> unique = new LinkedHashMap<>();
        for (Move m : moves) unique.putIfAbsent(formatMove(m), m);
        return new ArrayList<>(unique.values());
    }

    // Build a canonical key for a move: order doesn't matter (5+8 == 8+5).
    private static String formatMove(Move m) {
        int a = m.sumA(), b = m.sumB();
        if (a == 0 || b == 0) return Integer.toString(a + b); // single move
        int x = Math.min(a, b), y = Math.max(a, b);
        return x + "+" + y;
    }
}
