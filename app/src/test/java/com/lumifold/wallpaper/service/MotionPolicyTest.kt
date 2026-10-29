package com.lumifold.wallpaper.service

import com.lumifold.wallpaper.service.MotionPolicy.Pause
import org.junit.Assert.assertEquals
import org.junit.Test

class MotionPolicyTest {

    @Test
    fun batterySaverPausesOnlyWhenTheRuleIsOn() {
        assertEquals(Pause.BATTERY_SAVER, MotionPolicy.pauseReason(pauseOnSaver = true, powerSave = true))
        assertEquals(Pause.NONE, MotionPolicy.pauseReason(pauseOnSaver = false, powerSave = true))
    }

    @Test
    fun motionRunsWhenBatterySaverIsOff() {
        assertEquals(Pause.NONE, MotionPolicy.pauseReason(pauseOnSaver = true, powerSave = false))
        assertEquals(Pause.NONE, MotionPolicy.pauseReason(pauseOnSaver = false, powerSave = false))
    }
}
