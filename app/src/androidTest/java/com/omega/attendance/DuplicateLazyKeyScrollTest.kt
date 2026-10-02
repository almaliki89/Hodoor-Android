package com.omega.attendance

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.omega.attendance.presentation.components.uniqueLazyItemKey
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DuplicateLazyKeyScrollTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun repeatedIdsCanBeScrolledWithoutDuplicateKeys() {
        val ids = listOf(1L, 1L, 2L)
        composeRule.setContent {
            LazyColumn(Modifier.height(100.dp).testTag("duplicate-list")) {
                itemsIndexed(ids, key = { index, id -> uniqueLazyItemKey(id, index) }) { index, _ ->
                    Text("row-$index", Modifier.height(64.dp))
                }
            }
        }

        composeRule.onNodeWithText("row-0").assertIsDisplayed()
        composeRule.onNodeWithTag("duplicate-list").performScrollToIndex(2)
        composeRule.onNodeWithText("row-2").assertIsDisplayed()
    }

    @Test fun hallAndClassWithSameIdCanBeScrolledTogether() {
        composeRule.setContent {
            LazyColumn(Modifier.height(100.dp).testTag("settings-list")) {
                item { Text("header", Modifier.height(64.dp)) }
                itemsIndexed(listOf(1L), key = { index, id -> "hall-${uniqueLazyItemKey(id, index)}" }) { _, _ ->
                    Text("hall", Modifier.height(64.dp))
                }
                itemsIndexed(listOf(1L), key = { index, id -> "class-${uniqueLazyItemKey(id, index)}" }) { _, _ ->
                    Text("class", Modifier.height(64.dp))
                }
            }
        }

        composeRule.onNodeWithTag("settings-list").performScrollToIndex(2)
        composeRule.onNodeWithText("class").assertIsDisplayed()
    }
}
