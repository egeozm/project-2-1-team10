package io.github.cantstop.backend.AI_MCTS;

import io.github.cantstop.backend.GameConstants;
import io.github.cantstop.backend.GameState;
import io.github.cantstop.backend.Player;

import java.util.Random;

/**
 * Niezależna heurystyka Can't Stop do użycia przez MCTS (cutoff w rolloucie).
 * NIC nie zmienia w istniejącym kodzie. Importuje tylko publiczne klasy.
 *
 * Składniki (identyczne wagami z AIPlayer):
 *  - progressTerm:   0.60 * (myProgress - oppProgress)
 *  - tempoTerm:      0.08 * (myActive - oppActive)   [tempo liczone wyłącznie dla currentPlayer]
 *  - nearWinTerm:    0.30 * (myNearWins - oppNearWins)
 *  - lockTerm:       0.10 * (myLocks - oppLocks)
 *  - noise:          ~1e-6 (deterministyczny seed) – tie-break
 *
 * Wynik może być użyty bezpośrednio albo po normalizacji do [0,1].
 */
public final class Heuristics {

    private Heuristics() {}

    private static final double W_PROGRESS = 0.60;
    private static final double W_TEMPO    = 0.08;
    private static final double W_NEARWIN  = 0.30;
    private static final double W_LOCKS    = 0.10;

    private static final Random NOISE_RNG  = new Random(42);

    /** Główna ocena – ZAWSZE w perspektywie gracza 'perspective' (np. rootPlayer w MCTS). */
    public static double evaluate(GameState state, Player perspective) {
        final Player opp = perspective.opponent();
        final Player current = state.getCurrentPlayer();

        // --- Postęp stałych znaczników (0..1 per kolumna)
        double myProgress = 0.0;
        double oppProgress = 0.0;

        // --- Tempo: liczba aktywnych temp runnerów po stronie gracza na ruchu
        int myActive = 0, oppActive = 0;

        for (int col = 0; col < GameConstants.NUM_COLS; col++) {
            final int sum  = GameConstants.columnToSum(col);
            final int maxH = GameConstants.maxHeight(sum);

            final int myPerm  = (perspective == Player.RED) ? state.redPermAtCol(col)  : state.bluePermAtCol(col);
            final int oppPerm = (opp         == Player.RED) ? state.redPermAtCol(col) : state.bluePermAtCol(col);

            myProgress  += ((double) myPerm)  / maxH;
            oppProgress += ((double) oppPerm) / maxH;

            final int temp = state.tempAtCol(col);
            // temp runners ZAWSZE należą do currentPlayer
            if (current == perspective && temp > 0) myActive++;
            if (current == opp         && temp > 0) oppActive++;
        }

        final double progressTerm = W_PROGRESS * (myProgress - oppProgress);
        final double tempoTerm    = W_TEMPO    * (myActive   - oppActive);

        // --- Near wins (1 pole do zamknięcia, liczymy stałe markery)
        final int nearWins     = nearWinCount(state, perspective);
        final int oppNearWins  = nearWinCount(state, opp);
        final double nearWinTerm = W_NEARWIN * (nearWins - oppNearWins);

        // --- Zamknięte kolumny (locks)
        final int myLocks  = countLocks(state, perspective);
        final int oppLocks = countLocks(state, opp);
        final double lockTerm = W_LOCKS * (myLocks - oppLocks);

        // --- Delikatny szum do tie-breaków
        final double noise = NOISE_RNG.nextDouble() * 1e-6;

        return progressTerm + tempoTerm + nearWinTerm + lockTerm + noise;
    }

    /** Normalizacja do [0,1] – wygodne jako „nagroda” przy uciętym rolloucie. */
    public static double normalize01(double h) {
        final double alpha = 1.5; // ewentualnie dostroić w arenie
        return 1.0 / (1.0 + Math.exp(-alpha * h));
    }

    /** Ile kolumn jest „na 1 polu od zamknięcia” dla gracza p (liczymy stałe markery). */
    public static int nearWinCount(GameState state, Player p) {
        int c = 0;
        for (int col = 0; col < GameConstants.NUM_COLS; col++) {
            final int sum  = GameConstants.columnToSum(col);
            final int maxH = GameConstants.maxHeight(sum);
            final int h    = state.getMarkerHeight(p, col);
            if (h == maxH - 1) c++;
        }
        return c;
    }

    /** Ile kolumn jest zamkniętych przez gracza p (stały marker == max wysokość). */
    public static int countLocks(GameState state, Player p) {
        int c = 0;
        for (int col = 0; col < GameConstants.NUM_COLS; col++) {
            final int sum  = GameConstants.columnToSum(col);
            final int maxH = GameConstants.maxHeight(sum);
            if (state.getMarkerHeight(p, col) == maxH) c++;
        }
        return c;
    }
}
