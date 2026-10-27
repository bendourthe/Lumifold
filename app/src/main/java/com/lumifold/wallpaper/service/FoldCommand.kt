package com.lumifold.wallpaper.service

/**
 * What the on-device fold helper should apply. An ordinary app cannot power both
 * panels while Android still calls the phone closed, so a shell helper reads this.
 */
object FoldCommand {
    const val ARMED = "0"
    const val BOTH = "5"
    const val OPEN = "2"
    const val ONE = "reset"

    @Volatile var mode: String = ONE
}
