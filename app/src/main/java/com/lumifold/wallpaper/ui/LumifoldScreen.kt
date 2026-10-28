package com.lumifold.wallpaper.ui

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import android.content.Intent
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.lumifold.wallpaper.R
import com.lumifold.wallpaper.model.OpeningEffect
import com.lumifold.wallpaper.model.WallpaperCategory
import com.lumifold.wallpaper.model.WallpaperItem
import com.lumifold.wallpaper.model.WallpaperRepository
import com.lumifold.wallpaper.model.WallpaperSource
import android.graphics.Shader
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.lumifold.wallpaper.render.ShaderSnapshots
import com.lumifold.wallpaper.render.SurfaceSpec
import com.lumifold.wallpaper.render.WallpaperShader
import com.lumifold.wallpaper.render.VideoPreviewSlots
import com.lumifold.wallpaper.service.FoldWallpaperService
import com.lumifold.wallpaper.service.FoldAccessibilityService
import com.lumifold.wallpaper.service.MotionPolicy
import com.lumifold.wallpaper.service.VideoCache
import androidx.compose.ui.draw.alpha
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.roundToInt

internal const val COVER_AWAKE_HANDOFF = 90f
internal const val STOCK_HANDOFF = 8f

@Composable
fun LumifoldScreen(
    hingeAngle: FloatState,
    hasHingeSensor: Boolean,
    wallpaperActive: Boolean,
    refreshKey: Int,
    onOpenPicker: () -> Unit
) {
    val context = LocalContext.current
    var activeId by rememberSaveable { mutableStateOf(WallpaperRepository.getActiveWallpaper(context).id) }
    var effect by remember { mutableStateOf(WallpaperRepository.getOpeningEffect(context)) }
    var intensity by remember { mutableFloatStateOf(WallpaperRepository.getIntensity(context)) }
    // Re-read on resume: the Quick Settings tiles can change these while the app is away.
    var motion by remember(refreshKey) { mutableStateOf(WallpaperRepository.isDynamicMotionEnabled(context)) }
    var foldEffect by remember(refreshKey) { mutableStateOf(WallpaperRepository.isFoldEffectEnabled(context)) }
    var symmetricInner by remember { mutableStateOf(WallpaperRepository.isSymmetricInner(context)) }
    var fullSwipe by remember { mutableStateOf(WallpaperRepository.isFullSwipe(context)) }
    var pauseOnSaver by remember { mutableStateOf(WallpaperRepository.isPauseOnSaver(context)) }
    val pause = remember(refreshKey, pauseOnSaver) { MotionPolicy.currentPause(context) }
    val gridState = rememberLazyGridState()
    LaunchedEffect(gridState.isScrollInProgress) {
        VideoPreviewSlots.setScrolling(gridState.isScrollInProgress)
    }
    DisposableEffect(gridState) {
        onDispose { VideoPreviewSlots.setScrolling(false) }
    }
    var category by rememberSaveable { mutableStateOf<WallpaperCategory?>(null) }
    var liveOnly by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var lastDetailId by remember { mutableStateOf<String?>(null) }
    var backProgress by remember { mutableFloatStateOf(0f) }

    val measured = remember(refreshKey) {
        WallpaperRepository.getMeasuredOpenSwitch(context) to WallpaperRepository.getMeasuredCloseSwitch(context)
    }
    val active = WallpaperRepository.resolveWallpaper(activeId)
    val items = WallpaperRepository.getWallpapersByCategory(category, liveOnly)
    // The cover hold is built in whenever the fold effect is on.
    val handoff = if (foldEffect) COVER_AWAKE_HANDOFF else STOCK_HANDOFF
    if (detailId != null) lastDetailId = detailId

    PredictiveBackHandler(enabled = detailId != null || showSettings) { events: Flow<BackEventCompat> ->
        try {
            events.collect { backProgress = it.progress }
            if (detailId != null) detailId = null else showSettings = false
        } catch (e: CancellationException) {
            backProgress = 0f
        }
    }
    LaunchedEffect(detailId) { if (detailId == null) backProgress = 0f }
    val preview: @Composable (Modifier) -> Unit = { modifier ->
        PreviewCard(
            modifier = modifier,
            item = active,
            effect = effect,
            intensity = intensity,
            motion = motion,
            foldEffect = foldEffect,
            visible = detailId == null
        )
    }

    val quickToggles: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickToggles(
                foldEffect = foldEffect,
                onFoldEffect = {
                    foldEffect = it
                    WallpaperRepository.setFoldEffectEnabled(context, it)
                },
                motion = motion,
                onMotion = {
                    motion = it
                    WallpaperRepository.setDynamicMotionEnabled(context, it)
                },
                pause = pause,
                fullSwipe = fullSwipe,
                onFullSwipe = {
                    fullSwipe = it
                    WallpaperRepository.setFullSwipe(context, it)
                },
                symmetricInner = symmetricInner,
                onSymmetricInner = {
                    symmetricInner = it
                    WallpaperRepository.setSymmetricInner(context, it)
                }
            )
        }
    }

    val controls: LazyGridScope.(effectColumns: Int) -> Unit = { effectColumns ->
        fullSpan {
            SectionTitle(
                "Opening effect",
                if (foldEffect) "${OpeningEffect.entries.size} effects, driven by the real hinge angle on both screens"
                else "Fold effect is off. The wallpaper shows without any opening effect."
            )
        }
        fullSpan {
            Box(Modifier.alpha(if (foldEffect) 1f else 0.4f)) {
                EffectGrid(effect, effectColumns) {
                    effect = it
                    WallpaperRepository.setOpeningEffect(context, it)
                    if (!foldEffect) {
                        foldEffect = true
                        WallpaperRepository.setFoldEffectEnabled(context, true)
                    }
                }
            }
        }
        fullSpan {
            SectionTitle("Wallpapers", "${items.size} designs. Tap one to preview it.")
        }
        fullSpan {
            CategoryChips(category) { category = it }
        }
        fullSpan {
            KindChips(liveOnly) { liveOnly = it }
        }
        items(items, key = { it.id }) { item ->
            WallpaperTile(item = item, selected = item.id == activeId, onClick = { detailId = item.id })
        }
        fullSpan { Spacer(Modifier.height(8.dp)) }
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            val wide = maxWidth >= 720.dp
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.weight(0.46f).fillMaxHeight().padding(start = 20.dp, top = 12.dp, bottom = 16.dp)
                    ) {
                        Header(hingeAngle, hasHingeSensor) { showSettings = true }
                        Spacer(Modifier.height(12.dp))
                        quickToggles()
                        Spacer(Modifier.height(12.dp))
                        preview(Modifier.weight(1f).fillMaxWidth())
                        Spacer(Modifier.height(16.dp))
                        ApplyBar(wallpaperActive, onOpenPicker)
                    }
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(150.dp),
                        modifier = Modifier.weight(0.54f).fillMaxHeight(),
                        contentPadding = PaddingValues(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) { controls(4) }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(150.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        fullSpan { Header(hingeAngle, hasHingeSensor) { showSettings = true } }
                        fullSpan { quickToggles() }
                        fullSpan { preview(Modifier.fillMaxWidth().height(460.dp)) }
                        controls(3)
                    }
                    ApplyBar(wallpaperActive, onOpenPicker, Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                }
            }

            AnimatedVisibility(
                visible = showSettings && detailId == null,
                enter = slideInHorizontally { it / 3 } + fadeIn(),
                exit = slideOutHorizontally { it / 3 } + fadeOut()
            ) {
                SettingsPage(
                    onBack = { showSettings = false },
                    effect = effect,
                    intensity = intensity,
                    onIntensity = { intensity = it },
                    onIntensityDone = { WallpaperRepository.setIntensity(context, intensity) },
                    hingeAngle = hingeAngle,
                    hasHingeSensor = hasHingeSensor,
                    measuredOpen = measured.first,
                    measuredClose = measured.second,
                    pauseOnSaver = pauseOnSaver,
                    onPauseOnSaver = {
                        pauseOnSaver = it
                        WallpaperRepository.setPauseOnSaver(context, it)
                    }
                )
            }

            AnimatedVisibility(
                visible = detailId != null,
                enter = slideInHorizontally { it / 3 } + fadeIn(),
                exit = slideOutHorizontally { it / 3 } + fadeOut()
            ) {
                val item = WallpaperRepository.resolveWallpaper(lastDetailId)
                WallpaperDetailScreen(
                    item = item,
                    initialMotion = motion,
                    effect = effect,
                    intensity = intensity,
                    foldEffect = foldEffect,
                    hingeAngle = hingeAngle,
                    isApplied = wallpaperActive && item.id == activeId,
                    backProgress = backProgress,
                    onBack = { detailId = null },
                    onApply = { dynamic ->
                        WallpaperRepository.setActiveWallpaper(context, item.id)
                        WallpaperRepository.setDynamicMotionEnabled(context, dynamic)
                        activeId = item.id
                        motion = dynamic
                        detailId = null
                        if (wallpaperActive) {
                            Toast.makeText(context, "${item.title} applied", Toast.LENGTH_SHORT).show()
                        } else {
                            onOpenPicker()
                        }
                    }
                )
            }
        }
    }
}

private fun LazyGridScope.fullSpan(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun Header(hingeAngle: FloatState, hasHingeSensor: Boolean, onSettings: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Image(
            painter = painterResource(R.drawable.app_icon_art),
            contentDescription = null,
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(13.dp))
        )
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text("Lumifold", style = MaterialTheme.typography.titleLarge)
            Text(
                "Hinge-driven wallpapers for foldables",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onSettings) {
            Icon(painterResource(R.drawable.ic_settings_gear), contentDescription = "Advanced settings")
        }
        if (hasHingeSensor) HingePill(hingeAngle)
    }
}

@Composable
private fun HingePill(hingeAngle: FloatState) {
    val angle = hingeAngle.floatValue
    if (angle.isNaN()) return
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.secondary, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text("Hinge ${angle.roundToInt()}\u00B0", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * The opening model, playing the selected effect open and closed.
 */
@Composable
private fun PreviewCard(
    modifier: Modifier,
    item: WallpaperItem,
    effect: OpeningEffect,
    intensity: Float,
    motion: Boolean,
    foldEffect: Boolean,
    visible: Boolean
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.radialGradient(listOf(Color(0xFF1A2036), Color(0xFF0C0E14)), radius = 900f))
        ) {
            AndroidView(
                factory = { ctx ->
                    FoldPreviewView(ctx).apply {
                        flat = false
                        handleTouches = false
                        setAutoPlay(true)
                    }
                },
                update = { view ->
                    view.flat = false
                    view.handleTouches = false
                    if (!view.isAutoPlaying) view.setAutoPlay(true)
                    view.setWallpaper(item)
                    view.setEffect(effect, intensity, motion, foldEffect)
                    view.visibility = if (visible) android.view.View.VISIBLE else android.view.View.INVISIBLE
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun AngleReadout(angle: FloatState, handoff: Float, modifier: Modifier) {
    val a = angle.floatValue
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            phaseLabel(a, handoff),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text("${a.roundToInt()}\u00B0", style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun AngleSlider(angle: FloatState, onScrub: (Float) -> Unit, modifier: Modifier) {
    Slider(
        value = angle.floatValue,
        onValueChange = onScrub,
        valueRange = 0f..180f,
        modifier = modifier,
        colors = SliderDefaults.colors(inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest)
    )
}

internal fun phaseLabel(angle: Float, handoff: Float): String = when {
    angle < 1f -> "Closed \u00B7 cover display"
    angle < handoff -> "Opening \u00B7 cover display"
    angle < 179f -> "Opening \u00B7 inner display"
    else -> "Open \u00B7 inner display"
}

@Composable
private fun SettingsPage(
    onBack: () -> Unit,
    effect: OpeningEffect,
    intensity: Float,
    onIntensity: (Float) -> Unit,
    onIntensityDone: () -> Unit,
    hingeAngle: FloatState,
    hasHingeSensor: Boolean,
    measuredOpen: Float,
    measuredClose: Float,
    pauseOnSaver: Boolean,
    onPauseOnSaver: (Boolean) -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 0.dp)) { Text("Back") }
                Text("Advanced settings", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp))
            }
            TuningCard(
                effect, intensity, onIntensity, onIntensityDone,
                hingeAngle, hasHingeSensor, measuredOpen, measuredClose,
                pauseOnSaver, onPauseOnSaver
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(Modifier.padding(top = 18.dp, bottom = 2.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EffectGrid(selected: OpeningEffect, columns: Int, onSelect: (OpeningEffect) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val all = OpeningEffect.entries
    val featured = all.take(OpeningEffect.FEATURED_COUNT)
    val visible = when {
        expanded -> all
        selected in featured -> featured
        else -> featured.dropLast(1) + selected
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        visible.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { e ->
                    EffectTile(e, e == selected, Modifier.weight(1f)) { onSelect(e) }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        OutlinedButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
            Text(if (expanded) "Show fewer effects" else "Show all ${all.size} effects")
        }
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(selected.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Text(selected.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private val EFFECT_SAMPLE: WallpaperItem by lazy { WallpaperRepository.resolveWallpaper("duo_ribbons") }
private const val EFFECT_SAMPLE_ANGLE = 125f

/** Fine dark lines on white make blur and shading easy to compare across strengths. */
private val STRENGTH_SAMPLE: WallpaperItem by lazy { WallpaperRepository.resolveWallpaper("contour_ink") }
private const val STRENGTH_SAMPLE_ANGLE = 55f

/**
 * Shows a shader rendered once into a cached bitmap at this composable's pixel size. The shader is
 * compiled and rendered on [ShaderSnapshots.dispatcher], never on the main thread.
 */
@Composable
private fun ShaderImage(key: String, modifier: Modifier, shader: (width: Int, height: Int) -> Shader) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val sizedKey = "$key@${size.width}x${size.height}"
    // Not keyed, so the previous image stays up until the image for a new size is ready.
    var bitmap by remember { mutableStateOf(ShaderSnapshots.cached(sizedKey)?.asImageBitmap()) }
    LaunchedEffect(sizedKey) {
        val cached = ShaderSnapshots.cached(sizedKey)
        if (cached != null) {
            bitmap = cached.asImageBitmap()
        } else if (size.width > 0 && size.height > 0) {
            bitmap = withContext(ShaderSnapshots.dispatcher) {
                ShaderSnapshots.getOrRender(sizedKey, size.width, size.height) { shader(size.width, size.height) }
            }.asImageBitmap()
        }
    }
    Box(modifier.onSizeChanged { size = it }) {
        bitmap?.let { Image(it, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize()) }
    }
}

private fun boundShader(
    item: WallpaperItem,
    w: Int,
    h: Int,
    angleDeg: Float,
    effect: OpeningEffect,
    intensity: Float,
    inner: Boolean = true
): Shader {
    val surfH = if (inner) h / (w * 0.5f) else h / w.toFloat()
    val ws = WallpaperShader.create(item)
    ws.bind(
        SurfaceSpec(w, h, 0, inner, surfH, surfH),
        (angleDeg * PI / 180.0).toFloat(), FoldWallpaperService.STILL_TIME, effect, intensity, false
    )
    return ws.shader
}

@Composable
private fun EffectTile(effect: OpeningEffect, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .aspectRatio(0.92f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
    ) {
        ShaderImage("effect:${effect.id}", Modifier.fillMaxSize()) { w, h ->
            boundShader(EFFECT_SAMPLE, w, h, EFFECT_SAMPLE_ANGLE, effect, 1.2f)
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color(0xD9000000))))
        Text(
            effect.title,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)
        )
        if (selected) {
            Box(Modifier.fillMaxSize().border(2.5.dp, MaterialTheme.colorScheme.primary, shape))
            CheckBadge(Modifier.align(Alignment.TopEnd).padding(8.dp))
        }
    }
}

@Composable
private fun QuickToggles(
    foldEffect: Boolean,
    onFoldEffect: (Boolean) -> Unit,
    motion: Boolean,
    onMotion: (Boolean) -> Unit,
    pause: MotionPolicy.Pause,
    fullSwipe: Boolean,
    onFullSwipe: (Boolean) -> Unit,
    symmetricInner: Boolean,
    onSymmetricInner: (Boolean) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        QuickToggle(
            title = "Fold effect",
            status = if (foldEffect) "On" else "Off, wallpaper only",
            checked = foldEffect,
            onChange = onFoldEffect,
            modifier = Modifier.weight(1f)
        )
        QuickToggle(
            title = "Live effect",
            status = when {
                !motion -> "Off, still images"
                pause == MotionPolicy.Pause.BATTERY_SAVER -> "Paused by Battery Saver"
                else -> "On"
            },
            checked = motion,
            onChange = onMotion,
            modifier = Modifier.weight(1f)
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        QuickToggle(
            title = "Full swipe",
            status = if (fullSwipe) "Sweeps the inner screen" else "Moving half only",
            checked = fullSwipe,
            onChange = onFullSwipe,
            modifier = Modifier.weight(1f)
        )
        QuickToggle(
            title = "Mirror effect",
            status = if (symmetricInner) "Both halves move" else "Left half only",
            checked = symmetricInner,
            onChange = onSymmetricInner,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun QuickToggle(title: String, status: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest))
        }
    }
}

@Composable
private fun TuningCard(
    effect: OpeningEffect,
    intensity: Float,
    onIntensity: (Float) -> Unit,
    onIntensityDone: () -> Unit,
    hingeAngle: FloatState,
    hasHingeSensor: Boolean,
    measuredOpen: Float,
    measuredClose: Float,
    pauseOnSaver: Boolean,
    onPauseOnSaver: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Effect strength", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text("${(intensity * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(
                        "How much blur, shading and distortion the opening effect applies. The sample shows ${effect.title} on the cover screen, partly open.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = intensity,
                        onValueChange = onIntensity,
                        onValueChangeFinished = onIntensityDone,
                        valueRange = WallpaperRepository.MIN_INTENSITY..WallpaperRepository.MAX_INTENSITY,
                        colors = SliderDefaults.colors(inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                    )
                }
                val step = (intensity * 10).roundToInt()
                Box(Modifier.width(112.dp).aspectRatio(0.92f).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    ShaderImage("strength:${effect.id}:$step", Modifier.fillMaxSize()) { w, h ->
                        boundShader(STRENGTH_SAMPLE, w, h, STRENGTH_SAMPLE_ANGLE, effect, step / 10f, inner = false)
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 6.dp))
            SwitchRow(
                title = "Pause on Battery Saver",
                body = if (pauseOnSaver) "On. The live effect pauses while Android Battery Saver is on."
                    else "Off. The live effect keeps running with Battery Saver on.",
                checked = pauseOnSaver,
                onChange = onPauseOnSaver
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 6.dp))
            AppGlassRow()
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 6.dp))
            FoldSettingRow()
            Spacer(Modifier.height(10.dp))
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("DIAGNOSTICS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    HingeDiagnostic(hingeAngle, hasHingeSensor)
                    DiagnosticLine("Screen switch when opening", formatMeasured(measuredOpen))
                    DiagnosticLine("Screen switch when closing", formatMeasured(measuredClose))
                }
            }
        }
    }
}

@Composable
private fun FoldSettingRow() {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("Android fold settings", style = MaterialTheme.typography.titleSmall)
        Text(
            "Set Continue using apps on fold to Always. That turns the cover on as you close, instead of waiting behind the lock screen. Android swaps panels at about 90 degrees when opening and below 45 when closing; apps cannot change these angles.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(
            onClick = {
                runCatching { context.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            },
            contentPadding = PaddingValues(horizontal = 0.dp)
        ) { Text("Open Display settings") }
    }
}

@Composable
private fun AppGlassRow() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    fun accessibilityEnabled(): Boolean = context.getSystemService(AccessibilityManager::class.java)
        ?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        ?.any { it.resolveInfo.serviceInfo.name == FoldAccessibilityService::class.java.name &&
            it.resolveInfo.serviceInfo.packageName == context.packageName } == true
    var allowed by remember(context) { mutableStateOf(Settings.canDrawOverlays(context)) }
    var accessible by remember(context) { mutableStateOf(accessibilityEnabled()) }
    DisposableEffect(lifecycle, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                allowed = Settings.canDrawOverlays(context)
                accessible = accessibilityEnabled()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    fun openSettings(primary: Intent, fallback: Intent) {
        // Per-app routes are available on Pixel; retain public routes for other Android builds.
        runCatching { context.startActivity(primary.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .recoverCatching { context.startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { Toast.makeText(context, "Open Android Settings to manage this permission.", Toast.LENGTH_LONG).show() }
    }
    Column(Modifier.fillMaxWidth()) {
        SwitchRow(
            title = "Allow display over other apps",
            body = if (allowed) {
                "Allowed. Draws the fold effect over an open app. Tap, then select Lumifold in the Android list to manage it."
            } else {
                "Not allowed. Tap, then select Lumifold in the Android list to allow the fold effect over an open app."
            },
            checked = allowed,
            onChange = {
                val appUri = "package:${context.packageName}".toUri()
                openSettings(Intent("android.settings.MANAGE_APP_OVERLAY_PERMISSION", appUri),
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, appUri))
            }
        )
        SwitchRow(
            title = "Lumifold accessibility",
            body = if (accessible) "Enabled. Tap, then select Lumifold in Android Accessibility to manage this service."
                else "Disabled. Tap, then select Lumifold in Android Accessibility to enable effects over apps.",
            checked = accessible,
            onChange = {
                runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                    .onFailure { Toast.makeText(context, "Open Android Settings, then Accessibility and Lumifold.", Toast.LENGTH_LONG).show() }
            }
        )
    }
}

@Composable
private fun HingeDiagnostic(hingeAngle: FloatState, hasHingeSensor: Boolean) {
    val angle = hingeAngle.floatValue
    DiagnosticLine(
        "Hinge sensor",
        when {
            !hasHingeSensor -> "Not available on this device"
            angle.isNaN() -> "Waiting for data"
            else -> "${angle.roundToInt()}\u00B0"
        }
    )
}

private fun formatMeasured(angle: Float) =
    if (angle.isNaN()) "Not measured yet" else "${angle.roundToInt()}\u00B0"

@Composable
private fun DiagnosticLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SwitchRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 6.dp)
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest)
        )
    }
}

@Composable
private fun KindChips(liveOnly: Boolean?, onSelect: (Boolean?) -> Unit) {
    val options = listOf(null to "All", true to "Live", false to "Still")
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
    ) {
        Row(Modifier.padding(4.dp)) {
            options.forEach { (value, label) ->
                val selected = liveOnly == value
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) MaterialTheme.colorScheme.inverseSurface else Color.Transparent)
                        .clickable { onSelect(value) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) MaterialTheme.colorScheme.inverseOnSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryChips(selected: WallpaperCategory?, onSelect: (WallpaperCategory?) -> Unit) {
    val options = listOf<WallpaperCategory?>(null) + WallpaperCategory.entries
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options) { c ->
            FilterChip(
                selected = c == selected,
                onClick = { onSelect(c) },
                label = { Text(c?.label ?: "All") },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    }
}

@Composable
private fun WallpaperTile(item: WallpaperItem, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier
            .aspectRatio(0.9f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
    ) {
        WallpaperThumbnail(item, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color(0xCC000000))))
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.category.label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
        }
        Text(
            if (item.isLive) "Live" else "Still",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .background(Color(0x99000000), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        )
        if (selected) {
            Box(Modifier.fillMaxSize().border(2.5.dp, MaterialTheme.colorScheme.primary, shape))
            CheckBadge(Modifier.align(Alignment.TopEnd).padding(10.dp))
        }
    }
}

@Composable
private fun CheckBadge(modifier: Modifier) {
    Box(
        modifier.size(24.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(CheckIcon, contentDescription = "Selected", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
    }
}

@Composable
internal fun WallpaperThumbnail(item: WallpaperItem, modifier: Modifier) {
    AndroidView(
        factory = {
            FoldPreviewView(it).apply {
                flat = true
                handleTouches = false
            }
        },
        update = { view ->
            view.flat = true
            view.handleTouches = false
            view.setAutoPlay(false)
            view.setWallpaper(item)
            view.setEffect(OpeningEffect.WINDOW, 1f, item.isLive, foldEffect = false)
            view.refreshVideo()
        },
        modifier = modifier
    )
}

@Composable
private fun ApplyBar(active: Boolean, onOpenPicker: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars)) {
        if (active) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.size(8.dp).background(Color(0xFF4ADE80), CircleShape))
                Text(
                    "Live wallpaper active. Changes apply instantly.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(start = 10.dp)
                )
                TextButton(onClick = onOpenPicker) { Text("Reapply") }
            }
        } else {
            Button(
                onClick = onOpenPicker,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Set as wallpaper", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

internal val PlayIcon: ImageVector = ImageVector.Builder("Play", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = SolidColor(Color.White)) {
        moveTo(8f, 5.5f); lineTo(19f, 12f); lineTo(8f, 18.5f); close()
    }
}.build()

internal val PauseIcon: ImageVector = ImageVector.Builder("Pause", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = SolidColor(Color.White)) {
        moveTo(7f, 5.5f); lineTo(10.5f, 5.5f); lineTo(10.5f, 18.5f); lineTo(7f, 18.5f); close()
        moveTo(13.5f, 5.5f); lineTo(17f, 5.5f); lineTo(17f, 18.5f); lineTo(13.5f, 18.5f); close()
    }
}.build()

internal val CheckIcon: ImageVector = ImageVector.Builder("Check", 24.dp, 24.dp, 24f, 24f).apply {
    path(stroke = SolidColor(Color.White), strokeLineWidth = 2.6f) {
        moveTo(5.5f, 12.5f); lineTo(10f, 17f); lineTo(18.5f, 7.5f)
    }
}.build()

internal val BackIcon: ImageVector = ImageVector.Builder("Back", 24.dp, 24.dp, 24f, 24f).apply {
    path(stroke = SolidColor(Color.White), strokeLineWidth = 2.2f) {
        moveTo(19f, 12f); lineTo(5.5f, 12f)
        moveTo(11f, 6f); lineTo(5f, 12f); lineTo(11f, 18f)
    }
}.build()
