package dev.glasstv.launcher.games

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BoardGamesTest {
    @Test fun ticTacToeTakesTheWinAndBlocks() {
        assertEquals(2, TicTacToe.bestMove(intArrayOf(2, 2, 0, 1, 1, 0, 0, 0, 0), 2))
        assertEquals(5, TicTacToe.bestMove(intArrayOf(2, 0, 0, 1, 1, 0, 0, 0, 0), 2))
    }

    @Test fun perfectGraceNeverLosesTicTacToe() {
        repeat(30) { seed ->
            val r = Random(seed); val b = IntArray(9); var turn = 1
            while (TicTacToe.winner(b) == 0) {
                b[if (turn == 1) b.indices.filter { b[it] == 0 }.random(r) else TicTacToe.bestMove(b, 2, r)] = turn
                turn = 3 - turn
            }
            assertTrue(TicTacToe.winner(b) != 1)
        }
    }

    @Test fun connectFourDetectsDiagonalAndBlocks() {
        val g = ConnectFour()
        g.drop(0, 1); g.drop(1, 2); g.drop(1, 1); g.drop(2, 2); g.drop(2, 2); g.drop(2, 1)
        g.drop(3, 2); g.drop(3, 2); g.drop(3, 2)
        assertEquals(0, g.winner())
        g.drop(3, 1)
        assertEquals(1, g.winner())
        val h = ConnectFour(); repeat(3) { h.drop(4, 1) }
        assertEquals(4, h.bestMove(2))
    }

    @Test fun checkersForcesCapturesAndCrowns() {
        val g = Checkers()
        assertEquals(7, g.legalMoves().size)
        g.board.fill(0)
        g.board[5 * 8 + 2] = 1; g.board[4 * 8 + 3] = 2; g.board[1 * 8 + 4] = 1
        val moves = g.legalMoves()
        assertTrue(moves.all { it.captured >= 0 })
        assertTrue(g.play(moves.first()))
        assertEquals(0, g.count(2))
        assertEquals(1, g.winner())
        val k = Checkers(); k.board.fill(0); k.board[1 * 8 + 2] = 1; k.board[7 * 8 + 0] = 2
        k.play(k.legalMoves().first { it.to / 8 == 0 })
        assertTrue(k.board.any { it == 3 })
    }

    @Test fun memoryMatchScoresAndSwapsTurns() {
        val g = MemoryMatch(random = Random(1))
        val a = 0; val b = g.cards.indices.first { it != a && g.cards[it] == g.cards[a] }
        val miss = g.cards.indices.first { g.cards[it] != g.cards[a] }
        g.flip(a); g.flip(miss); assertFalse(g.settle()); assertEquals(2, g.turn)
        g.flip(a); g.flip(b); assertTrue(g.settle()); assertEquals(1, g.scores[2]); assertEquals(2, g.turn)
    }
}
