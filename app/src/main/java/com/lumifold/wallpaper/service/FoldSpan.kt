package com.lumifold.wallpaper.service

/**
 * The app does not ask Android to power both panels. Forcing a device state turns the cover
 * off around 45 degrees, brings it back late and cropped, holds the inner screen off on a fast
 * open, and locks the phone. Android's own handoff stays in charge.
 */
object FoldSpan {
    const val CLOSED_MAX_DEGREES = 1f

    /** This phone's hinge reads about 175 when the phone is flat open, not 180. */
    const val OPEN_MIN_DEGREES = 170f

    /** Below this on the way closed, the inner panel should turn off and the cover stay on. */
    const val CLOSE_INNER_OFF_DEGREES = 10f

    @Suppress("UNUSED_PARAMETER")
    fun commandFor(enabled: Boolean, angleDegrees: Float, opening: Boolean = true): String =
        FoldCommand.ONE

    @Suppress("UNUSED_PARAMETER")
    fun wantsBothScreens(enabled: Boolean, angleDegrees: Float, opening: Boolean = true): Boolean =
        false
}
