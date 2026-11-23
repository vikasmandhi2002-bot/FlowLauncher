package com.flow.launcher

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.exp
import kotlin.math.pow

data class AppEntry(
    val label: String,
    val packageName: String,
    val icon: ImageBitmap,
    val firstLetter: Char
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FlowLauncherScreen()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Prevent exiting the home screen on back press
    }
}

@Composable
fun FlowLauncherScreen() {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val allApps = remember { loadInstalledApps(context) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }

    val displayedApps = remember(allApps, searchQuery) {
        if (searchQuery.isBlank()) allApps
        else allApps.filter { it.label.contains(searchQuery, ignoreCase = true) }
    }

    // Real-time Clock
    var currentTime by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = Date()
            delay(1000L)
        }
    }
    val timeStr = remember(currentTime) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(currentTime) }
    val secStr = remember(currentTime) { SimpleDateFormat(":ss", Locale.getDefault()).format(currentTime) }
    val dateStr = remember(currentTime) { SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(currentTime).uppercase() }

    // Niagara Wave Scrubber State
    var scrubberTouchY by remember { mutableFloatStateOf(-1000f) }
    var isScrubbing by remember { mutableStateOf(false) }
    var activeLetter by remember { mutableStateOf<Char?>(null) }
    var railTopY by remember { mutableFloatStateOf(0f) }
    var railHeight by remember { mutableFloatStateOf(1f) }
    val alphabet = remember { ('A'..'Z').toList() }

    // Atmospheric ColorOS 17 Background
    val bgBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0A1124),
            Color(0xFF13223F),
            Color(0xFF0B1328)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgBrush)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // 1. ColorOS 17 Fluid Cloud Real Media Pill
            FluidCloudMediaPill(context = context)

            Spacer(modifier = Modifier.height(20.dp))

            // 2. Glanceable Clock Header
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = timeStr,
                    color = Color.White,
                    fontSize = 54.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1.5).sp
                )
                Text(
                    text = secStr,
                    color = Color(0xFF60A5FA),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 10.dp, start = 4.dp)
                )
            }
            Text(
                text = dateStr,
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (showSearch) {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search ${allApps.size} apps...", color = Color.White.copy(alpha = 0.5f)) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White.copy(alpha = 0.12f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 28.dp, bottom = 10.dp)
                )
            }

            // 3. Hardware-Accelerated App List with Gaussian Wave Displacement
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp, end = 36.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(displayedApps, key = { _, app -> app.packageName }) { _, app ->
                    WaveAppRow(
                        app = app,
                        isScrubbing = isScrubbing,
                        scrubberTouchY = scrubberTouchY,
                        onLaunch = {
                            context.packageManager.getLaunchIntentForPackage(app.packageName)?.let {
                                context.startActivity(it)
                            }
                        }
                    )
                }
            }
        }

        // 4. Right-Edge A–Z Niagara Wave Rail
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(0.68f)
                .width(34.dp)
                .padding(end = 4.dp)
                .onGloballyPositioned { coords ->
                    railTopY = coords.positionInRoot().y
                    railHeight = coords.size.height.toFloat().coerceAtLeast(1f)
                }
                .pointerInput(displayedApps) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            isScrubbing = true
                            scrubberTouchY = railTopY + offset.y
                        },
                        onDragEnd = {
                            isScrubbing = false
                            activeLetter = null
                            scrubberTouchY = -1000f
                        },
                        onDragCancel = {
                            isScrubbing = false
                            activeLetter = null
                            scrubberTouchY = -1000f
                        },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            val localY = change.position.y.coerceIn(0f, railHeight)
                            scrubberTouchY = railTopY + localY
                            val idx = ((localY / railHeight) * alphabet.size).toInt().coerceIn(0, alphabet.lastIndex)
                            val letter = alphabet[idx]
                            if (letter != activeLetter) {
                                activeLetter = letter
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                val targetIndex = displayedApps.indexOfFirst { it.firstLetter >= letter }
                                if (targetIndex >= 0) {
                                    coroutineScope.launch {
                                        listState.scrollToItem(targetIndex)
                                    }
                                }
                            }
                        }
                    )
                }
        ) {
            alphabet.forEach { letter ->
                val isSelected = letter == activeLetter
                Text(
                    text = letter.toString(),
                    color = if (isSelected) Color(0xFF60A5FA) else Color.White.copy(alpha = 0.55f),
                    fontSize = if (isSelected) 13.sp else 10.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }

        // Floating Letter Bubble during Scrub
        if (isScrubbing && activeLetter != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E3A8A).copy(alpha = 0.85f))
                    .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
            ) {
                Text(
                    text = activeLetter.toString(),
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 5. ColorOS 17 Floating Bottom Pill Bar
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp)
                .width(240.dp)
                .height(54.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF182644).copy(alpha = 0.88f))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(28.dp))
        ) {
            Text(
                text = "Home",
                color = if (!showSearch) Color(0xFF60A5FA) else Color.White.copy(alpha = 0.7f),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier.clickable {
                    showSearch = false
                    searchQuery = ""
                    coroutineScope.launch { listState.animateScrollToItem(0) }
                }
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(20.dp)
                    .background(Color.White.copy(alpha = 0.15f))
            )
            Text(
                text = if (showSearch) "Close" else "Search",
                color = if (showSearch) Color(0xFF60A5FA) else Color.White.copy(alpha = 0.7f),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier.clickable {
                    showSearch = !showSearch
                    if (!showSearch) searchQuery = ""
                }
            )
        }
    }
}

@Composable
fun WaveAppRow(
    app: AppEntry,
    isScrubbing: Boolean,
    scrubberTouchY: Float,
    onLaunch: () -> Unit
) {
    var rowCenterY by remember { mutableFloatStateOf(0f) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Gaussian bell curve displacement relative to thumb Y
    val targetWave = if (isScrubbing && rowCenterY > 0f) {
        val dy = scrubberTouchY - rowCenterY
        exp(-(dy * dy) / (2.0 * 140.0.pow(2))).toFloat()
    } else 0f

    val smoothWave by animateFloatAsState(
        targetValue = targetWave,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
        label = "wave"
    )

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium),
        label = "press"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                rowCenterY = coords.positionInRoot().y + (coords.size.height / 2f)
            }
            .graphicsLayer {
                translationX = smoothWave * 95f
                val s = (1f + smoothWave * 0.14f) * pressScale
                scaleX = s
                scaleY = s
            }
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.06f + smoothWave * 0.10f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.10f + smoothWave * 0.25f),
                shape = RoundedCornerShape(22.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null) { onLaunch() }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Image(
            bitmap = app.icon,
            contentDescription = app.label,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = app.label,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun FluidCloudMediaPill(context: Context) {
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var isPlaying by remember { mutableStateOf(audioManager.isMusicActive) }

    LaunchedEffect(Unit) {
        while (true) {
            isPlaying = audioManager.isMusicActive
            delay(1000L)
        }
    }

    fun sendMediaKey(keyCode: Int) {
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        isPlaying = !isPlaying
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Color(0xFF162442).copy(alpha = 0.85f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(26.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column {
            Text(
                text = "FLUID CLOUD • AUDIO",
                color = Color(0xFF60A5FA),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Text(
                text = if (isPlaying) "System Media Playing" else "Media Idle",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MediaButton("⏮") { sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
            MediaButton(if (isPlaying) "⏸" else "▶") { sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }
            MediaButton("⏭") { sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT) }
        }
    }
}

@Composable
fun MediaButton(symbol: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f))
            .clickable { onClick() }
    ) {
        Text(text = symbol, color = Color.White, fontSize = 14.sp)
    }
}

private fun loadInstalledApps(context: Context): List<AppEntry> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN, null).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }
    return pm.queryIntentActivities(intent, 0)
        .filter { it.activityInfo.packageName != context.packageName }
        .map { resolveInfo ->
            val label = resolveInfo.loadLabel(pm).toString()
            val iconBitmap = drawableToBitmap(resolveInfo.loadIcon(pm)).asImageBitmap()
            val firstChar = label.firstOrNull()?.uppercaseChar()?.let {
                if (it in 'A'..'Z') it else '#'
            } ?: '#'
            AppEntry(
                label = label,
                packageName = resolveInfo.activityInfo.packageName,
                icon = iconBitmap,
                firstLetter = firstChar
            )
        }
        .sortedBy { it.label.lowercase(Locale.getDefault()) }
}

private fun drawableToBitmap(drawable: Drawable): Bitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        return drawable.bitmap
    }
    val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: 96
    val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: 96
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}
