import json
import tempfile
import unittest
from pathlib import Path

from lain.audit.logger import AuditLogger, redact


class AuditTests(unittest.TestCase):
    def test_redacts_secret_like_keys_recursively(self):
        value = {
            "token": "abc",
            "nested": [{"api_key": "123", "safe": "ok"}],
            "Authorization": "Bearer nope",
            "cookieJar": "hidden",
            "safe": "visible",
        }
        redacted = redact(value)
        self.assertEqual(redacted["token"], "[REDACTED]")
        self.assertEqual(redacted["nested"][0]["api_key"], "[REDACTED]")
        self.assertEqual(redacted["Authorization"], "[REDACTED]")
        self.assertEqual(redacted["cookieJar"], "[REDACTED]")
        self.assertEqual(redacted["safe"], "visible")

    def test_redacts_arbitrary_content_bodies(self):
        redacted = redact({"arguments": {"path": "note.md", "content": "private body"}})
        self.assertEqual(redacted["arguments"]["content"], "[REDACTED]")
        self.assertEqual(redacted["arguments"]["path"], "note.md")

    def test_sms_content_and_recipient_redact_from_audit(self):
        value = {"action": {"type": "android.sms.compose",
                            "arguments": {"recipient": "+15551234567", "content": "Private"}}}
        scrubbed = redact(value)
        self.assertEqual(scrubbed["action"]["arguments"]["content"], "[REDACTED]")
        self.assertEqual(scrubbed["action"]["arguments"]["recipient"], "[REDACTED]")

    def test_append_writes_one_json_object_per_line(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp, "audit.jsonl")
            logger = AuditLogger(path)
            logger.append({"request_id": "r1", "execution_attempted": True})
            logger.append({"request_id": "r2", "execution_attempted": False})
            lines = path.read_text().splitlines()
            self.assertEqual(len(lines), 2)
            self.assertEqual(json.loads(lines[0])["request_id"], "r1")

    def test_has_executed_request_ignores_corrupt_lines_and_non_attempts(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp, "audit.jsonl")
            path.write_text('{bad json\n{"request_id":"r1","execution_attempted":false}\n')
            logger = AuditLogger(path)
            self.assertFalse(logger.has_executed_request("r1"))
            logger.append({"request_id": "r1", "execution_attempted": True})
            self.assertTrue(logger.has_executed_request("r1"))


if __name__ == "__main__":
    unittest.main()


class PerActionAuditTests(unittest.TestCase):
    def test_has_executed_action_is_scoped_to_request_and_action(self):
        with tempfile.TemporaryDirectory() as tmp:
            logger = AuditLogger(Path(tmp, "audit.jsonl"))
            logger.append({"request_id": "r1", "action_id": "a1", "execution_attempted": True})
            logger.append({"request_id": "r1", "action_id": "a2", "execution_attempted": False})
            logger.append({"request_id": "r2", "action_id": "a1", "execution_attempted": True})
            self.assertTrue(logger.has_executed_action("r1", "a1"))
            self.assertFalse(logger.has_executed_action("r1", "a2"))
            self.assertFalse(logger.has_executed_action("r1", "missing"))
