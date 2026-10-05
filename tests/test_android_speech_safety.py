"""Static Android speech safety contracts which must hold on every supported API path."""
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
BACKEND = ROOT / "android" / "app" / "src" / "main" / "java" / "dev" / "lain" / "os" / "voice" / "AndroidOnDeviceSpeechBackend.kt"


class AndroidSpeechSafetySourceTests(unittest.TestCase):
    def test_captured_pcm_support_is_checked_before_start_listening(self):
        source = BACKEND.read_text(encoding="utf-8")
        entry = source.index("private fun startRecognitionApi33")
        support = source.index("checkRecognitionSupport(", entry)
        start = source.index("startListening(", entry)

        self.assertLess(
            support,
            start,
            "captured PCM must be positively supported for the exact recognizer intent before listening starts",
        )

    def test_support_check_failure_fails_closed(self):
        source = BACKEND.read_text(encoding="utf-8")
        entry = source.index("private fun startRecognitionApi33")
        support = source.index("checkRecognitionSupport(", entry)
        callback = source.index("RecognitionSupportCallback", support)
        error = source.index("override fun onError", callback)
        begin = source.index("beginListeningApi33", callback)

        self.assertLess(error, begin)
        error_block = source[error:begin]
        self.assertIn("PROVIDER_UNAVAILABLE", error_block)


if __name__ == "__main__":
    unittest.main()
