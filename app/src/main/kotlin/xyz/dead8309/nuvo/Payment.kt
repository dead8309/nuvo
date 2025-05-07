package xyz.dead8309.nuvo

import android.content.Context
import android.content.SharedPreferences

object PaymentContextPrefs {
    private const val PREFS_NAME = "nuvo_payment_context_prefs"
    private const val KEY_SESSION_ID = "pending_payment_session_id"
    private const val KEY_TOOL_CALL_ID = "pending_payment_tool_call_id"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun savePendingPayment(context: Context, sessionId: String, toolCallId: String) {
        getPrefs(context).edit()
            .putString(KEY_SESSION_ID, sessionId)
            .putString(KEY_TOOL_CALL_ID, toolCallId)
            .apply()
    }

    data class PendingPaymentInfo(val sessionId: String, val toolCallId: String)

    fun getAndClearPendingPayment(context: Context): PendingPaymentInfo? {
        val prefs = getPrefs(context)
        val sessionId = prefs.getString(KEY_SESSION_ID, null)
        val toolCallId = prefs.getString(KEY_TOOL_CALL_ID, null)

        return if (sessionId != null && toolCallId != null) {
            prefs.edit()
                .remove(KEY_SESSION_ID)
                .remove(KEY_TOOL_CALL_ID)
                .apply()
            PendingPaymentInfo(sessionId, toolCallId)
        } else {
            prefs.edit()
                .remove(KEY_SESSION_ID)
                .remove(KEY_TOOL_CALL_ID)
                .apply()
            null
        }
    }
}