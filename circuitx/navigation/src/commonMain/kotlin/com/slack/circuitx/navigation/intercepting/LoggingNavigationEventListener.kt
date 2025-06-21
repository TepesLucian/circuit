// Copyright (C) 2025 Slack Technologies, LLC
// SPDX-License-Identifier: Apache-2.0
package com.slack.circuitx.navigation.intercepting

import com.slack.circuit.runtime.screen.PopResult
import com.slack.circuit.runtime.screen.Screen
import kotlinx.collections.immutable.ImmutableList
import kotlin.math.log

/** A [NavigationEventListener] that adds logging to Circuit navigation. */
public class LoggingNavigationEventListener(private val logger: NavigationLogger) :
  NavigationEventListener {

    init {
      logger.log("App start on tab Root")
    }

  override fun onBackStackChanged(backStack: ImmutableList<Screen>) {
//    logger.log("Backstack changed ${backStack.joinToString { it.loggingName() ?: "" }}")
  }

  override fun goTo(screen: Screen) {
    logger.log("goTo ${screen.loggingName()}")
  }

  override fun pop(backStack: ImmutableList<Screen>, result: PopResult?) {
    logger.log("pop ${backStack.firstOrNull()?.loggingName()}")
  }

  override fun resetRoot(
    newRoot: Screen,
    saveState: Boolean,
    restoreState: Boolean
  ) {
    logger.log("resetRoot $newRoot saveState $saveState restoreState $restoreState")
  }
}

private fun Screen.loggingName() = this::class.simpleName
