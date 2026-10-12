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
        val previous = if (preferences.contains("setup_version")) {
            preferences.getInt("setup_version", 0)
        } else null
        if (!preferences.edit().putInt("setup_version", SETUP_VERSION).commit()) {
            // commit applies to memory even when writing to disk fails. Restore
            // that memory state before a recreated launcher can read it.
            val rollback = preferences.edit()
            if (previous == null) rollback.remove("setup_version")
            else rollback.putInt("setup_version", previous)
            rollback.commit()
            error("Could not save setup completion")
        }
    }

    private companion object {
        const val SETUP_VERSION = 1
    }
}
