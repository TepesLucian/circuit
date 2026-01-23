package com.slack.circuitx.gesturenavigation

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import com.slack.circuit.backstack.rememberSaveableBackStack
import com.slack.circuit.foundation.CircuitCompositionLocals
import com.slack.circuit.foundation.NavigableCircuitContent
import com.slack.circuit.foundation.rememberCircuitNavigator
import com.slack.circuit.internal.test.TestContentTags.TAG_COUNT
import com.slack.circuit.internal.test.TestContentTags.TAG_GO_NEXT
import com.slack.circuit.internal.test.TestContentTags.TAG_INCREASE_COUNT
import com.slack.circuit.internal.test.TestContentTags.TAG_LABEL
import com.slack.circuit.internal.test.TestScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

val IntCompositionLocal = staticCompositionLocalOf { 0 }

@Config(minSdk = 34)
@RunWith(RobolectricTestRunner::class)
class CompositionLocalStateLossTest {
    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `composition local state loss test`() = runTest {
        composeTestRule.run {
            val circuit = createTestBackCircuit()
            var intValue by mutableIntStateOf(1)

            setContent {
                CompositionLocalProvider(IntCompositionLocal provides intValue) {
                    CircuitCompositionLocals(circuit) {
                        val backStack = rememberSaveableBackStack(TestScreen.RootAlpha)
                        val navigator = rememberCircuitNavigator(backStack = backStack)
                        NavigableCircuitContent(
                            navigator = navigator,
                            backStack = backStack,
                            decoratorFactory = remember { AndroidPredictiveBackNavDecorator.Factory(onBackInvoked = navigator::pop) },
                        )
                    }
                }
            }
            // Current: Root Alpha. Navigate to Screen A
            onTopNavigationRecordNodeWithTag(TAG_LABEL).assertTextEquals("Root Alpha")
            onTopNavigationRecordNodeWithTag(TAG_COUNT).assertTextEquals("0")
            onTopNavigationRecordNodeWithTag(TAG_INCREASE_COUNT).performClick()
            onTopNavigationRecordNodeWithTag(TAG_COUNT).assertTextEquals("1")

            // go to A and back normally, root should remain at count=1
            println("Going to A")
            onTopNavigationRecordNodeWithTag(TAG_GO_NEXT).performClick()
            onTopNavigationRecordNodeWithTag(TAG_LABEL).assertTextEquals("A")
            // this is screen A count so should be 0
            onTopNavigationRecordNodeWithTag(TAG_COUNT).assertTextEquals("0")

            println("Going back normally to root")
            activity.onBackPressedDispatcher.onBackPressed()
            // back at root alpha, checkout count = 1
            onTopNavigationRecordNodeWithTag(TAG_LABEL).assertTextEquals("Root Alpha")
            onTopNavigationRecordNodeWithTag(TAG_COUNT).assertTextEquals("1")

            // do the same process again but increase the composition local on screen A
            println("Going to A but changing the composition local")
            onTopNavigationRecordNodeWithTag(TAG_GO_NEXT).performClick()
            // increasing here fails the test - probably due to navigation animation running + recomposition of the composition local ?
            intValue += 1
            onTopNavigationRecordNodeWithTag(TAG_LABEL).assertTextEquals("A")
            // increasing here AND commenting out previous increase passes the test
            intValue += 1
            println("Going back to root")
            activity.onBackPressedDispatcher.onBackPressed()
            onTopNavigationRecordNodeWithTag(TAG_LABEL).assertTextEquals("Root Alpha")
            onTopNavigationRecordNodeWithTag(TAG_COUNT).assertTextEquals("1")
        }
    }
}