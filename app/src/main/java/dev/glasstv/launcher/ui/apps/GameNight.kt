package dev.glasstv.launcher.ui.apps

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.glasstv.launcher.Personal
import dev.glasstv.launcher.games.Checkers
import dev.glasstv.launcher.games.ConnectFour
import dev.glasstv.launcher.games.MemoryMatch
import dev.glasstv.launcher.games.TicTacToe
import dev.glasstv.launcher.ui.theme.GlassType
import dev.glasstv.launcher.ui.theme.LocalGlass
import kotlinx.coroutines.delay

private enum class Game(val title: String, val emoji: String, val blurb: String) {
    TIC_TAC_TOE("Tic-Tac-Toe", "💗", "Hearts vs. stars"),
    CONNECT_FOUR("Connect 4", "🔴", "Four in a row wins"),
    CHECKERS("Checkers", "🏁", "Classic, with kings"),
    MEMORY("Memory", "🃏", "Find the pairs"),
}

/** Board games for two on the couch, or against Grace. */
@Composable
fun GameNightApp(onClose: () -> Unit) {
    var game by remember { mutableStateOf<Game?>(null) }
    var vsGrace by remember { mutableStateOf(true) }
    val g = game
    AppSheet(g?.emoji ?: "🎲", g?.title ?: "Game Night", if (g == null) "Pick a game · play each other or Grace" else "Back to games: press Back", onClose = { if (g != null) game = null else onClose() }) {
        when (g) {
            null -> GamePicker(vsGrace, { vsGrace = it }) { game = it }
            Game.TIC_TAC_TOE -> TicTacToeBoard(vsGrace)
            Game.CONNECT_FOUR -> ConnectFourBoard(vsGrace)
            Game.CHECKERS -> CheckersBoard(vsGrace)
            Game.MEMORY -> MemoryBoard(vsGrace)
        }
    }
}

private fun p2Name(vsGrace: Boolean) = if (vsGrace) Personal.ASSISTANT else Personal.LOVE_FROM

@Composable
private fun ColumnScope.GamePicker(vsGrace: Boolean, setVsGrace: (Boolean) -> Unit, onPick: (Game) -> Unit) {
    val c = LocalGlass.current
    val first = rememberInitialFocus()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Hint("Playing:  ")
        Btn("${Personal.NAME} vs ${Personal.ASSISTANT}", primary = vsGrace) { setVsGrace(true) }
        Spacer(Modifier.width(10.dp))
        Btn("${Personal.NAME} vs ${Personal.LOVE_FROM}", primary = !vsGrace) { setVsGrace(false) }
    }
    Spacer(Modifier.height(22.dp))
    Spacer(Modifier.weight(1f))
    Row(Modifier.fillMaxWidth().height(210.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Game.entries.forEachIndexed { i, gm ->
            Surface(
                onClick = { onPick(gm) },
                modifier = Modifier.weight(1f).fillMaxHeight().initialFocus(if (i == 0) first else null),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(26.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = c.background, focusedContainerColor = c.pink, pressedContainerColor = c.pink,
                    contentColor = c.text, focusedContentColor = c.cocoa,
                ),
                border = ClickableSurfaceDefaults.border(
                    border = Border(BorderStroke(1.dp, c.highlightTop), shape = RoundedCornerShape(26.dp)),
                    focusedBorder = Border(BorderStroke(3.dp, c.rose), shape = RoundedCornerShape(26.dp)),
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                glow = ClickableSurfaceDefaults.glow(Glow.None, Glow(c.shadow, 18.dp)),
            ) {
                Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(gm.emoji, style = GlassType.hero)
                    Spacer(Modifier.height(14.dp))
                    Text(gm.title, style = GlassType.title, maxLines = 1)
                    Text(gm.blurb, style = GlassType.small, color = c.textSoft, maxLines = 1)
                }
            }
        }
    }
    Spacer(Modifier.weight(1f))
}

// ---------------------------------------------------------------- shared bits

/** Board on the left, score/status panel on the right. */
@Composable
private fun ColumnScope.GameLayout(status: String, sub: String, onNew: () -> Unit, board: @Composable () -> Unit) {
    val c = LocalGlass.current
    Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1.4f).fillMaxHeight(), contentAlignment = Alignment.Center) { board() }
        Spacer(Modifier.width(28.dp))
        Column(Modifier.weight(1f)) {
            Text(status, style = GlassType.hero.copy(fontSize = GlassType.hero.fontSize * 0.62f), color = c.text)
            Spacer(Modifier.height(6.dp))
            Text(sub, style = GlassType.body, color = c.textSoft)
            Spacer(Modifier.height(22.dp))
            Btn("New game", primary = true, onClick = onNew)
        }
    }
}

@Composable
private fun Piece(player: Int, size: Dp, king: Boolean = false) {
    val c = LocalGlass.current
    val fill = if (player == 1) c.rose else c.cocoa
    val s by animateFloatAsState(if (player == 0) 0f else 1f, label = "piece")
    Box(
        Modifier.size(size).scale(s).background(fill, CircleShape).border(3.dp, Color.White.copy(alpha = 0.55f), CircleShape),
        contentAlignment = Alignment.Center,
    ) { if (king) Text("♛", style = GlassType.label, color = Color.White) }
}

/** A focusable board square. */
@Composable
private fun Cell(
    size: Dp, bg: Color, onClick: () -> Unit, modifier: Modifier = Modifier, highlight: Boolean = false,
    shape: RoundedCornerShape = RoundedCornerShape(12.dp), content: @Composable () -> Unit,
) {
    val c = LocalGlass.current
    Surface(
        onClick = onClick, modifier = modifier.size(size),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(containerColor = if (highlight) c.pink else bg, focusedContainerColor = c.pink, pressedContainerColor = c.pink),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(if (highlight) 2.dp else 0.dp, c.rose), shape = shape),
            focusedBorder = Border(BorderStroke(3.dp, c.rose), shape = shape),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        glow = ClickableSurfaceDefaults.glow(Glow.None, Glow.None),
    ) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() } }
}

private fun turnText(turn: Int, vsGrace: Boolean) = if (turn == 1) "${Personal.NAME}'s turn" else if (vsGrace) "${Personal.ASSISTANT} is thinking…" else "${Personal.LOVE_FROM}'s turn"

private fun resultText(w: Int, vsGrace: Boolean) = when (w) {
    -1 -> "It's a tie ♡"
    1 -> "${Personal.NAME} wins! 🎉"
    else -> if (vsGrace) "${Personal.ASSISTANT} wins this one 🐰" else "${Personal.LOVE_FROM} wins! 🎉"
}

// ---------------------------------------------------------------- Tic-Tac-Toe

@Composable
private fun ColumnScope.TicTacToeBoard(vsGrace: Boolean) {
    val c = LocalGlass.current
    var round by remember { mutableIntStateOf(0) }
    val board = remember(round) { IntArray(9) }
    var turn by remember(round) { mutableIntStateOf(1) }
    var tick by remember { mutableIntStateOf(0) }
    val first = rememberInitialFocus()
    val w = run { tick; TicTacToe.winner(board) }
    val line = run { tick; TicTacToe.winningLine(board) }

    fun place(i: Int) { if (board[i] == 0 && w == 0) { board[i] = turn; turn = 3 - turn; tick++ } }

    LaunchedEffect(turn, round, w) {
        if (vsGrace && turn == 2 && w == 0) { delay(650); place(TicTacToe.bestMove(board, 2, mistakes = 0.25)) }
    }
    GameLayout(if (w != 0) resultText(w, vsGrace) else turnText(turn, vsGrace), "${Personal.NAME} 💗  ·  ${p2Name(vsGrace)} ⭐", { round++ }) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (r in 0 until 3) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (col in 0 until 3) {
                    val i = r * 3 + col
                    Cell(84.dp, c.background, { if (!(vsGrace && turn == 2)) place(i) }, Modifier.initialFocus(if (i == 4) first else null),
                        highlight = line?.contains(i) == true, shape = RoundedCornerShape(22.dp)) {
                        tick
                        when (board[i]) { 1 -> Text("💗", style = GlassType.hero); 2 -> Text("⭐", style = GlassType.hero) }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Connect Four

@Composable
private fun ColumnScope.ConnectFourBoard(vsGrace: Boolean) {
    val c = LocalGlass.current
    var round by remember { mutableIntStateOf(0) }
    val game = remember(round) { ConnectFour() }
    var turn by remember(round) { mutableIntStateOf(1) }
    var tick by remember { mutableIntStateOf(0) }
    val first = rememberInitialFocus()
    val w = run { tick; game.winner() }
    val line = run { tick; game.winLine() }

    fun drop(col: Int) { if (w == 0 && game.drop(col, turn) >= 0) { turn = 3 - turn; tick++ } }

    LaunchedEffect(turn, round, w) {
        if (vsGrace && turn == 2 && w == 0) { delay(650); drop(game.bestMove(2)) }
    }
    GameLayout(if (w != 0) resultText(w, vsGrace) else turnText(turn, vsGrace), "Pick a column and press OK to drop.", { round++ }) {
        Column(
            Modifier.background(c.pink.copy(alpha = 0.55f), RoundedCornerShape(24.dp)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (r in 0 until game.rows) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (col in 0 until game.cols) {
                    Cell(40.dp, c.background, { if (!(vsGrace && turn == 2)) drop(col) },
                        Modifier.initialFocus(if (r == game.rows - 1 && col == 3) first else null),
                        highlight = line?.contains(r to col) == true, shape = RoundedCornerShape(50)) {
                        tick
                        val p = game.cells[r][col]
                        if (p != 0) Piece(p, 32.dp)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Checkers

@Composable
private fun ColumnScope.CheckersBoard(vsGrace: Boolean) {
    val c = LocalGlass.current
    var round by remember { mutableIntStateOf(0) }
    val game = remember(round) { Checkers() }
    var selected by remember(round) { mutableIntStateOf(-1) }
    var tick by remember { mutableIntStateOf(0) }
    val first = rememberInitialFocus()
    val turn = run { tick; game.turn }
    val w = run { tick; game.winner() }
    val chain = run { tick; game.chain }
    val sel = if (chain >= 0) chain else selected
    val targets = run { tick; if (sel >= 0) game.movesFor(sel) else emptyList() }
    val movable = run { tick; game.legalMoves().map { it.from }.toSet() }

    fun tap(i: Int) {
        if (w != 0 || (vsGrace && turn == 2)) return
        targets.firstOrNull { it.to == i }?.let { game.play(it); selected = -1; tick++; return }
        if (chain < 0 && i in movable) selected = i
    }

    LaunchedEffect(tick, round) {
        if (vsGrace && game.turn == 2 && game.winner() == 0) { delay(700); game.bestMove()?.let { game.play(it); tick++ } }
    }
    val sub = "${Personal.NAME} ${game.count(1)} 🌸  ·  ${p2Name(vsGrace)} ${game.count(2)} 🍫\n" +
        if (chain >= 0) "Keep jumping!" else "Pick a piece, then where it goes."
    GameLayout(if (w != 0) resultText(w, vsGrace) else turnText(turn, vsGrace), sub, { round++ }) {
        Column(Modifier.background(c.latte.copy(alpha = 0.35f), RoundedCornerShape(16.dp)).padding(8.dp)) {
            for (r in 0 until 8) Row {
                for (col in 0 until 8) {
                    val i = r * 8 + col
                    val v = run { tick; game.board[i] }
                    val dark = Checkers.dark(i)
                    val square = if (dark) c.pink.copy(alpha = 0.8f) else c.background
                    val mod = Modifier.initialFocus(if (i == 42) first else null)
                    if (!dark) Box(Modifier.size(34.dp).background(square)) {}
                    else Cell(34.dp, square, { tap(i) }, mod, highlight = i == sel || targets.any { it.to == i }, shape = RoundedCornerShape(6.dp)) {
                        if (v != 0) Piece(game.owner(v), 26.dp, king = game.isKing(v))
                        else if (targets.any { it.to == i }) Box(Modifier.size(12.dp).background(c.rose, CircleShape))
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Memory Match

@Composable
private fun ColumnScope.MemoryBoard(vsGrace: Boolean) {
    val c = LocalGlass.current
    var round by remember { mutableIntStateOf(0) }
    val game = remember(round) { MemoryMatch() }
    var tick by remember { mutableIntStateOf(0) }
    var looking by remember(round) { mutableStateOf(false) }
    // Grace remembers some of what she's seen, so she's good but beatable.
    val seen = remember(round) { mutableMapOf<Int, String>() }
    val first = rememberInitialFocus()
    val turn = run { tick; game.turn }
    val done = run { tick; game.done() }

    fun flip(i: Int) { if (!looking && game.flip(i)) looking = true; seen[i] = game.cards[i]; tick++ }

    LaunchedEffect(looking) { if (looking) { delay(1100); game.settle(); looking = false; tick++ } }
    LaunchedEffect(tick, round) {
        if (!vsGrace || game.turn != 2 || game.done() || looking) return@LaunchedEffect
        delay(700)
        val open = game.cards.indices.filter { !game.matched[it] && it !in game.up }
        val known = seen.filterKeys { it in open && kotlin.random.Random.nextDouble() < 0.7 }
        val pick = if (game.up.isEmpty()) {
            known.entries.groupBy { it.value }.values.firstOrNull { it.size >= 2 }?.first()?.key ?: open.filter { it !in seen }.randomOrNull() ?: open.random()
        } else {
            val face = game.cards[game.up[0]]
            known.entries.firstOrNull { it.value == face && it.key != game.up[0] }?.key ?: open.filter { it !in seen }.randomOrNull() ?: open.random()
        }
        flip(pick)
    }
    val w = if (!done) 0 else game.scores[1].compareTo(game.scores[2]).let { if (it > 0) 1 else if (it < 0) 2 else -1 }
    GameLayout(if (done) resultText(w, vsGrace) else turnText(turn, vsGrace),
        "${Personal.NAME} ${game.scores[1]}  ·  ${p2Name(vsGrace)} ${game.scores[2]}\nA match means go again.", { round++ }) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (r in 0 until 4) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (col in 0 until 4) {
                    val i = r * 4 + col
                    tick
                    val faceUp = game.matched[i] || i in game.up
                    Cell(64.dp, if (faceUp) c.background else c.rose.copy(alpha = 0.85f), { if (!(vsGrace && turn == 2) && !faceUp) flip(i) },
                        Modifier.initialFocus(if (i == 0) first else null), highlight = game.matched[i], shape = RoundedCornerShape(16.dp)) {
                        Text(if (faceUp) game.cards[i] else "♡", style = GlassType.hero.copy(fontSize = GlassType.hero.fontSize * 0.8f), color = Color.White)
                    }
                }
            }
        }
    }
}
