package com.lumifold.wallpaper.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.lumifold.wallpaper.model.MotionStyle
import com.lumifold.wallpaper.service.VideoCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.lumifold.wallpaper.model.OpeningEffect
import com.lumifold.wallpaper.model.WallpaperItem
import com.lumifold.wallpaper.model.WallpaperSource

@Composable
fun WallpaperDetailScreen(
    item: WallpaperItem,
    initialMotion: Boolean,
    effect: OpeningEffect,
    intensity: Float,
    foldEffect: Boolean,
    handoff: Float,
    isApplied: Boolean,
    backProgress: Float,
    onBack: () -> Unit,
    onApply: (dynamic: Boolean) -> Unit
) {
    var dynamic by remember(item.id) { mutableStateOf(initialMotion) }
    var view by remember(item.id) { mutableIntStateOf(0) }
    val context = LocalContext.current
    val clip = item.source as? WallpaperSource.Video
    var cached by remember(item.id) { mutableStateOf(VideoCache.isCached(context, item)) }
    var progress by remember(item.id) { mutableStateOf<Float?>(null) }
    var failed by remember(item.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val download: (then: () -> Unit) -> Unit = { then ->
        failed = false
        progress = 0f
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                VideoCache.download(context, item, isCancelled = { !isActive }) { p ->
                    scope.launch { progress = p }
                }
            }
            progress = null
            cached = ok
            failed = !ok
            if (ok) then()
        }
    }
    val shift = with(LocalDensity.current) { 72.dp.toPx() }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = backProgress * shift
                val s = 1f - 0.08f * backProgress
                scaleX = s
                scaleY = s
                alpha = 1f - 0.3f * backProgress
            }
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Column(Modifier.padding(top = 12.dp, bottom = 10.dp)) {
                Text(item.title, style = MaterialTheme.typography.headlineSmall)
                Text(
                    item.category.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.radialGradient(listOf(Color(0xFF1A2036), Color(0xFF0C0E14)), radius = 1100f))
                ) {
                    AndroidView(
                        factory = { ctx -> FoldPreviewView(ctx) },
                        update = { v ->
                            v.setWallpaper(item)
                            v.setEffect(effect, intensity, dynamic, foldEffect)
                            v.handoffAngle = handoff
                            v.flat = view == 0
                            if (cached) v.refreshVideo()
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            SegmentedToggle(listOf("Live view", "Opening"), view) { view = it }
            if (!item.isStill) {
                Spacer(Modifier.height(10.dp))
                SegmentedToggle(listOf("Dynamic", "Static"), if (dynamic) 0 else 1) { dynamic = it == 0 }
            }
            Text(
                when {
                    item.isStill -> "Still image: this wallpaper has no live motion. The opening effect still follows the hinge."
                    dynamic -> motionCaption(item)
                    else -> "Still image. The fold effect still follows the hinge."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(top = 14.dp, bottom = 12.dp)
            ) {
                FilledTonalIconButton(onClick = onBack, modifier = Modifier.size(56.dp)) {
                    Icon(BackIcon, contentDescription = "Back to wallpapers")
                }
                Spacer(Modifier.width(12.dp))
                val needsDownload = clip != null && !cached
                Button(
                    onClick = {
                        when {
                            progress != null -> Unit
                            needsDownload -> download { onApply(dynamic) }
                            else -> onApply(dynamic)
                        }
                    },
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.weight(1f).height(56.dp)
                ) {
                    val size = clip?.let { "%.0f MB".format(it.sizeMb) }
                    val label = when {
                        progress != null -> "Downloading ${((progress ?: 0f) * 100).toInt()}%"
                        needsDownload && failed -> "Retry download ($size)"
                        needsDownload -> "Download and apply ($size)"
                        isApplied -> "Update wallpaper"
                        else -> "Apply"
                    }
                    Text(label, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

private fun motionCaption(item: WallpaperItem): String = when (val s = item.source) {
    is WallpaperSource.Generative -> "Animated scene rendered live at your screen's resolution."
    is WallpaperSource.Video -> "Real footage on a silent loop. Downloads once, then plays offline."
    is WallpaperSource.Photo -> when (s.motion) {
        MotionStyle.NONE -> "Still photograph. The fold effect still follows the hinge."
        MotionStyle.LIQUID -> "Slow, flowing liquid glass."
        MotionStyle.PULSE -> "Pulses of light travel through the bright details."
        MotionStyle.GLOBE -> "Rotates slowly in sunlight, built from real NASA surface maps."
        MotionStyle.NEBULA -> "Rebuilt in depth: the clouds and stars shift with a slow camera, and stars twinkle."
        MotionStyle.DEPTH -> "Rebuilt in depth from the image: a slow camera drifts through the scene."
        MotionStyle.COSMOS -> "A slow drift through space with twinkling stars."
    }
}

@Composable
private fun SegmentedToggle(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEachIndexed { i, label ->
                val on = i == selected
                Surface(
                    onClick = { onSelect(i) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (on) MaterialTheme.colorScheme.primary else Color.Transparent,
                    modifier = Modifier.weight(1f).height(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
