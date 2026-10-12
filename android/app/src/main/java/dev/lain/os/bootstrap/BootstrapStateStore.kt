package dev.lain.os.bootstrap

import android.content.Context
import android.content.SharedPreferences

/** Setup acknowledgement only: grants and planner readiness are always rescanned. */
internal class BootstrapStateStore(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences("bootstrap-v1", Context.MODE_PRIVATE),
    )

    fun isComplete(): Boolean = preferences.getInt("setup_version", 0) == SETUP_VERSION

    fun complete() {
        check(preferences.edit().putInt("setup_version", SETUP_VERSION).commit()) {
            "Could not save setup completion"
        }
    }

    private companion object {
        const val SETUP_VERSION = 1
    }
}
