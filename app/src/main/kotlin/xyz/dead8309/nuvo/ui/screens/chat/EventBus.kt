package xyz.dead8309.nuvo.ui.screens.chat

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class PaymentEvent(
    // "success", "cancelled", "failed"
    val status: String,
    val originalToolCallId: String?,
    val stripeSessionId: String?
)

object PaymentEventBus {
    private val _events = MutableSharedFlow<PaymentEvent>()
    val events = _events.asSharedFlow()

    suspend fun post(event: PaymentEvent) {
        _events.emit(event)
    }
}