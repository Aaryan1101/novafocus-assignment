package com.novafocus.alphabetlauncher

import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Black = Color.Black
private val Muted = Color(0xFFB6B6B6)
private const val MaxPullDp = 104f

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AlphabetLauncherTheme { LauncherScreen() } }
    }
}

@Composable
private fun LauncherScreen(viewModel: LauncherViewModel = viewModel()) {
    val state by viewModel.loadState.collectAsStateWithLifecycle()
    var active by remember { mutableStateOf(false) }
    var selected by remember { mutableIntStateOf(0) }
    var fingerY by remember { mutableFloatStateOf(0f) }
    var touchBounds by remember { mutableStateOf(AlphabetBounds()) }
    val view = LocalView.current
    val density = LocalDensity.current
    val touchZoneWidth = with(density) { 86.dp.toPx() }
    val apps = (state as? LoadState.Ready)?.apps.orEmpty()
    val groups = remember(apps) { apps.groupBy { groupKey(it.label) } }

    val onLaunch: (LaunchableApp) -> Unit = { app -> viewModel.launch(app) { android.widget.Toast.makeText(view.context, "App unavailable", android.widget.Toast.LENGTH_SHORT).show() } }
    val dragModifier = Modifier.pointerInput(apps, touchBounds, touchZoneWidth) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!touchBounds.contains(down.position, size.width.toFloat(), touchZoneWidth)) return@awaitEachGesture
            down.consume()
            active = true
            var last = -1
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                val y = change.position.y.coerceIn(touchBounds.top, touchBounds.bottom)
                fingerY = y
                val index = letterIndex(y, touchBounds.firstCenter, touchBounds.spacing)
                if (index != last) {
                    last = index
                    selected = index
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
                change.consume()
            }
            active = false
        }
    }

    Box(Modifier.fillMaxSize().background(Black).then(dragModifier)) {
        when (state) {
            LoadState.Loading -> LoadingState()
            is LoadState.Failed -> ErrorState { viewModel.refresh() }
            is LoadState.Ready -> if (active) Results(groups[selectedIndexLetter(selected)].orEmpty(), selected, onLaunch) else Home(apps.take(7), onLaunch)
        }
        AlphabetBar(
            active = active,
            selected = selected,
            fingerY = fingerY,
            release = if (active) 1f else 0f,
            onBounds = { touchBounds = it },
            modifier = Modifier.align(Alignment.CenterEnd)
        )
        if (active) Bubble(selected, fingerY, touchBounds, density)
    }
}

private fun selectedIndexLetter(index: Int): Char = ('A'.code + index.coerceIn(0, 25)).toChar()

@Composable private fun LoadingState() = Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp) }

@Composable private fun ErrorState(retry: () -> Unit) = Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
    Text("Couldn't load apps", color = Muted)
    TextButton(onClick = retry) { Text("Retry", color = Color.White) }
}

@Composable private fun Home(apps: List<LaunchableApp>, onLaunch: (LaunchableApp) -> Unit) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); kotlinx.coroutines.delay(30_000) } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val start = maxWidth * 0.12f
        Column(Modifier.fillMaxSize().padding(start = start, end = 76.dp), verticalArrangement = Arrangement.Center) {
        Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(now), color = Color.White, fontSize = 58.sp, fontWeight = FontWeight.Light)
        Text(SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(now), color = Muted, fontSize = 19.sp)
        Spacer(Modifier.height(24.dp))
        apps.forEach { AppRow(it, onLaunch) }
        }
    }
}

@Composable private fun Results(apps: List<LaunchableApp>, selected: Int, onLaunch: (LaunchableApp) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val start = maxWidth * 0.12f
        Column(Modifier.fillMaxSize().padding(start = start, top = 56.dp, end = 76.dp)) {
        Text(selectedIndexLetter(selected).toString(), color = Color.White, fontSize = 29.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))
        if (apps.isEmpty()) Text("No apps", color = Muted, fontSize = 18.sp)
        else LazyColumn { items(apps, key = { it.component.flattenToString() }) { AppRow(it, onLaunch) } }
        }
    }
}

@Composable private fun AppRow(app: LaunchableApp, onLaunch: (LaunchableApp) -> Unit) {
    Row(Modifier.fillMaxWidth().height(70.dp).clip(CircleShape).clickable(onClick = { onLaunch(app) }).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(app.icon.asImageBitmap(), app.label, Modifier.size(50.dp))
        Spacer(Modifier.width(18.dp))
        Text(app.label, color = Color.White, fontSize = 19.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

private data class AlphabetBounds(val top: Float = 0f, val bottom: Float = 1f, val firstCenter: Float = 0f, val spacing: Float = 1f) {
    fun contains(position: Offset, screenWidth: Float, zoneWidth: Float): Boolean =
        position.x >= screenWidth - zoneWidth && position.y in top..bottom
}

@Composable private fun AlphabetBar(active: Boolean, selected: Int, fingerY: Float, release: Float, onBounds: (AlphabetBounds) -> Unit, modifier: Modifier) {
    BoxWithConstraints(modifier.fillMaxHeight().width(78.dp).padding(end = 12.dp), contentAlignment = Alignment.CenterEnd) {
        val density = LocalDensity.current
        val heightPx = with(density) { maxHeight.toPx() }
        val spacing = (heightPx / 27f).coerceAtLeast(with(density) { 18.dp.toPx() })
        val first = (heightPx - spacing * 25f) / 2f
        LaunchedEffect(heightPx, spacing, first) { onBounds(AlphabetBounds(first - spacing / 2f, first + spacing * 25f + spacing / 2f, first, spacing)) }
        Canvas(Modifier.fillMaxSize()) {
            val baseX = size.width - with(density) { 4.dp.toPx() }
            for (i in 0 until 26) {
                val y = first + i * spacing
                val pull = if (active) curveDisplacement(y, fingerY, spacing, with(density) { MaxPullDp.dp.toPx() }) else 0f
                val x = baseX + pull * release
                drawContext.canvas.nativeCanvas.drawText(('A'.code + i).toChar().toString(), x, y + with(density) { 5.dp.toPx() }, android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = with(density) { 14.sp.toPx() }
                    textAlign = android.graphics.Paint.Align.RIGHT
                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                })
            }
            drawContext.canvas.nativeCanvas.drawText("☆", baseX, first - spacing + with(density) { 5.dp.toPx() }, android.graphics.Paint().apply { color = android.graphics.Color.WHITE; textSize = with(density) { 20.sp.toPx() }; textAlign = android.graphics.Paint.Align.RIGHT })
            drawContext.canvas.nativeCanvas.drawCircle(baseX - with(density) { 4.dp.toPx() }, first + 26 * spacing, with(density) { 3.dp.toPx() }, android.graphics.Paint().apply { color = android.graphics.Color.WHITE })
        }
    }
}

@Composable private fun Bubble(selected: Int, y: Float, bounds: AlphabetBounds, density: Density) {
    val bubbleY = y.coerceIn(bounds.top + dpPx(32, density), bounds.bottom - dpPx(32, density))
    Box(Modifier.offset(x = (-88).dp, y = with(density) { bubbleY.toDp() - 32.dp }).size(64.dp).clip(CircleShape).background(Color(0xCC333333)), Alignment.Center) {
        Text(selectedIndexLetter(selected).toString(), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Medium)
    }
}

private fun dpPx(value: Int, density: Density): Float = with(density) { value.dp.toPx() }

@Composable private fun AlphabetLauncherTheme(content: @Composable () -> Unit) { MaterialTheme(colorScheme = darkColorScheme(background = Black, surface = Black), content = content) }
