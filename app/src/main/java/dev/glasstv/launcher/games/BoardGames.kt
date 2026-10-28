package dev.glasstv.launcher.games

import kotlin.random.Random

/** Pure game rules for Game Night. No Android types, so every rule is unit-tested. Players are 1 (Anna) and 2 (Justin / Grace). */

// ---------------------------------------------------------------- Tic-Tac-Toe

object TicTacToe {
    private val LINES = listOf(
        intArrayOf(0, 1, 2), intArrayOf(3, 4, 5), intArrayOf(6, 7, 8),
        intArrayOf(0, 3, 6), intArrayOf(1, 4, 7), intArrayOf(2, 5, 8),
        intArrayOf(0, 4, 8), intArrayOf(2, 4, 6),
    )

    /** Winner (1/2), 0 if still playing, -1 for a draw. */
    fun winner(b: IntArray): Int {
        for (l in LINES) if (b[l[0]] != 0 && b[l[0]] == b[l[1]] && b[l[1]] == b[l[2]]) return b[l[0]]
        return if (b.all { it != 0 }) -1 else 0
    }

    fun winningLine(b: IntArray): IntArray? = LINES.firstOrNull { b[it[0]] != 0 && b[it[0]] == b[it[1]] && b[it[1]] == b[it[2]] }

    /** Best cell for [me] by minimax. [mistakes] (0..1) lets Grace slip sometimes so she's beatable. */
    fun bestMove(b: IntArray, me: Int, random: Random = Random, mistakes: Double = 0.0): Int {
        val free = b.indices.filter { b[it] == 0 }
        if (free.isEmpty()) return -1
        if (random.nextDouble() < mistakes) return free.random(random)
        val scored = free.map { i -> b[i] = me; val s = minimax(b, 3 - me, me); b[i] = 0; i to s }
        val top = scored.maxOf { it.second }
        return scored.filter { it.second == top }.map { it.first }.random(random)
    }

    private fun minimax(b: IntArray, turn: Int, me: Int): Int {
        when (val w = winner(b)) {
            -1 -> return 0
            0 -> {}
            else -> return if (w == me) 10 else -10
        }
        val scores = b.indices.filter { b[it] == 0 }.map { i -> b[i] = turn; val s = minimax(b, 3 - turn, me); b[i] = 0; s }
        return if (turn == me) scores.max() - 1 else scores.min() + 1
    }
}

// ---------------------------------------------------------------- Connect Four

class ConnectFour(val cols: Int = 7, val rows: Int = 6) {
    /** Row 0 is the top. */
    val cells = Array(rows) { IntArray(cols) }

    fun canDrop(c: Int) = c in 0 until cols && cells[0][c] == 0

    /** Drops a disc, returning the row it landed in, or -1 if the column is full. */
    fun drop(c: Int, p: Int): Int {
        if (!canDrop(c)) return -1
        for (r in rows - 1 downTo 0) if (cells[r][c] == 0) { cells[r][c] = p; return r }
        return -1
    }

    private fun undo(c: Int) { for (r in 0 until rows) if (cells[r][c] != 0) { cells[r][c] = 0; return } }

    /** The four winning cells as (row, col), or null. */
    fun winLine(): List<Pair<Int, Int>>? {
        val dirs = listOf(0 to 1, 1 to 0, 1 to 1, 1 to -1)
        for (r in 0 until rows) for (c in 0 until cols) {
            val p = cells[r][c]; if (p == 0) continue
            for ((dr, dc) in dirs) {
                val line = (0 until 4).map { r + dr * it to c + dc * it }
                if (line.all { (rr, cc) -> rr in 0 until rows && cc in 0 until cols && cells[rr][cc] == p }) return line
            }
        }
        return null
    }

    /** Winner (1/2), 0 if still playing, -1 for a draw. */
    fun winner(): Int = winLine()?.first()?.let { (r, c) -> cells[r][c] } ?: if ((0 until cols).none(::canDrop)) -1 else 0

    /** Grace's column: win now, block a win, avoid handing one over, else prefer the middle. */
    fun bestMove(me: Int, random: Random = Random): Int {
        val open = (0 until cols).filter(::canDrop)
        if (open.isEmpty()) return -1
        val them = 3 - me
        open.firstOrNull { drop(it, me); val w = winner() == me; undo(it); w }?.let { return it }
        open.firstOrNull { drop(it, them); val w = winner() == them; undo(it); w }?.let { return it }
        val safe = open.filter { c ->
            drop(c, me)
            val gives = (0 until cols).filter(::canDrop).any { o -> drop(o, them); val w = winner() == them; undo(o); w }
            undo(c); !gives
        }.ifEmpty { open }
        val mid = cols / 2
        return safe.sortedBy { kotlin.math.abs(it - mid) + random.nextDouble() * 1.5 }.first()
    }
}

// ---------------------------------------------------------------- Checkers

/** American checkers on 8×8. Player 1 sits at the bottom and moves up. Captures are mandatory; multi-jumps continue. */
class Checkers {
    data class Move(val from: Int, val to: Int, val captured: Int = -1)

    /** 0 empty, 1/2 men, 3/4 kings of player 1/2. Index = row * 8 + col. */
    val board = IntArray(64).also { b ->
        for (i in 0 until 64) if (dark(i)) when (i / 8) { in 0..2 -> b[i] = 2; in 5..7 -> b[i] = 1 }
    }
    var turn = 1; private set
    /** Piece that must keep jumping this turn, or -1. */
    var chain = -1; private set

    fun owner(v: Int) = if (v == 0) 0 else if (v == 1 || v == 3) 1 else 2
    fun isKing(v: Int) = v >= 3

    fun movesFor(from: Int): List<Move> = legalMoves().filter { it.from == from }

    fun legalMoves(): List<Move> {
        val from = if (chain >= 0) listOf(chain) else (0 until 64).filter { owner(board[it]) == turn }
        val jumps = from.flatMap { jumpsFrom(it) }
        return if (jumps.isNotEmpty() || chain >= 0) jumps else from.flatMap { stepsFrom(it) }
    }

    private fun dirs(i: Int): List<Int> {
        val v = board[i]
        return when { isKing(v) -> listOf(-1, 1); owner(v) == 1 -> listOf(-1); else -> listOf(1) }
    }

    private fun at(r: Int, c: Int) = if (r in 0..7 && c in 0..7) r * 8 + c else -1

    private fun stepsFrom(i: Int) = dirs(i).flatMap { dr -> listOf(-1, 1).mapNotNull { dc ->
        val t = at(i / 8 + dr, i % 8 + dc); if (t >= 0 && board[t] == 0) Move(i, t) else null
    } }

    private fun jumpsFrom(i: Int) = dirs(i).flatMap { dr -> listOf(-1, 1).mapNotNull { dc ->
        val m = at(i / 8 + dr, i % 8 + dc); val t = at(i / 8 + 2 * dr, i % 8 + 2 * dc)
        if (m >= 0 && t >= 0 && board[t] == 0 && owner(board[m]) == 3 - turn) Move(i, t, m) else null
    } }

    /** Applies a legal move. Returns false if it isn't legal. */
    fun play(m: Move): Boolean {
        if (m !in legalMoves()) return false
        var v = board[m.from]
        board[m.from] = 0
        if (m.captured >= 0) board[m.captured] = 0
        val crowned = !isKing(v) && ((owner(v) == 1 && m.to / 8 == 0) || (owner(v) == 2 && m.to / 8 == 7))
        if (crowned) v += 2
        board[m.to] = v
        chain = -1
        if (m.captured >= 0 && !crowned && jumpsFrom(m.to).isNotEmpty()) chain = m.to else turn = 3 - turn
        return true
    }

    fun count(p: Int) = board.count { owner(it) == p }

    /** Winner once a side has no pieces or no moves; 0 while playing. */
    fun winner(): Int = when {
        count(1) == 0 -> 2
        count(2) == 0 -> 1
        legalMoves().isEmpty() -> 3 - turn
        else -> 0
    }

    /** Grace's move: capture when she can, crown when she can, avoid stepping into a jump. */
    fun bestMove(random: Random = Random): Move? {
        val moves = legalMoves().ifEmpty { return null }
        return moves.maxByOrNull { m -> score(m) + random.nextDouble() }
    }

    private fun score(m: Move): Double {
        var s = 0.0
        if (m.captured >= 0) s += 10 + if (isKing(board[m.captured])) 5 else 0
        val r = m.to / 8
        if (!isKing(board[m.from]) && ((turn == 2 && r == 7) || (turn == 1 && r == 0))) s += 6
        // Would the opponent be able to jump the piece where it lands?
        val me = turn
        val copy = board.copyOf()
        board[m.to] = board[m.from]; board[m.from] = 0; if (m.captured >= 0) board[m.captured] = 0
        val tr = m.to / 8; val tc = m.to % 8
        for (dr in listOf(-1, 1)) for (dc in listOf(-1, 1)) {
            val a = at(tr + dr, tc + dc); val b = at(tr - dr, tc - dc)
            if (a >= 0 && b >= 0 && owner(board[a]) == 3 - me && board[b] == 0) {
                val attacker = board[a]
                val forward = if (owner(attacker) == 1) -1 else 1
                if (isKing(attacker) || -dr == forward) s -= 8
            }
        }
        copy.copyInto(board)
        if (m.to % 8 == 0 || m.to % 8 == 7) s += 1 // edges are safe
        return s
    }

    companion object { fun dark(i: Int) = (i / 8 + i % 8) % 2 == 1 }
}

// ---------------------------------------------------------------- Memory Match

/** A face-down deck of emoji pairs. Two players take turns; a match scores and goes again. */
class MemoryMatch(faces: List<String> = FACES, pairs: Int = 8, random: Random = Random) {
    val cards: List<String> = faces.shuffled(random).take(pairs).let { (it + it).shuffled(random) }
    val matched = BooleanArray(cards.size)
    val up = mutableListOf<Int>()
    val scores = intArrayOf(0, 0, 0)
    var turn = 1; private set

    /** Flips [i]. Returns true when two cards are up and the board must be [settle]d after a short look. */
    fun flip(i: Int): Boolean {
        if (matched[i] || i in up || up.size == 2) return false
        up += i
        return up.size == 2
    }

    /** Resolves the two face-up cards. Returns true on a match. */
    fun settle(): Boolean {
        if (up.size < 2) return false
        val hit = cards[up[0]] == cards[up[1]]
        if (hit) { up.forEach { matched[it] = true }; scores[turn]++ } else turn = 3 - turn
        up.clear()
        return hit
    }

    fun done() = matched.all { it }

    companion object {
        val FACES = listOf("💕", "🌸", "🍓", "🧁", "🎀", "🌙", "🍿", "🐰", "🦋", "🌷", "🍩", "⭐")
    }
}
