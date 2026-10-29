package com.lumifold.wallpaper.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.lumifold.wallpaper.model.WallpaperRepository

/** Quick Settings tile that turns a boolean preference on and off. */
abstract class PreferenceTile : TileService() {

    protected abstract fun read(): Boolean
    protected abstract fun write(enabled: Boolean)
    protected open fun subtitle(enabled: Boolean): String = if (enabled) "On" else "Off"

    override fun onStartListening() {
        refresh()
    }

    override fun onClick() {
        write(!read())
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val enabled = read()
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = subtitle(enabled)
        tile.updateTile()
    }
}

class FoldEffectTileService : PreferenceTile() {
    override fun read() = WallpaperRepository.isFoldEffectEnabled(this)
    override fun write(enabled: Boolean) = WallpaperRepository.setFoldEffectEnabled(this, enabled)
}

class MotionTileService : PreferenceTile() {
    override fun read() = WallpaperRepository.isDynamicMotionEnabled(this)
    override fun write(enabled: Boolean) = WallpaperRepository.setDynamicMotionEnabled(this, enabled)

    override fun subtitle(enabled: Boolean): String = when {
        !enabled -> "Off"
        MotionPolicy.currentPause(this) == MotionPolicy.Pause.BATTERY_SAVER -> "Paused, saver"
        else -> "On"
    }
}
