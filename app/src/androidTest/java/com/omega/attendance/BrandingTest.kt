package com.omega.attendance

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrandingTest {
    @Test
    fun appNameIsHodoorInResourcesAndLauncher() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertEquals("حضور", context.getString(R.string.app_name))
        assertEquals("حضور", context.applicationInfo.loadLabel(context.packageManager).toString())
    }
}
