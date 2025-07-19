// Copyright (C) 2023 Slack Technologies, LLC
// SPDX-License-Identifier: Apache-2.0
package com.slack.circuitx.gesturenavigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import com.slack.circuit.backstack.rememberSaveableBackStack
import com.slack.circuit.foundation.Circuit
import com.slack.circuit.foundation.CircuitCompositionLocals
import com.slack.circuit.foundation.NavigableCircuitContent
import com.slack.circuit.foundation.rememberCircuitNavigator
import com.slack.circuit.internal.test.TestContentTags
import com.slack.circuit.internal.test.TestContentTags.TAG_GO_NEXT
import com.slack.circuit.internal.test.TestContentTags.TAG_LABEL
import com.slack.circuit.internal.test.TestContentTags.TAG_POP
import com.slack.circuit.internal.test.TestContentTags.TAG_RESET_ROOT_ALPHA
import com.slack.circuit.internal.test.TestContentTags.TAG_RESET_ROOT_BETA
import com.slack.circuit.internal.test.TestScreen
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.ui.ui
import com.slack.circuitx.gesturenavigation.GestureNavigationCrashTest.Companion.TEST_RESET_BETA_RESTORE
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalMaterialApi::class)
@Config(minSdk = 34)
@RunWith(ParameterizedRobolectricTestRunner::class)
class GestureNavigationCrashTest(
  private val decorationOption: GestureNavDecorationOption,
  private val useSwipe: Boolean,
) {
  companion object {
    @JvmStatic
    @ParameterizedRobolectricTestRunner.Parameters(
      name = "{0}_useSwipe={1}"
    )
    fun params() =
      parameterizedParams()
        .combineWithParameters(
          GestureNavDecorationOption.Cupertino,
          GestureNavDecorationOption.AndroidPredictiveBack,
        )
        .combineWithParameters(false, true) // useSwipe

    const val TEST_RESET_BETA_RESTORE = "resetBetaRootRestore"
  }

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private fun pop() {
    if (useSwipe) {
      when (decorationOption) {
        GestureNavDecorationOption.AndroidPredictiveBack -> {
          composeTestRule.activityRule.scenario.performGestureNavigationBackSwipe()
        }
        GestureNavDecorationOption.Cupertino -> {
          composeTestRule.onRoot().performTouchInput {
            swipeRight(startX = width * 0.2f, endX = width * 0.8f)
          }
        }
      }
    } else {
      composeTestRule.onTopNavigationRecordNodeWithTag(TAG_POP).performClick()
    }
  }

  @Test
  fun `Testing crash when switching tabs with restoreState=false and using gesture navigation`() {
    composeTestRule.run {
      val circuit = Circuit.Builder()
        .addPresenterFactory { screen, navigator, _ -> TestCrashPresenter(screen as TestScreen, navigator) }
        .addUiFactory { _, _ -> ui<TestCrashState> { state, modifier -> TestCrashContent(state, modifier) } }
        .build()

      setContent {
        CircuitCompositionLocals(circuit) {
          val backStack = rememberSaveableBackStack(TestScreen.RootAlpha)
          val navigator =
            rememberCircuitNavigator(
              backStack = backStack,
              onRootPop = {}, // no-op for tests
            )
          NavigableCircuitContent(
            navigator = navigator,
            backStack = backStack,
            decoratorFactory =
              remember {
                when (decorationOption) {
                  GestureNavDecorationOption.AndroidPredictiveBack -> {
                    AndroidPredictiveBackNavDecorator.Factory(onBackInvoked = navigator::pop)
                  }
                  GestureNavDecorationOption.Cupertino -> {
                    CupertinoGestureNavigationDecorator.Factory(onBackInvoked = navigator::pop)
                  }
                }
              },
          )
        }
      }

      // current alpha at startup
      onTopNavigationRecordNodeWithTag(TAG_LABEL).assertTextEquals("Root Alpha")

      // reset to beta with saveState=true restoreState=true
      onTopNavigationRecordNodeWithTag(TAG_RESET_ROOT_BETA).performClick()
      onTopNavigationRecordNodeWithTag(TAG_LABEL).assertTextEquals("Root Beta")

      // reset back to alpha with saveState=true restoreState=true
      onTopNavigationRecordNodeWithTag(TAG_RESET_ROOT_ALPHA).performClick()
      onTopNavigationRecordNodeWithTag(TAG_LABEL).assertTextEquals("Root Alpha")

      // go to A
      onTopNavigationRecordNodeWithTag(TAG_GO_NEXT).performClick()
      onTopNavigationRecordNodeWithTag(TAG_LABEL).assertTextEquals("A")

      // reset to beta with saveState=true restoreState=false - !!! important to be false not true !!!
      onTopNavigationRecordNodeWithTag(TEST_RESET_BETA_RESTORE).performClick()
      onTopNavigationRecordNodeWithTag(TAG_LABEL).assertTextEquals("Root Beta")

      // reset back to alpha with saveState=true restoreState=true
      onTopNavigationRecordNodeWithTag(TAG_RESET_ROOT_ALPHA).performClick()
      onTopNavigationRecordNodeWithTag(TAG_LABEL).assertTextEquals("A")

      // pop Screen A - should go to alpha root
      pop()
      onTopNavigationRecordNodeWithTag(TAG_LABEL).assertTextEquals("Root Alpha")

      // reset back to beta with saveState=true restoreState=true - will crash if previous pop was done with gesture navigation
      onTopNavigationRecordNodeWithTag(TAG_RESET_ROOT_BETA).performClick()
      onTopNavigationRecordNodeWithTag(TAG_LABEL).assertTextEquals("Root Beta")
    }
  }
}

private class TestCrashPresenter(
  private val screen: TestScreen,
  private val navigator: Navigator,
) : Presenter<TestCrashState> {
  @Composable
  override fun present(): TestCrashState {
    return TestCrashState(screen.label) { event ->
      when (event) {
        TestCrashEvent.PopNavigation -> navigator.pop()
        TestCrashEvent.GoToNextScreen -> {
          when (screen) {
            // Root screens all go to ScreenA
            TestScreen.RootAlpha -> navigator.goTo(TestScreen.ScreenA)
            TestScreen.RootBeta -> navigator.goTo(TestScreen.ScreenA)
            // Otherwise each screen navigates to the next screen
            TestScreen.ScreenA -> navigator.goTo(TestScreen.ScreenB)
            TestScreen.ScreenB -> navigator.goTo(TestScreen.ScreenC)
            else -> error("Can't navigate from $screen")
          }
        }
        is TestCrashEvent.ResetRootAlpha ->
          navigator.resetRoot(TestScreen.RootAlpha, true, event.restoreState)
        is TestCrashEvent.ResetRootBeta ->
          navigator.resetRoot(TestScreen.RootBeta, true, event.restoreState)
      }
    }
  }
}

@Composable
private fun TestCrashContent(state: TestCrashState, modifier: Modifier = Modifier) {
  Column(modifier = modifier.testTag(TestContentTags.TAG_ROOT)) {
    BasicText(text = state.label, modifier = Modifier.testTag(TAG_LABEL))

    BasicText(
      text = "Pop",
      modifier =
        Modifier.testTag(TAG_POP).clickable {
          state.eventSink(TestCrashEvent.PopNavigation)
        },
    )
    BasicText(
      text = "Go to next",
      modifier =
        Modifier.testTag(TAG_GO_NEXT).clickable {
          state.eventSink(TestCrashEvent.GoToNextScreen)
        },
    )

    BasicText(
      text = "Reset to Root Alpha",
      modifier =
        Modifier.testTag(TAG_RESET_ROOT_ALPHA).clickable {
          state.eventSink(TestCrashEvent.ResetRootAlpha())
        },
    )

    BasicText(
      text = "Reset to Root Beta",
      modifier =
        Modifier.testTag(TAG_RESET_ROOT_BETA).clickable {
          state.eventSink(TestCrashEvent.ResetRootBeta())
        },
    )

    BasicText(
      text = "Reset to Root Beta Restore",
      modifier =
        Modifier.testTag(TEST_RESET_BETA_RESTORE).clickable {
          state.eventSink(TestCrashEvent.ResetRootBeta(false))
        },
    )
  }
}

data class TestCrashState(val label: String, val eventSink: (TestCrashEvent) -> Unit) :
  CircuitUiState

sealed interface TestCrashEvent {
  data object GoToNextScreen : TestCrashEvent

  data object PopNavigation : TestCrashEvent

  data class ResetRootAlpha(
    val restoreState: Boolean = true,
  ) : TestCrashEvent

  data class ResetRootBeta(
    val restoreState: Boolean = true,
  ) : TestCrashEvent
}