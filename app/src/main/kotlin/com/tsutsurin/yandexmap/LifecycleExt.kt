package com.tsutsurin.yandexmap

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

fun LifecycleOwner.launchWhen(
    state: Lifecycle.State,
    block: suspend CoroutineScope.() -> Unit
) = lifecycleScope.launch {
    repeatOnLifecycle(state, block)
}

fun LifecycleOwner.launchWhenCreated(block: suspend CoroutineScope.() -> Unit) =
    launchWhen(Lifecycle.State.CREATED, block)

fun LifecycleOwner.launchWhenStarted(block: suspend CoroutineScope.() -> Unit) =
    launchWhen(Lifecycle.State.STARTED, block)

fun LifecycleOwner.launchWhenResumed(block: suspend CoroutineScope.() -> Unit) =
    launchWhen(Lifecycle.State.RESUMED, block)
