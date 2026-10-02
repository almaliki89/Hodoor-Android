package com.omega.attendance

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.omega.attendance.presentation.settings.AboutSection
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AboutSectionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun developerPortraitAndContactAreVisible() {
        compose.setContent { AboutSection() }
        compose.onNodeWithContentDescription("صورة المبرمج مصطفى تركي").assertExists()
        compose.onNodeWithText("المبرمج: مصطفى تركي").assertExists()
    }
}
