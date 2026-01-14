package com.sms.edgenode.observability

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics

object CrashReporter {
    private val crashlytics: FirebaseCrashlytics? by lazy {
        try {
            FirebaseCrashlytics.getInstance()
        } catch (e: Exception) {
            Log.w("CrashReporter", "Crashlytics not initialized")
            null
        }
    }

    fun logError(message: String, throwable: Throwable? = null) {
        crashlytics?.log(message)
        throwable?.let { crashlytics?.recordException(it) }
        Log.e("EdgeNode", message, throwable)
    }

    fun logEvent(event: String, details: Map<String, String> = emptyMap()) {
        crashlytics?.log("$event: $details")
        Log.i("EdgeNode", "$event: $details")
    }

    fun setUserId(userId: String) {
        crashlytics?.setUserId(userId)
    }

    fun setCustomKey(key: String, value: String) {
        crashlytics?.setCustomKey(key, value)
    }
}
