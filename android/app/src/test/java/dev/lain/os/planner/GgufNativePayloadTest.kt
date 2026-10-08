package dev.lain.os.planner

import org.junit.Assert.assertEquals
import org.junit.Test

class GgufNativePayloadTest {
    private fun parsed(text: String) = GgufNativeProbe.decodeNativePayload(text.toByteArray(Charsets.UTF_8))
    private val invalid = GgufNativeProbeResult.Rejected(GgufNativeProbeFailure.OUTPUT_INVALID)

    @Test fun preservesCompleteUtf8MultibytePayload() {
        assertEquals(GgufNativeProbeResult.Generated("Grüße 🌙"), parsed("OK:Grüße 🌙"))
    }

    @Test fun rejectsMalformedUtf8InsteadOfReplacingBytes() {
        val malformed = byteArrayOf('O'.code.toByte(), 'K'.code.toByte(), ':'.code.toByte(), 0xC3.toByte(), 0x28)
        assertEquals(invalid, GgufNativeProbe.decodeNativePayload(malformed))
    }

    @Test fun rejectsEmbeddedNulInsteadOfTruncating() {
        val nul = byteArrayOf('O'.code.toByte(), 'K'.code.toByte(), ':'.code.toByte(), 'a'.code.toByte(), 0, 'b'.code.toByte())
        assertEquals(invalid, GgufNativeProbe.decodeNativePayload(nul))
    }

    @Test fun rejectsOversizedPayloadBeforeStringAllocation() {
        val oversized = "OK:".toByteArray() + ByteArray(65_537) { 'a'.code.toByte() }
        assertEquals(invalid, GgufNativeProbe.decodeNativePayload(oversized))
    }

    @Test fun acceptsMaxBoundedUtf8TextPayload() {
        val max = "OK:".toByteArray() + ByteArray(65_536) { 'a'.code.toByte() }
        val result = GgufNativeProbe.decodeNativePayload(max)
        assertEquals(GgufNativeProbeResult.Generated("a".repeat(65_536)), result)
    }

    @Test fun preservesKnownNativeErrorCode() {
        assertEquals(GgufNativeProbeResult.Rejected(GgufNativeProbeFailure.MODEL_NOT_FOUND), parsed("ERR:MODEL_NOT_FOUND"))
    }

    @Test fun rejectsMissingProtocolPrefixAndEmptySuccess() {
        assertEquals(invalid, parsed("OK:"))
        assertEquals(invalid, parsed("model says yes"))
    }
}
