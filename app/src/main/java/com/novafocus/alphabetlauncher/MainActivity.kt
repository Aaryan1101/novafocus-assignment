package com.novafocus.alphabetlauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private const val MaxPullDp = 88f
private const val PreferredAlphabetSpacingDp = 16f
private val LetterLabels = Array(26) { index -> ('A'.code + index).toChar().toString() }

@Stable
private class AlphabetMotionState {
    var fingerY by mutableFloatStateOf(0f)
    var releaseFingerY by mutableFloatStateOf(0f)
    val releaseProgress = Animatable(0f)
}

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
    val personalization by viewModel.personalization.collectAsStateWithLifecycle()
    var active by remember { mutableStateOf(false) }
    var selected by remember { mutableIntStateOf(-1) }
    var touchBounds by remember { mutableStateOf(AlphabetBounds()) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchClosing by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var favouriteDialogApp by remember { mutableStateOf<LaunchableApp?>(null) }
    val motion = remember { AlphabetMotionState() }
    val searchProgress = remember { Animatable(0f) }
    val searchDismissOffset = remember { Animatable(0f) }
    var revealDragging by remember { mutableStateOf(false) }
    var revealDragProgress by remember { mutableFloatStateOf(0f) }
    var dismissDragging by remember { mutableStateOf(false) }
    var dismissDragOffset by remember { mutableFloatStateOf(0f) }
    var searchExitDirection by remember { mutableFloatStateOf(1f) }
    val view = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val touchZoneWidth = with(density) { 86.dp.toPx() }
    val apps = (state as? LoadState.Ready)?.apps.orEmpty()
    val groups = remember(apps) { apps.groupBy { groupKey(it.label) } }
    val availableLetters = remember(groups) { groups.keys.filterNotNull().toSet() }
    val appsByComponent = remember(apps) { apps.associateBy { it.component.flattenToString() } }
    val favouriteApps = remember(apps, personalization.favourites) {
        apps.filter { it.component.flattenToString() in personalization.favourites }
    }
    val recentApps = remember(appsByComponent, personalization.recent) {
        personalization.recent.mapNotNull(appsByComponent::get)
    }
    val homeApps = remember(apps, favouriteApps, recentApps) {
        if (favouriteApps.isNotEmpty()) {
            favouriteApps.take(7)
        } else {
            (recentApps + apps).distinctBy { it.component }.take(7)
        }
    }
    val searchThreshold = with(density) { 64.dp.toPx() }
    val searchRevealDistance = with(density) { 176.dp.toPx() }
    val searchExitDistance = with(density) { 32.dp.toPx() }
    val canReturnFromSearch = searchOpen && !searchClosing && (
        searchQuery.isBlank() || apps.none { it.label.contains(searchQuery.trim(), ignoreCase = true) }
    )

    DisposableEffect(context, viewModel) {
        val packageChanges = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                viewModel.refresh(showLoading = false)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(context, packageChanges, filter, ContextCompat.RECEIVER_EXPORTED)
        onDispose { context.unregisterReceiver(packageChanges) }
    }

    val onLaunch: (LaunchableApp) -> Unit = remember(viewModel, view.context) {
        { app -> viewModel.launch(app) { android.widget.Toast.makeText(view.context, "App unavailable", android.widget.Toast.LENGTH_SHORT).show() } }
    }
    val onLongPress: (LaunchableApp) -> Unit = { favouriteDialogApp = it }
    val dragModifier = Modifier.pointerInput(touchBounds, touchZoneWidth, searchOpen) {
        if (searchOpen) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!touchBounds.contains(down.position, size.width.toFloat(), touchZoneWidth)) return@awaitEachGesture
            down.consume()
            active = true
            scope.launch {
                motion.releaseProgress.stop()
                motion.releaseProgress.snapTo(1f)
            }
            val initialY = down.position.y.coerceIn(touchBounds.top, touchBounds.bottom)
            motion.fingerY = initialY
            selected = sidebarIndex(initialY, touchBounds.firstCenter, touchBounds.spacing)
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            var last = selected
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                val y = change.position.y.coerceIn(touchBounds.top, touchBounds.bottom)
                motion.fingerY = y
                val index = sidebarIndex(y, touchBounds.firstCenter, touchBounds.spacing)
                if (index != last) {
                    last = index
                    selected = index
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
                change.consume()
            }
            motion.releaseFingerY = motion.fingerY
            active = false
            selected = -1
            scope.launch { motion.releaseProgress.animateTo(0f, spring(dampingRatio = 0.58f, stiffness = 300f)) }
        }
    }
    val closeSearch: (Float) -> Unit = { direction ->
        scope.launch {
            searchClosing = true
            searchExitDirection = if (direction < 0f) -1f else 1f
            searchDismissOffset.stop()
            if (dismissDragging) searchDismissOffset.snapTo(dismissDragOffset)
            dismissDragging = false
            launch {
                searchDismissOffset.animateTo(
                    searchExitDirection * searchExitDistance,
                    spring(dampingRatio = 0.72f, stiffness = 360f)
                )
            }
            searchProgress.animateTo(0f, spring(dampingRatio = 0.68f, stiffness = 320f))
            searchDismissOffset.snapTo(0f)
            searchOpen = false
            searchClosing = false
            searchQuery = ""
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }
    val searchGestureModifier = Modifier.pointerInput(
        searchOpen, active, state, touchBounds, touchZoneWidth, searchThreshold, searchRevealDistance
    ) {
        if (searchOpen || active || state !is LoadState.Ready) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (touchBounds.contains(down.position, size.width.toFloat(), touchZoneWidth)) return@awaitEachGesture
            if (down.position.y < size.height * 0.72f) return@awaitEachGesture
            val start = down.position
            revealDragging = true
            revealDragProgress = 0f
            var reveal = 0f
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                val delta = change.position - start
                if (delta.y < 0f && abs(delta.y) > abs(delta.x) * 1.15f) {
                    reveal = (-delta.y / searchRevealDistance).coerceIn(0f, 1f)
                    revealDragProgress = reveal
                    change.consume()
                }
            }
            if (reveal >= 0.32f) {
                searchQuery = ""
                searchOpen = true
                scope.launch {
                    searchProgress.stop()
                    searchProgress.snapTo(reveal)
                    revealDragging = false
                    searchProgress.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 300f))
                }
            } else {
                scope.launch {
                    searchProgress.stop()
                    searchProgress.snapTo(reveal)
                    revealDragging = false
                    searchProgress.animateTo(0f, spring(dampingRatio = 0.72f, stiffness = 360f))
                }
            }
        }
    }
    val searchReturnModifier = Modifier.pointerInput(canReturnFromSearch, searchThreshold) {
        if (!canReturnFromSearch) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (down.position.y < size.height * 0.42f) return@awaitEachGesture
            val start = down.position
            dismissDragging = true
            dismissDragOffset = searchDismissOffset.value
            var verticalDistance = 0f
            var exitDirection = 1f
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                val delta = change.position - start
                if (abs(delta.y) > abs(delta.x) * 1.15f) {
                    verticalDistance = abs(delta.y)
                    exitDirection = if (delta.y < 0f) -1f else 1f
                    dismissDragOffset = (delta.y * 0.28f).coerceIn(-searchExitDistance, searchExitDistance)
                    change.consume()
                }
            }
            if (verticalDistance >= searchThreshold) {
                closeSearch(exitDirection)
            } else {
                scope.launch {
                    searchDismissOffset.stop()
                    searchDismissOffset.snapTo(dismissDragOffset)
                    dismissDragging = false
                    searchDismissOffset.animateTo(0f, spring(dampingRatio = 0.58f, stiffness = 420f))
                }
            }
        }
    }

    BackHandler(enabled = searchOpen && !searchClosing) {
        closeSearch(1f)
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Box(Modifier.fillMaxSize().then(searchGestureModifier).then(searchReturnModifier).then(dragModifier)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            compositingStrategy = CompositingStrategy.ModulateAlpha
                            val progress = (if (revealDragging) revealDragProgress else searchProgress.value).coerceIn(0f, 1f)
                            alpha = 1f - progress
                            translationY = -progress * 24.dp.toPx()
                        }
                ) {
                    when (state) {
                        LoadState.Loading -> LoadingState()
                        is LoadState.Failed -> ErrorState { viewModel.refresh() }
                        is LoadState.Ready -> if (selected < 0) {
                            Home(homeApps, onLaunch, onLongPress)
                        } else {
                            Results(groups[selectedIndexLetter(selected)].orEmpty(), selected, onLaunch, onLongPress)
                        }
                    }
                    AlphabetBar(
                        active = active,
                        selected = selected,
                        availableLetters = availableLetters,
                        motion = motion,
                        onBounds = { touchBounds = it },
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                }
                if (state is LoadState.Ready) {
                    SearchScreen(
                        apps = apps,
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onLaunch = onLaunch,
                        onLongPress = onLongPress,
                        enabled = searchOpen,
                        modifier = Modifier.graphicsLayer {
                            compositingStrategy = CompositingStrategy.ModulateAlpha
                            val progress = if (revealDragging) revealDragProgress else searchProgress.value
                            val dismissOffset = if (dismissDragging) dismissDragOffset else searchDismissOffset.value
                            alpha = progress.coerceIn(0f, 1f)
                            translationY = if (searchClosing) {
                                searchExitDirection * (1f - progress) * 48.dp.toPx() + dismissOffset
                            } else {
                                (1f - progress) * 48.dp.toPx() + dismissOffset
                            }
                        }
                    )
                }
            }
        }
    }
    favouriteDialogApp?.let { app ->
        val isFavourite = app.component.flattenToString() in personalization.favourites
        FavouriteDialog(
            app = app,
            isFavourite = isFavourite,
            onDismiss = { favouriteDialogApp = null },
            onConfirm = {
                viewModel.setFavourite(app, !isFavourite)
                favouriteDialogApp = null
            }
        )
    }
}

private fun selectedIndexLetter(index: Int): Char = ('A'.code + index.coerceIn(0, 25)).toChar()
private fun selectionLabel(index: Int): String = if (index < 0) "Favourites" else LetterLabels[index.coerceIn(0, 25)]
private fun selectionGlyph(index: Int): String = if (index < 0) "☆" else LetterLabels[index.coerceIn(0, 25)]

@Composable private fun LoadingState() = Box(Modifier.fillMaxSize(), Alignment.Center) {
    CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground, strokeWidth = 2.dp)
}

@Composable private fun ErrorState(retry: () -> Unit) = Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
    Text("Couldn't load apps", color = MaterialTheme.colorScheme.onSurfaceVariant)
    TextButton(onClick = retry) { Text("Retry", color = MaterialTheme.colorScheme.onBackground) }
}

@Composable private fun Home(
    apps: List<LaunchableApp>,
    onLaunch: (LaunchableApp) -> Unit,
    onLongPress: (LaunchableApp) -> Unit
) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); kotlinx.coroutines.delay(30_000) } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val start = maxWidth * 0.12f
        Column(Modifier.fillMaxSize().padding(start = start, end = 82.dp), verticalArrangement = Arrangement.Center) {
        Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(now), color = MaterialTheme.colorScheme.onBackground, fontSize = 50.sp, fontWeight = FontWeight.Light)
        Text(SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(now), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 17.sp)
        Spacer(Modifier.height(20.dp))
        apps.forEach { AppRow(it, onLaunch, onLongPress) }
        }
    }
}

@Composable private fun Results(
    apps: List<LaunchableApp>,
    selected: Int,
    onLaunch: (LaunchableApp) -> Unit,
    onLongPress: (LaunchableApp) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val start = maxWidth * 0.12f
        val startPx = with(density) { start.toPx() }
        val topPx = with(density) { 52.dp.toPx() }
        val headerHeightPx = with(density) { 38.dp.toPx() }
        val rowHeightPx = with(density) { 52.dp.toPx() }
        val iconSizePx = with(density) { 36.dp.roundToPx() }
        val iconLabelGapPx = with(density) { 12.dp.toPx() }
        val sidebarGuard = 82.dp
        val endGuardPx = with(density) { 8.dp.toPx() }
        val contentHeight = maxOf(
            maxHeight,
            52.dp + 38.dp + 52.dp * apps.size.coerceAtLeast(1) + 24.dp
        )
        val foregroundColor = MaterialTheme.colorScheme.onBackground.toArgb()
        val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
        val headerPaint = remember(density, foregroundColor) { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = foregroundColor
            textSize = with(density) { 26.sp.toPx() }
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        } }
        val labelPaint = remember(density, foregroundColor) { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = foregroundColor
            textSize = with(density) { 17.sp.toPx() }
        } }
        val emptyPaint = remember(density, mutedColor) { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = mutedColor
            textSize = with(density) { 16.sp.toPx() }
        } }
        val tapModifier = Modifier.pointerInput(apps, startPx, topPx, headerHeightPx, rowHeightPx, endGuardPx) {
            detectTapGestures(
                onLongPress = { position ->
                    if (position.x < size.width - endGuardPx && position.y >= topPx + headerHeightPx) {
                        val index = ((position.y - topPx - headerHeightPx) / rowHeightPx).toInt()
                        if (index in apps.indices) onLongPress(apps[index])
                    }
                },
                onTap = { position ->
                    if (position.x < size.width - endGuardPx && position.y >= topPx + headerHeightPx) {
                    val index = ((position.y - topPx - headerHeightPx) / rowHeightPx).toInt()
                    if (index in apps.indices) onLaunch(apps[index])
                    }
                }
            )
        }
        val scrollState = rememberScrollState()
        Box(
            Modifier
                .fillMaxSize()
                .padding(end = sidebarGuard)
                .verticalScroll(scrollState)
        ) {
            Canvas(Modifier.fillMaxWidth().height(contentHeight).then(tapModifier)) {
                val nativeCanvas = drawContext.canvas.nativeCanvas
                nativeCanvas.drawText(selectionLabel(selected), startPx, topPx - headerPaint.fontMetrics.ascent, headerPaint)
                val rowsTop = topPx + headerHeightPx
                if (apps.isEmpty()) {
                    nativeCanvas.drawText("No apps", startPx, rowsTop - emptyPaint.fontMetrics.ascent, emptyPaint)
                } else {
                    val labelBaselineOffset = -(labelPaint.fontMetrics.ascent + labelPaint.fontMetrics.descent) / 2f
                    for (index in apps.indices) {
                        val app = apps[index]
                        val rowTop = rowsTop + index * rowHeightPx
                        val rowCenter = rowTop + rowHeightPx / 2f
                        val iconTop = (rowCenter - iconSizePx / 2f).roundToInt()
                        drawImage(
                            image = app.icon,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(app.icon.width, app.icon.height),
                            dstOffset = IntOffset(startPx.roundToInt(), iconTop),
                            dstSize = IntSize(iconSizePx, iconSizePx)
                        )
                        val labelX = startPx + iconSizePx + iconLabelGapPx
                        val availableWidth = size.width - endGuardPx - labelX
                        val displayLabel = if (labelPaint.measureText(app.label) <= availableWidth) {
                            app.label
                        } else {
                            val count = labelPaint.breakText(app.label, true, (availableWidth - labelPaint.measureText("…")).coerceAtLeast(0f), null)
                            app.label.take(count).trimEnd() + "…"
                        }
                        nativeCanvas.drawText(displayLabel, labelX, rowCenter + labelBaselineOffset, labelPaint)
                    }
                }
            }
        }
    }
}

@Composable private fun AppRow(
    app: LaunchableApp,
    onLaunch: (LaunchableApp) -> Unit,
    onLongPress: (LaunchableApp) -> Unit,
    enabled: Boolean = true
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(CircleShape)
            .combinedClickable(
                enabled = enabled,
                onClick = { onLaunch(app) },
                onLongClick = { onLongPress(app) }
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(app.icon, app.label, Modifier.size(36.dp))
        Spacer(Modifier.width(12.dp))
        Text(app.label, color = MaterialTheme.colorScheme.onBackground, fontSize = 17.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

@Composable private fun FavouriteDialog(
    app: LaunchableApp,
    isFavourite: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Image(app.icon, contentDescription = null, modifier = Modifier.size(36.dp)) },
        title = { Text(app.label) },
        text = {
            Text(if (isFavourite) "Remove this app from Favourites?" else "Add this app to Favourites?")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(if (isFavourite) "Remove" else "Add")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable private fun SearchScreen(
    apps: List<LaunchableApp>,
    query: String,
    onQueryChange: (String) -> Unit,
    onLaunch: (LaunchableApp) -> Unit,
    onLongPress: (LaunchableApp) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var settledQuery by remember { mutableStateOf(query) }
    LaunchedEffect(query) {
        kotlinx.coroutines.delay(60)
        settledQuery = query
    }
    val results = remember(apps, settledQuery) {
        if (settledQuery.isBlank()) apps.take(1) else apps.filter { it.label.contains(settledQuery.trim(), ignoreCase = true) }
    }
    val inputShield = if (enabled) {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                }
            }
        }
    } else {
        Modifier
    }
    LaunchedEffect(enabled) {
        if (enabled) {
            kotlinx.coroutines.delay(300)
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    Column(
        modifier
            .fillMaxSize()
            .then(inputShield)
            .padding(top = 18.dp, start = 12.dp, end = 12.dp)
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .focusRequester(focusRequester)
                .padding(horizontal = 16.dp),
            singleLine = true,
            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
            decorationBox = { innerField ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text("Search apps", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
                    innerField()
                }
            }
        )
        Spacer(Modifier.height(10.dp))
        if (results.isEmpty()) {
            Text("No apps found", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp))
        } else {
            LazyColumn(contentPadding = PaddingValues(horizontal = 8.dp), userScrollEnabled = enabled) {
                items(results, key = { it.component.flattenToString() }) { AppRow(it, onLaunch, onLongPress, enabled) }
            }
        }
    }
}

private data class AlphabetBounds(val top: Float = 0f, val bottom: Float = 1f, val firstCenter: Float = 0f, val spacing: Float = 1f) {
    fun contains(position: Offset, screenWidth: Float, zoneWidth: Float): Boolean =
        position.x >= screenWidth - zoneWidth && position.y in top..bottom
}

@Composable private fun AlphabetBar(
    active: Boolean,
    selected: Int,
    availableLetters: Set<Char>,
    motion: AlphabetMotionState,
    onBounds: (AlphabetBounds) -> Unit,
    modifier: Modifier
) {
    BoxWithConstraints(modifier.fillMaxHeight().width(78.dp).padding(end = 12.dp), contentAlignment = Alignment.CenterEnd) {
        val density = LocalDensity.current
        val heightPx = with(density) { maxHeight.toPx() }
        val foregroundColor = MaterialTheme.colorScheme.onBackground.toArgb()
        val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
        val bubbleColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f).toArgb()
        val textPaint = remember(density, foregroundColor) { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = foregroundColor
            textSize = with(density) { 13.sp.toPx() }
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.NORMAL)
        } }
        val emptyTextPaint = remember(textPaint, mutedColor) { android.graphics.Paint(textPaint).apply {
            color = mutedColor
            alpha = 96
        } }
        val starPaint = remember(density, foregroundColor) { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = foregroundColor
            textSize = with(density) { 15.sp.toPx() }
            textAlign = android.graphics.Paint.Align.CENTER
        } }
        val bubbleTextPaint = remember(density, foregroundColor) { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = foregroundColor
            textSize = with(density) { 29.sp.toPx() }
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        } }
        val bubblePaint = remember(bubbleColor) { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = bubbleColor } }
        val starMetrics = starPaint.fontMetrics
        val markerExtent = maxOf((starMetrics.descent - starMetrics.ascent) / 2f, with(density) { 3.dp.toPx() })
        val spacing = safeAlphabetSpacing(heightPx, markerExtent, with(density) { PreferredAlphabetSpacingDp.dp.toPx() })
        val first = (heightPx - spacing * 25f) / 2f
        val letterCenters = remember(first, spacing) { FloatArray(26) { index -> first + index * spacing } }
        val maxPullPx = remember(density) { with(density) { MaxPullDp.dp.toPx() } }
        val baseInsetPx = remember(density) { with(density) { 8.dp.toPx() } }
        val dotRadiusPx = remember(density) { with(density) { 3.dp.toPx() } }
        val bubbleRadiusPx = remember(density) { with(density) { 28.dp.toPx() } }
        val bubbleGapPx = remember(density) { with(density) { 14.dp.toPx() } }
        val letterBaselineOffset = remember(textPaint) { -(textPaint.fontMetrics.ascent + textPaint.fontMetrics.descent) / 2f }
        val starBaselineOffset = remember(starPaint) { -(starPaint.fontMetrics.ascent + starPaint.fontMetrics.descent) / 2f }
        val bubbleBaselineOffset = remember(bubbleTextPaint) { -(bubbleTextPaint.fontMetrics.ascent + bubbleTextPaint.fontMetrics.descent) / 2f }
        LaunchedEffect(heightPx, spacing, first) { onBounds(AlphabetBounds(first - spacing * 1.5f, first + spacing * 25f + spacing / 2f, first, spacing)) }
        val dotPaint = remember(foregroundColor) { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = foregroundColor } }
        Canvas(Modifier.fillMaxSize().semantics {
            contentDescription = "Alphabet navigation"
            stateDescription = if (active) "Selected ${selectionLabel(selected)}" else "Favourites"
        }) {
            val baseX = size.width - baseInsetPx
            val displacementY = if (active) motion.fingerY else motion.releaseFingerY
            val displacementProgress = if (active) 1f else motion.releaseProgress.value
            val starCenter = first - spacing
            for (i in 0 until 26) {
                val y = letterCenters[i]
                val pull = curveDisplacement(y, displacementY, spacing, maxPullPx)
                val x = baseX + pull * displacementProgress
                val paint = if (selectedIndexLetter(i) in availableLetters) textPaint else emptyTextPaint
                drawContext.canvas.nativeCanvas.drawText(LetterLabels[i], x, y + letterBaselineOffset, paint)
            }
            val starPull = curveDisplacement(starCenter, displacementY, spacing, maxPullPx)
            drawContext.canvas.nativeCanvas.drawText("☆", baseX + starPull * displacementProgress, starCenter + starBaselineOffset, starPaint)
            drawContext.canvas.nativeCanvas.drawCircle(baseX, first + 26 * spacing, dotRadiusPx, dotPaint)
            if (active) {
                val bubbleY = motion.fingerY.coerceIn(bubbleRadiusPx, size.height - bubbleRadiusPx)
                val bubbleX = baseX - maxPullPx - bubbleGapPx - bubbleRadiusPx
                drawContext.canvas.nativeCanvas.drawCircle(bubbleX, bubbleY, bubbleRadiusPx, bubblePaint)
                drawContext.canvas.nativeCanvas.drawText(selectionGlyph(selected), bubbleX, bubbleY + bubbleBaselineOffset, bubbleTextPaint)
            }
        }
    }
}

@Composable private fun AlphabetLauncherTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) {
        darkColorScheme(
            primary = Color(0xFFC9B6FF),
            background = Color.Black,
            onBackground = Color.White,
            surface = Color.Black,
            onSurface = Color.White,
            surfaceVariant = Color(0xFF1B1B1F),
            onSurfaceVariant = Color(0xFFB6B2BC)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF6847B8),
            background = Color(0xFFF8F6FB),
            onBackground = Color(0xFF1B1820),
            surface = Color.White,
            onSurface = Color(0xFF1B1820),
            surfaceVariant = Color(0xFFECE8F2),
            onSurfaceVariant = Color(0xFF655F6A)
        )
    }
    MaterialTheme(colorScheme = colors, content = content)
}
