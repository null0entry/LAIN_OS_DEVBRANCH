package dev.lain.os.voice

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpeechVoiceSelectionStoreAndroidTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun clearPreferences() {
        context.getSharedPreferences(SPEECH_VOICE_PREFERENCES, 0).edit().clear().commit()
    }

    @Test fun selectionSurvivesStoreRecreationAndIsProviderScoped() {
        val selected = LocalSpeechVoice(
            id = "offline-en",
            engineId = "fixture.engine",
            requiresNetwork = false,
            installed = true,
        )

        SpeechVoiceSelectionStore(context).save("local-provider", selected)

        assertEquals(
            StoredSpeechVoice("fixture.engine", "offline-en"),
            SpeechVoiceSelectionStore(context).load("local-provider"),
        )
        assertNull(SpeechVoiceSelectionStore(context).load("other-provider"))
    }
}
