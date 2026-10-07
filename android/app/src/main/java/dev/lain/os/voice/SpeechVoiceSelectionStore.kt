package dev.lain.os.voice

import android.content.Context

const val SPEECH_VOICE_PREFERENCES = "speech_voice_selection"

data class StoredSpeechVoice(
    val engineId: String,
    val voiceId: String,
)

/**
 * Persists only stable, non-secret voice identity. The provider key and engine
 * identity must both match the current local inventory before use.
 */
class SpeechVoiceSelectionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        SPEECH_VOICE_PREFERENCES,
        Context.MODE_PRIVATE,
    )

    fun load(providerId: String): StoredSpeechVoice? {
        if (providerId.isBlank()) return null
        val engineId = preferences.getString(engineKey(providerId), null)
        val voiceId = preferences.getString(voiceKey(providerId), null)
        if (engineId.isNullOrBlank() || voiceId.isNullOrBlank()) return null
        return StoredSpeechVoice(engineId = engineId, voiceId = voiceId)
    }

    fun save(providerId: String, voice: LocalSpeechVoice) {
        require(providerId.isNotBlank())
        require(voice.id.isNotBlank() && voice.engineId.isNotBlank())
        require(voice.installed && !voice.requiresNetwork)
        preferences.edit()
            .putString(engineKey(providerId), voice.engineId)
            .putString(voiceKey(providerId), voice.id)
            .apply()
    }

    private fun engineKey(providerId: String) = "$providerId.engine"
    private fun voiceKey(providerId: String) = "$providerId.voice"
}
