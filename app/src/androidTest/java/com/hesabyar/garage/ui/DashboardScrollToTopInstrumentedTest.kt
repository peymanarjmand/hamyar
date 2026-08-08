package com.hesabyar.garage.ui

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DashboardScrollToTopInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun newHomeRequestScrollsDashboardListToFirstItem() {
        lateinit var listState: LazyListState
        var request by mutableLongStateOf(0L)

        composeRule.setContent {
            listState = rememberLazyListState(initialFirstVisibleItemIndex = 20)
            DashboardScrollToTopEffect(listState, request)
            LazyColumn(state = listState) {
                items((0..30).toList()) { index -> Text("row-$index", Modifier.height(48.dp)) }
            }
        }

        composeRule.runOnIdle {
            assertTrue(listState.firstVisibleItemIndex > 0)
            request++
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { listState.firstVisibleItemIndex == 0 }
    }
}
