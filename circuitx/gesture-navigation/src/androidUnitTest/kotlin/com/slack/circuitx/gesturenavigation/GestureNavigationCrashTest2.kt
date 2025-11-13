// Copyright (C) 2025 Slack Technologies, LLC
// SPDX-License-Identifier: Apache-2.0
package com.slack.circuitx.gesturenavigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.slack.circuit.backstack.rememberSaveableBackStack
import com.slack.circuit.foundation.Circuit
import com.slack.circuit.foundation.CircuitCompositionLocals
import com.slack.circuit.foundation.NavigableCircuitContent
import com.slack.circuit.foundation.rememberCircuitNavigator
import com.slack.circuit.internal.test.TestContentTags
import com.slack.circuit.internal.test.TestContentTags.TAG_LABEL
import com.slack.circuit.internal.test.TestScreenTabs
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.resetRoot
import com.slack.circuit.runtime.ui.ui
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(minSdk = 35)
@RunWith(RobolectricTestRunner::class)
class GestureNavigationCrashTest2 {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun `gesture crash 2`() = runTest {
    composeTestRule.run {
      val circuit =
        Circuit.Builder()
          .addPresenterFactory { screen, navigator, _ ->
            TestCrashPresenter2(screen as TestScreenTabs, navigator)
          }
          .addUiFactory { _, _ ->
            ui<TestCrashState2> { state, modifier -> TestCrashContent2(state, modifier) }
          }
          .build()

      lateinit var navigator: Navigator
      setContent {
        CircuitCompositionLocals(circuit) {
          val backStack = rememberSaveableBackStack(TestScreenTabs.Launch)
          navigator = rememberCircuitNavigator(backStack = backStack)
          NavigableCircuitContent(
            navigator = navigator,
            backStack = backStack,
            decoratorFactory =
              remember { AndroidPredictiveBackNavDecorator.Factory(onBackInvoked = navigator::pop) },
          )
        }
      }

        // current alpha at startup
        onTopNavigationRecordNodeWithTag(TAG_LABEL).assertTextEquals("Launch")
        navigator.resetRoot(TestScreenTabs.Tab1, saveState = false, restoreState = false)
        onTopNavigationRecordNodeWithTag(TAG_LABEL).assertTextEquals("Tab1")
        navigator.resetRoot(TestScreenTabs.Tab4, saveState = true, restoreState = true)
        onTopNavigationRecordNodeWithTag(TAG_LABEL).assertTextEquals("Tab4")
        navigator.resetRoot(TestScreenTabs.Tab2, saveState = true, restoreState = true)
        onTopNavigationRecordNodeWithTag(TAG_LABEL).assertTextEquals("Tab2")

        navigator.goTo(TestScreenTabs.IntScreen(1))
        onTopNavigationRecordNodeWithTag(TAG_LABEL).assertTextEquals("IntScreen1")
        navigator.goTo(TestScreenTabs.IntScreen(2))
        onTopNavigationRecordNodeWithTag(TAG_LABEL).assertTextEquals("IntScreen2")
        navigator.goTo(TestScreenTabs.IntScreen(3))
        onTopNavigationRecordNodeWithTag(TAG_LABEL).assertTextEquals("IntScreen3")
        activityRule.scenario.performGestureNavigationBackSwipe()
        onTopNavigationRecordNodeWithTag(TAG_LABEL).assertTextEquals("IntScreen2")

        navigator.goTo(TestScreenTabs.IntScreen(3))
        onTopNavigationRecordNodeWithTag(TAG_LABEL).assertTextEquals("IntScreen3")
        navigator.goTo(TestScreenTabs.IntScreen(4))
        onTopNavigationRecordNodeWithTag(TAG_LABEL).assertTextEquals("IntScreen4")

        navigator.resetRoot(TestScreenTabs.Tab3, saveState = true, restoreState = false)
        onTopNavigationRecordNodeWithTag(TAG_LABEL).assertTextEquals("Tab3")

        navigator.resetRoot(TestScreenTabs.Tab2, saveState = true, restoreState = true)
        onTopNavigationRecordNodeWithTag(TAG_LABEL).assertTextEquals("IntScreen4")
        activityRule.scenario.performGestureNavigationBackSwipe()
        activityRule.scenario.performGestureNavigationBackSwipe() // this crashes on device
        onTopNavigationRecordNodeWithTag(TAG_LABEL).assertTextEquals("IntScreen2")
        activityRule.scenario.performGestureNavigationBackSwipe()
        onTopNavigationRecordNodeWithTag(TAG_LABEL).assertTextEquals("IntScreen1")
        activityRule.scenario.performGestureNavigationBackSwipe()
        onTopNavigationRecordNodeWithTag(TAG_LABEL).assertTextEquals("Tab2")
    }
  }
}

private class TestCrashPresenter2(private val screen: TestScreenTabs, private val navigator: Navigator) :
  Presenter<TestCrashState2> {
  @Composable
  override fun present(): TestCrashState2 {
    return TestCrashState2(screen.label) { event ->
    }
  }
}

@Composable
private fun TestCrashContent2(state: TestCrashState2, modifier: Modifier = Modifier) {
  Box(modifier = modifier.testTag(TestContentTags.TAG_ROOT)) {
    BasicText(text = state.label, modifier = Modifier.testTag(TAG_LABEL))
  }
}

data class TestCrashState2(val label: String, val eventSink: (TestCrashEvent2) -> Unit) :
  CircuitUiState

sealed interface TestCrashEvent2 {
}
