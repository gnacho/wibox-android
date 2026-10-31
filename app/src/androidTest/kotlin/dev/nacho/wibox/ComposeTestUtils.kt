package dev.nacho.wibox

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag

fun ComposeTestRule.tag(value: String): SemanticsNodeInteraction = onNodeWithTag(value, useUnmergedTree = true)

fun ComposeTestRule.awaitTag(value: String, timeoutMillis: Long = 20_000) {
    waitUntil(timeoutMillis) {
        onAllNodesWithTag(value, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }
}

fun ComposeTestRule.awaitEnabled(value: String, timeoutMillis: Long = 20_000) {
    waitUntil(timeoutMillis) {
        onAllNodesWithTag(value, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .any { SemanticsProperties.Disabled !in it.config }
    }
}
