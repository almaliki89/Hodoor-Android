package com.omega.attendance

import com.omega.attendance.data.settings.AppPreferences
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsModelTest {
    @Test fun defaultLateThresholdFollowsStart() {
        val value = AppPreferences()
        assertTrue(value.lateMinutes > value.startMinutes)
    }
}
