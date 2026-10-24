package com.lumifold.wallpaper.service

import com.lumifold.wallpaper.service.MotionPolicy.Battery
import com.lumifold.wallpaper.service.MotionPolicy.Pause
import org.junit.Assert.assertEquals
import org.junit.Test

class MotionPolicyTest {

    private fun reason(saverRule: Boolean, levelRule: Boolean, threshold: Int, b: Battery) =
        MotionPolicy.pauseReason(saverRule, levelRule, threshold, b)

    @Test
    fun batterySaverPausesOnlyWhenTheRuleIsOn() {
        val saver = Battery(level = 80, charging = false, powerSave = true)
        assertEquals(Pause.BATTERY_SAVER, reason(true, false, 20, saver))
        assertEquals(Pause.NONE, reason(false, false, 20, saver))
    }

    @Test
    fun lowBatteryPausesAtOrBelowTheThreshold() {
        assertEquals(Pause.LOW_BATTERY, reason(false, true, 20, Battery(20, false, false)))
        assertEquals(Pause.LOW_BATTERY, reason(false, true, 20, Battery(5, false, false)))
        assertEquals(Pause.NONE, reason(false, true, 20, Battery(21, false, false)))
    }

    @Test
    fun chargingOverridesTheLevelRule() {
        assertEquals(Pause.NONE, reason(false, true, 20, Battery(10, true, false)))
    }

    @Test
    fun unknownLevelNeverPauses() {
        assertEquals(Pause.NONE, reason(false, true, 20, Battery(-1, false, false)))
    }

    @Test
    fun saverTakesPriority() {
        assertEquals(Pause.BATTERY_SAVER, reason(true, true, 20, Battery(10, false, true)))
    }
}
