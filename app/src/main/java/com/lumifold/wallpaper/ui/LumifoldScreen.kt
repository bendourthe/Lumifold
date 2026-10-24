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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
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
import com.lumifold.wallpaper.service.FoldWallpaperService
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
    var motion by remember { mutableStateOf(WallpaperRepository.isDynamicMotionEnabled(context)) }
    var coverAwake by remember { mutableStateOf(WallpaperRepository.isCoverAwakeEnabled(context)) }
    var category by rememberSaveable { mutableStateOf<WallpaperCategory?>(null) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var lastDetailId by remember { mutableStateOf<String?>(null) }
    var backProgress by remember { mutableFloatStateOf(0f) }
    // Updated every animation frame; read only by the small composables that display it.
    val previewAngle = remember { mutableFloatStateOf(0f) }
    var autoPlay by remember { mutableStateOf(true) }
    var followHinge by remember { mutableStateOf(false) }
    var previewView by remember { mutableStateOf<FoldPreviewView?>(null) }

    val measured = remember(refreshKey) {
        WallpaperRepository.getMeasuredOpenSwitch(context) to WallpaperRepository.getMeasuredCloseSwitch(context)
    }
    val active = WallpaperRepository.resolveWallpaper(activeId)
    val items = WallpaperRepository.getWallpapersByCategory(category)
    val handoff = if (coverAwake) COVER_AWAKE_HANDOFF else STOCK_HANDOFF
    if (detailId != null) lastDetailId = detailId

    LaunchedEffect(followHinge, previewView) {
        if (!followHinge) return@LaunchedEffect
        snapshotFlow { hingeAngle.floatValue }.collect { a ->
            if (!a.isNaN()) previewView?.setAngleManually(a)
        }
    }

    PredictiveBackHandler(enabled = detailId != null) { events: Flow<BackEventCompat> ->
        try {
            events.collect { backProgress = it.progress }
            detailId = null
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
            handoff = handoff,
            visible = detailId == null,
            angle = previewAngle,
            autoPlay = autoPlay,
            followHinge = followHinge,
            canFollowHinge = hasHingeSensor,
            onViewCreated = { view ->
                previewView = view
                view.onAngleChanged = { previewAngle.floatValue = it }
                view.onAutoPlayChanged = { autoPlay = it }
            },
            onScrub = { followHinge = false; previewView?.setAngleManually(it) },
            onTogglePlay = {
                followHinge = false
                previewView?.setAutoPlay(!autoPlay)
            },
            onToggleFollow = { followHinge = !followHinge }
        )
    }

    val controls: LazyGridScope.(effectColumns: Int) -> Unit = { effectColumns ->
        fullSpan {
            SectionTitle("Opening effect", "${OpeningEffect.entries.size} effects, driven by the real hinge angle on both screens")
        }
        fullSpan {
            EffectGrid(effect, effectColumns) {
                effect = it
                WallpaperRepository.setOpeningEffect(context, it)
            }
        }
        fullSpan {
            TuningCard(
                intensity = intensity,
                onIntensity = {
                    intensity = it
                    WallpaperRepository.setIntensity(context, it)
                },
                coverAwake = coverAwake,
                onCoverAwake = {
                    coverAwake = it
                    WallpaperRepository.setCoverAwakeEnabled(context, it)
                },
                hingeAngle = hingeAngle,
                hasHingeSensor = hasHingeSensor,
                measuredOpen = measured.first,
                measuredClose = measured.second
            )
        }
        fullSpan {
            SectionTitle("Wallpapers", "${WallpaperRepository.wallpapers.size} designs. Tap one to preview it.")
        }
        fullSpan {
            CategoryChips(category) { category = it }
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
                        Header(hingeAngle, hasHingeSensor)
                        Spacer(Modifier.height(16.dp))
                        preview(Modifier.weight(1f).fillMaxWidth())
                        Spacer(Modifier.height(16.dp))
                        ApplyBar(wallpaperActive, onOpenPicker)
                    }
                    LazyVerticalGrid(
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
                        columns = GridCells.Adaptive(150.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        fullSpan { Header(hingeAngle, hasHingeSensor) }
                        fullSpan { preview(Modifier.fillMaxWidth().height(460.dp)) }
                        controls(3)
                    }
                    ApplyBar(wallpaperActive, onOpenPicker, Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                }
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
                    handoff = handoff,
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
private fun Header(hingeAngle: FloatState, hasHingeSensor: Boolean) {
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

@Composable
private fun PreviewCard(
    modifier: Modifier,
    item: WallpaperItem,
    effect: OpeningEffect,
    intensity: Float,
    motion: Boolean,
    handoff: Float,
    visible: Boolean,
    angle: FloatState,
    autoPlay: Boolean,
    followHinge: Boolean,
    canFollowHinge: Boolean,
    onViewCreated: (FoldPreviewView) -> Unit,
    onScrub: (Float) -> Unit,
    onTogglePlay: () -> Unit,
    onToggleFollow: () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column {
            AngleReadout(angle, handoff, Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 4.dp))
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Brush.radialGradient(listOf(Color(0xFF1A2036), Color(0xFF0C0E14)), radius = 900f))
            ) {
                AndroidView(
                    factory = { ctx -> FoldPreviewView(ctx).also(onViewCreated) },
                    update = { view ->
                        view.setWallpaper(item)
                        view.setEffect(effect, intensity, motion)
                        view.handoffAngle = handoff
                        view.visibility = if (visible) android.view.View.VISIBLE else android.view.View.INVISIBLE
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, top = 6.dp, bottom = 10.dp)
            ) {
                FilledTonalIconButton(onClick = onTogglePlay) {
                    Icon(if (autoPlay) PauseIcon else PlayIcon, contentDescription = if (autoPlay) "Pause" else "Play")
                }
                AngleSlider(angle, onScrub, Modifier.weight(1f).padding(horizontal = 8.dp))
                if (canFollowHinge) {
                    FilterChip(selected = followHinge, onClick = onToggleFollow, label = { Text("Follow hinge") })
                }
            }
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
private fun SectionTitle(title: String, subtitle: String) {
    Column(Modifier.padding(top = 18.dp, bottom = 2.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EffectGrid(selected: OpeningEffect, columns: Int, onSelect: (OpeningEffect) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OpeningEffect.entries.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { e ->
                    EffectTile(e, e == selected, Modifier.weight(1f)) { onSelect(e) }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
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

/**
 * Shows a shader rendered once into a cached bitmap at this composable's pixel size. The shader is
 * compiled and rendered on [ShaderSnapshots.dispatcher], never on the main thread.
 */
@Composable
private fun ShaderImage(key: String, modifier: Modifier, shader: (width: Int, height: Int) -> Shader) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val sizedKey = "$key@${size.width}x${size.height}"
    val bitmap by produceState(ShaderSnapshots.cached(sizedKey)?.asImageBitmap(), sizedKey) {
        if (size.width == 0 || size.height == 0 || value != null) return@produceState
        value = withContext(ShaderSnapshots.dispatcher) {
            ShaderSnapshots.getOrRender(sizedKey, size.width, size.height) { shader(size.width, size.height) }
        }.asImageBitmap()
    }
    Box(modifier.onSizeChanged { size = it }) {
        bitmap?.let { Image(it, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize()) }
    }
}

private fun boundShader(item: WallpaperItem, w: Int, h: Int, angleDeg: Float, effect: OpeningEffect, intensity: Float): Shader {
    val surfH = h / (w * 0.5f)
    val ws = WallpaperShader.create(item)
    ws.bind(
        SurfaceSpec(w, h, 0, true, surfH, surfH),
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
private fun TuningCard(
    intensity: Float,
    onIntensity: (Float) -> Unit,
    coverAwake: Boolean,
    onCoverAwake: (Boolean) -> Unit,
    hingeAngle: FloatState,
    hasHingeSensor: Boolean,
    measuredOpen: Float,
    measuredClose: Float
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Strength", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text("${(intensity * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = intensity,
                onValueChange = onIntensity,
                valueRange = WallpaperRepository.MIN_INTENSITY..WallpaperRepository.MAX_INTENSITY,
                colors = SliderDefaults.colors(inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest)
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 6.dp))
            SwitchRow(
                title = "Keep cover on while opening",
                body = "On Android 16 and later, keeps the cover display awake until about 90\u00B0 so the effect plays on it. Never holds the screen longer than your screen timeout.",
                checked = coverAwake,
                onChange = onCoverAwake
            )
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
            .aspectRatio(0.72f)
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
    when (val source = item.source) {
        is WallpaperSource.Generative -> GenerativeThumbnail(item, modifier)
        is WallpaperSource.Photo -> PhotoThumbnail(source.resId, modifier)
    }
}

@Composable
private fun GenerativeThumbnail(item: WallpaperItem, modifier: Modifier) {
    ShaderImage("wallpaper:${item.id}", modifier) { w, h ->
        boundShader(item, w, h, 180f, OpeningEffect.WINDOW, 1f)
    }
}

@Composable
private fun PhotoThumbnail(resId: Int, modifier: Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, resId) {
        value = withContext(Dispatchers.IO) {
            val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
            BitmapFactory.decodeResource(context.resources, resId, opts)?.asImageBitmap()
        }
    }
    bitmap?.let {
        Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
    }
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
