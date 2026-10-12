package dev.lain.os.bootstrap

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class BootstrapStateStoreTest {
    @Test fun failedDiskCommitDoesNotCompleteSetupInMemoryOrAfterRecreation() {
        // SharedPreferences applies edits to memory before reporting disk failure.
        val values = mutableMapOf<String, Int>()
        lateinit var preferences: SharedPreferences
        preferences = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getInt" -> values[args!![0]] ?: args[1]
                "contains" -> values.containsKey(args!![0])
                "edit" -> {
                    val edits = mutableMapOf<String, Int?>()
                    lateinit var editor: SharedPreferences.Editor
                    editor = Proxy.newProxyInstance(
                        SharedPreferences.Editor::class.java.classLoader,
                        arrayOf(SharedPreferences.Editor::class.java),
                    ) { _, editMethod, editArgs ->
                        when (editMethod.name) {
                            "putInt" -> { edits[editArgs!![0] as String] = editArgs[1] as Int; editor }
                            "remove" -> { edits[editArgs!![0] as String] = null; editor }
                            "commit" -> {
                                edits.forEach { (key, value) ->
                                    if (value == null) values.remove(key) else values[key] = value
                                }
                                false
                            }
                            else -> error("Unexpected editor method: ${editMethod.name}")
                        }
                    } as SharedPreferences.Editor
                    editor
                }
                else -> error("Unexpected preferences method: ${method.name}")
            }
        } as SharedPreferences
        val store = BootstrapStateStore(preferences)
        assertThrows(IllegalStateException::class.java) { store.complete() }
        assertFalse(store.isComplete())
        assertFalse(BootstrapStateStore(preferences).isComplete())
    }
}
