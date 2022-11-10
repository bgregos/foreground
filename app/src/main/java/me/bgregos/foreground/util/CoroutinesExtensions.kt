package me.bgregos.foreground.util

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

/**
 * Launches a coroutine that repeats whenever the lifecycle is resumed. This is useful for
 * collecting flows in Android components with less boilerplate.
 */
fun LifecycleOwner.launchLifecycleAware(toRun: suspend () -> Unit) {
    lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.RESUMED) {
            toRun()
        }
    }
}