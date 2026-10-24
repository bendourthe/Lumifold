package com.ambientfold.wallpaper.ui

import android.graphics.BitmapFactory
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
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
import com.ambientfold.wallpaper.R
import com.ambientfold.wallpaper.model.OpeningEffect
import com.ambientfold.wallpaper.model.WallpaperCategory
import com.ambientfold.wallpaper.model.WallpaperItem
import com.ambientfold.wallpaper.model.WallpaperRepository
import com.ambientfold.wallpaper.model.WallpaperSource
import com.ambientfold.wallpaper.render.SurfaceSpec
import com.ambientfold.wallpaper.render.WallpaperShader
import com.ambientfold.wallpaper.service.FoldWallpaperService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.roundToInt

private const val COVER_AWAKE_HANDOFF = 90f
private const val STOCK_HANDOFF = 8f

@Composable
fun DuoBlurScreen(
    hingeAngle: FloatState,
    hasHingeSensor: Boolean,
    wallpaperActive: Boolean,
    refreshKey: Int,
    onApply: () -> Unit
) {
    val context = LocalContext.current
    var selectedId by rememberSaveable { mutableStateOf(WallpaperRepository.getActiveWallpaper(context).id) }
    var effect by remember { mutableStateOf(WallpaperRepository.getOpeningEffect(context)) }
    var intensity by remember { mutableFloatStateOf(WallpaperRepository.getIntensity(context)) }
    var motion by remember { mutableStateOf(WallpaperRepository.isDynamicMotionEnabled(context)) }
    var coverAwake by remember { mutableStateOf(WallpaperRepository.isCoverAwakeEnabled(context)) }
    var category by rememberSaveable { mutableStateOf<WallpaperCategory?>(null) }
    // Updated every animation frame; read only by the small composables that display it.
    val previewAngle = remember { mutableFloatStateOf(0f) }
    var autoPlay by remember { mutableStateOf(true) }
    var followHinge by remember { mutableStateOf(false) }
    var previewView by remember { mutableStateOf<FoldPreviewView?>(null) }

    val measured = remember(refreshKey) {
        WallpaperRepository.getMeasuredOpenSwitch(context) to WallpaperRepository.getMeasuredCloseSwitch(context)
    }
    val selected = WallpaperRepository.resolveWallpaper(selectedId)
    val items = WallpaperRepository.getWallpapersByCategory(category)

    LaunchedEffect(followHinge, previewView) {
        if (!followHinge) return@LaunchedEffect
        snapshotFlow { hingeAngle.floatValue }.collect { a ->
            if (!a.isNaN()) previewView?.setAngleManually(a)
        }
    }

    val preview: @Composable (Modifier) -> Unit = { modifier ->
        PreviewCard(
            modifier = modifier,
            item = selected,
            effect = effect,
            intensity = intensity,
            motion = motion,
            handoff = if (coverAwake) COVER_AWAKE_HANDOFF else STOCK_HANDOFF,
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

    val controls: LazyGridScope.() -> Unit = {
        fullSpan {
            SectionTitle("Opening effect", "Driven by the real hinge angle on both screens")
        }
        fullSpan {
            EffectPicker(effect) {
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
                motion = motion,
                onMotion = {
                    motion = it
                    WallpaperRepository.setDynamicMotionEnabled(context, it)
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
            SectionTitle("Wallpapers", "${WallpaperRepository.wallpapers.size} designs. Live designs render at your screen's native resolution.")
        }
        fullSpan {
            CategoryChips(category) { category = it }
        }
        items(items, key = { it.id }) { item ->
            WallpaperTile(
                item = item,
                selected = item.id == selectedId,
                onClick = {
                    selectedId = item.id
                    WallpaperRepository.setActiveWallpaper(context, item.id)
                }
            )
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
                        ApplyBar(wallpaperActive, onApply)
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(150.dp),
                        modifier = Modifier.weight(0.54f).fillMaxHeight(),
                        contentPadding = PaddingValues(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        content = controls
                    )
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
                        fullSpan { preview(Modifier.fillMaxWidth().height(440.dp)) }
                        controls()
                    }
                    ApplyBar(wallpaperActive, onApply, Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                }
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
            Text("DuoBlur", style = MaterialTheme.typography.titleLarge)
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
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xFF1A2036), Color(0xFF0C0E14)),
                            radius = 900f
                        )
                    )
            ) {
                AndroidView(
                    factory = { ctx -> FoldPreviewView(ctx).also(onViewCreated) },
                    update = { view ->
                        view.setWallpaper(item)
                        view.setEffect(effect, intensity, motion)
                        view.handoffAngle = handoff
                    },
                    modifier = Modifier.fillMaxSize()
                )
                AngleReadout(angle, handoff, Modifier.fillMaxWidth().padding(16.dp))
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
                    FilterChip(
                        selected = followHinge,
                        onClick = onToggleFollow,
                        label = { Text("Follow hinge") }
                    )
                }
            }
        }
    }
}

@Composable
private fun AngleReadout(angle: FloatState, handoff: Float, modifier: Modifier) {
    val a = angle.floatValue
    Row(modifier, verticalAlignment = Alignment.Top) {
        Text(
            phaseLabel(a, handoff),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text("${a.roundToInt()}\u00B0", style = MaterialTheme.typography.headlineSmall)
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

private fun phaseLabel(angle: Float, handoff: Float): String = when {
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
private fun EffectPicker(selected: OpeningEffect, onSelect: (OpeningEffect) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(OpeningEffect.entries) { e ->
            val isSelected = e == selected
            Surface(
                onClick = { onSelect(e) },
                shape = RoundedCornerShape(20.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(
                    if (isSelected) 1.5.dp else 1.dp,
                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier.width(196.dp).height(124.dp)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        e.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        e.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun TuningCard(
    intensity: Float,
    onIntensity: (Float) -> Unit,
    motion: Boolean,
    onMotion: (Boolean) -> Unit,
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
                title = "Living motion",
                body = "Animates live designs and adds a slow drift to photos. Turn off to save battery; the fold effect still follows the hinge.",
                checked = motion,
                onChange = onMotion
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
        when (val source = item.source) {
            is WallpaperSource.Generative -> GenerativeThumbnail(item)
            is WallpaperSource.Photo -> PhotoThumbnail(source.resId)
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color(0xCC000000)))
        )
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.category.label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
        }
        if (item.isLive) {
            Text(
                "LIVE",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .background(Color(0x66000000), CircleShape)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
        if (selected) {
            Box(Modifier.fillMaxSize().border(2.5.dp, MaterialTheme.colorScheme.primary, shape))
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(24.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(CheckIcon, contentDescription = "Selected", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun GenerativeThumbnail(item: WallpaperItem) {
    val ws = remember(item.id) { WallpaperShader.create(item) }
    Box(
        Modifier.fillMaxSize().drawBehind {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@drawBehind
            val surfH = h / (w * 0.5f)
            ws.bind(
                SurfaceSpec(w.toInt(), h.toInt(), 0, true, surfH, surfH),
                PI.toFloat(), FoldWallpaperService.STILL_TIME, OpeningEffect.WINDOW, 1f, false
            )
            drawRect(ShaderBrush(ws.shader))
        }
    )
}

@Composable
private fun PhotoThumbnail(resId: Int) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, resId) {
        value = withContext(Dispatchers.IO) {
            val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
            BitmapFactory.decodeResource(context.resources, resId, opts)?.asImageBitmap()
        }
    }
    bitmap?.let {
        Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun ApplyBar(active: Boolean, onApply: () -> Unit, modifier: Modifier = Modifier) {
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
                TextButton(onClick = onApply) { Text("Reapply") }
            }
        } else {
            Button(
                onClick = onApply,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Set as wallpaper", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

private val PlayIcon: ImageVector = ImageVector.Builder("Play", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = SolidColor(Color.White)) {
        moveTo(8f, 5.5f); lineTo(19f, 12f); lineTo(8f, 18.5f); close()
    }
}.build()

private val PauseIcon: ImageVector = ImageVector.Builder("Pause", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = SolidColor(Color.White)) {
        moveTo(7f, 5.5f); lineTo(10.5f, 5.5f); lineTo(10.5f, 18.5f); lineTo(7f, 18.5f); close()
        moveTo(13.5f, 5.5f); lineTo(17f, 5.5f); lineTo(17f, 18.5f); lineTo(13.5f, 18.5f); close()
    }
}.build()

private val CheckIcon: ImageVector = ImageVector.Builder("Check", 24.dp, 24.dp, 24f, 24f).apply {
    path(stroke = SolidColor(Color.White), strokeLineWidth = 2.6f) {
        moveTo(5.5f, 12.5f); lineTo(10f, 17f); lineTo(18.5f, 7.5f)
    }
}.build()
