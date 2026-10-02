package com.omega.attendance

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.omega.attendance.presentation.reports.ReportSetupPanel
import com.omega.attendance.presentation.reports.ReportsUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReportsLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun reportControlsAreGroupedIntoClearSections() {
        compose.setContent {
            MaterialTheme {
                ReportSetupPanel(ReportsUiState())
            }
        }

        compose.onNodeWithText("خيارات التقرير").assertExists()
        compose.onNodeWithText("الفئة").assertExists()
        compose.onNodeWithText("الفترة").assertExists()
        compose.onNodeWithText("الفلاتر").assertExists()
        compose.onAllNodesWithText("تصفية النتائج").assertCountEquals(0)
        compose.onAllNodesWithText("حالة الحضور").assertCountEquals(0)
    }
}
