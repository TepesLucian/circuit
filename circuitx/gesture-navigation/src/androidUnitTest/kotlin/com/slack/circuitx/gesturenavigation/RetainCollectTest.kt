package com.slack.circuitx.gesturenavigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import com.slack.circuit.backstack.rememberSaveableBackStack
import com.slack.circuit.foundation.Circuit
import com.slack.circuit.foundation.CircuitCompositionLocals
import com.slack.circuit.foundation.NavigableCircuitContent
import com.slack.circuit.foundation.rememberCircuitNavigator
import com.slack.circuit.internal.test.TestContentTags
import com.slack.circuit.internal.test.TestContentTags.TAG_COUNT
import com.slack.circuit.internal.test.TestContentTags.TAG_GO_NEXT
import com.slack.circuit.internal.test.TestContentTags.TAG_LABEL
import com.slack.circuit.internal.test.TestScreen
import com.slack.circuit.retained.produceAndCollectAsRetainedState
import com.slack.circuit.runtime.CircuitUiEvent
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Navigator
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.runtime.ui.ui
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val DELAY = 2000L

@RunWith(RobolectricTestRunner::class)
class RetainCollectTest {

    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `retain stops collecting`() = runTest {
        composeTestRule.run {
            val circuit =
                Circuit.Builder()
                    .addPresenterFactory { screen, navigator, _ ->
                        RetainCollectPresenter(screen as TestScreen, navigator)
                    }
                    .addUiFactory { _, _ ->
                        ui<RetainUiState> { state, modifier -> RetainTestUi(state, modifier) }
                    }
                    .build()

            lateinit var navigator: Navigator
            setContent {
                CircuitCompositionLocals(circuit) {
                    val backStack = rememberSaveableBackStack(TestScreen.RootAlpha)
                    navigator = rememberCircuitNavigator(backStack)
                    NavigableCircuitContent(
                        navigator = navigator,
                        backStack = backStack,
                        decoratorFactory = remember {
                            GestureNavigationDecorationFactory(onBackInvoked = navigator::pop)
                        },
                    )
                }
            }

            // initial value should be 0
            onTopNavigationRecordNodeWithTag(TAG_LABEL).assertTextEquals("Root Alpha")
            onTopNavigationRecordNodeWithTag(TAG_COUNT).assertTextEquals("0")
            // wait for the new value to confirm flow collecting is working
            advanceTimeByAndRun(DELAY)
            onTopNavigationRecordNodeWithTag(TAG_COUNT).assertTextEquals("1")
            // navigate to next screen and check the initial value
            onTopNavigationRecordNodeWithTag(TAG_GO_NEXT).performClick()
            onTopNavigationRecordNodeWithTag(TAG_LABEL).assertTextEquals("A")
            onTopNavigationRecordNodeWithTag(TAG_COUNT).assertTextEquals("0")
            // don't wait for the flow collecting and go to next
            onTopNavigationRecordNodeWithTag(TAG_GO_NEXT).performClick()
            onTopNavigationRecordNodeWithTag(TAG_LABEL).assertTextEquals("B")
            onTopNavigationRecordNodeWithTag(TAG_COUNT).assertTextEquals("0")
            onTopNavigationRecordNodeWithTag(TAG_GO_NEXT).performClick()
            onTopNavigationRecordNodeWithTag(TAG_LABEL).assertTextEquals("C")
            onTopNavigationRecordNodeWithTag(TAG_COUNT).assertTextEquals("0")
            // pop to screen B wait for 1
            activityRule.scenario.performGestureNavigationBackSwipe()
            onTopNavigationRecordNodeWithTag(TAG_LABEL).assertTextEquals("B")
            onTopNavigationRecordNodeWithTag(TAG_COUNT).assertTextEquals("0")
            advanceTimeByAndRun(DELAY)
            onTopNavigationRecordNodeWithTag(TAG_COUNT).assertTextEquals("1")
            // pop to screen A wait for 1
            activityRule.scenario.performGestureNavigationBackSwipe()
            onTopNavigationRecordNodeWithTag(TAG_LABEL).assertTextEquals("A")
            onTopNavigationRecordNodeWithTag(TAG_COUNT).assertTextEquals("0")
            advanceTimeByAndRun(DELAY)
            onTopNavigationRecordNodeWithTag(TAG_COUNT).assertTextEquals("1")
            // pop to root; value should be already at 1
            activityRule.scenario.performGestureNavigationBackSwipe()
            onTopNavigationRecordNodeWithTag(TAG_LABEL).assertTextEquals("Root Alpha")
            onTopNavigationRecordNodeWithTag(TAG_COUNT).assertTextEquals("1")
        }
    }

    private fun ComposeTestRule.advanceTimeByAndRun(milliseconds: Long) {
        mainClock.advanceTimeBy(milliseconds)
        mainClock.advanceTimeByFrame()
    }
}

private data class RetainUiState(
    val value: Int,
    val label: String,
    val eventSink: (RetainUiEvent) -> Unit
) : CircuitUiState

private sealed interface RetainUiEvent: CircuitUiEvent {
    data object GoToNext : RetainUiEvent
}

@Composable
private fun RetainTestUi(
    state: RetainUiState,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier
            .testTag(TestContentTags.TAG_ROOT),
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            BasicText(
                text = state.value.toString(),
                modifier = Modifier.testTag(TAG_COUNT),
            )
            BasicText(
                text = state.label,
                modifier = Modifier.testTag(TAG_LABEL),
            )
            BasicText(
                text = "Next",
                modifier = Modifier
                    .testTag(TAG_GO_NEXT)
                    .clickable {
                        state.eventSink(RetainUiEvent.GoToNext)
                    },
            )
        }
    }
}

private class RetainCollectPresenter(
    private val screen: TestScreen,
    private val navigator: Navigator,
) : Presenter<RetainUiState> {
    @Composable
    override fun present(): RetainUiState {
        val value by produceAndCollectAsRetainedState(
            initial = 0,
            producer = {
                flow {
                    delay(DELAY)
                    emit(1)
                }
            }
        )
        return RetainUiState(
            value = value,
            label = screen.label,
        ) { event ->
            when (event) {
                is RetainUiEvent.GoToNext -> {
                    navigator.goTo(when (screen) {
                        is TestScreen.RootAlpha -> TestScreen.ScreenA
                        is TestScreen.ScreenA -> TestScreen.ScreenB
                        else -> TestScreen.ScreenC
                    })
                }
            }
        }
    }
}