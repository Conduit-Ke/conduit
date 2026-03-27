package com.sms.edgenode.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * A global signal to wake up the RoutingService immediately when new work is available.
 * Eliminates the 5-second idle polling delay.
 */
object QueueSignal {
    private val _signal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val signal = _signal.asSharedFlow()

    /**
     * Signals that a new message has been injected or a rule has changed.
     */
    fun poke() {
        _signal.tryEmit(Unit)
    }
}
