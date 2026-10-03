import os
import tempfile
import unittest
from unittest.mock import patch
from pathlib import Path

from lain.config import RuntimeConfig
from lain.errors import ErrorCode
from lain.execution.filesystem import execute_file_copy, execute_file_move, execute_file_write_text
from lain.protocol.models import ActionStatus


class FilesystemExecutionTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name).resolve()
        self.config = RuntimeConfig.for_workspace(self.root)

    def tearDown(self):
        self.tmp.cleanup()

    def test_write_text_creates_utf8_file_and_hash_metadata(self):
        outcome = execute_file_write_text({"path": "note.md", "content": "héllo", "overwrite": False}, self.config)
        self.assertEqual(outcome.status, ActionStatus.SUCCESS)
        self.assertEqual((self.root / "note.md").read_text(encoding="utf-8"), "héllo")
        self.assertEqual(outcome.details["size"], len("héllo".encode("utf-8")))
        self.assertEqual(len(outcome.details["sha256"]), 64)

    def test_write_text_falls_back_when_hardlinks_are_unsupported(self):
        import errno
        with patch.object(os, "link", create=True, side_effect=OSError(errno.EPERM, "not permitted")):
            outcome = execute_file_write_text({"path": "no-hardlink.md", "content": "portable", "overwrite": False}, self.config)
        self.assertEqual(outcome.status, ActionStatus.SUCCESS)
        self.assertEqual((self.root / "no-hardlink.md").read_text(), "portable")

    def test_write_text_falls_back_when_hardlinks_are_access_denied(self):
        import errno
        with patch.object(os, "link", create=True, side_effect=OSError(errno.EACCES, "permission denied")):
            outcome = execute_file_write_text({"path": "android-eacces.md", "content": "portable", "overwrite": False}, self.config)
        self.assertEqual(outcome.status, ActionStatus.SUCCESS)
        self.assertEqual((self.root / "android-eacces.md").read_text(), "portable")

    def test_write_text_falls_back_when_os_link_is_missing(self):
        with patch.object(os, "link", new=None, create=True):
            outcome = execute_file_write_text({"path": "missing-link.md", "content": "termux", "overwrite": False}, self.config)
        self.assertEqual(outcome.status, ActionStatus.SUCCESS)
        self.assertEqual((self.root / "missing-link.md").read_text(), "termux")

    def test_write_text_does_not_overwrite_by_default(self):
        (self.root / "note.md").write_text("old", encoding="utf-8")
        outcome = execute_file_write_text({"path": "note.md", "content": "new", "overwrite": False}, self.config)
        self.assertEqual(outcome.status, ActionStatus.FAILURE)
        self.assertEqual(outcome.error_code, ErrorCode.DESTINATION_EXISTS.value)
        self.assertEqual((self.root / "note.md").read_text(), "old")

    def test_write_text_enforces_byte_limit_before_mutation(self):
        config = RuntimeConfig(
            allowed_roots=(self.root,),
            audit_path=self.root / "audit.jsonl",
            max_text_write_bytes=4,
        )
        outcome = execute_file_write_text({"path": "too-big", "content": "12345", "overwrite": False}, config)
        self.assertEqual(outcome.error_code, ErrorCode.ARGUMENT_INVALID.value)
        self.assertFalse((self.root / "too-big").exists())

    def test_copy_preserves_source_and_creates_matching_destination(self):
        source = self.root / "source.bin"
        source.write_bytes(b"abc123")
        outcome = execute_file_copy({"source": "source.bin", "destination": "copy.bin", "overwrite": False}, self.config)
        self.assertEqual(outcome.status, ActionStatus.SUCCESS)
        self.assertEqual(source.read_bytes(), b"abc123")
        self.assertEqual((self.root / "copy.bin").read_bytes(), b"abc123")
        self.assertEqual(outcome.details["sha256_before"], outcome.details["sha256_after"])

    def test_move_removes_source_only_after_destination_published(self):
        source = self.root / "source.bin"
        source.write_bytes(b"abc123")
        outcome = execute_file_move({"source": "source.bin", "destination": "moved.bin", "overwrite": False}, self.config)
        self.assertEqual(outcome.status, ActionStatus.SUCCESS)
        self.assertFalse(source.exists())
        self.assertEqual((self.root / "moved.bin").read_bytes(), b"abc123")

    def test_missing_source_is_structured_failure(self):
        outcome = execute_file_copy({"source": "missing", "destination": "copy", "overwrite": False}, self.config)
        self.assertEqual(outcome.status, ActionStatus.FAILURE)
        self.assertEqual(outcome.error_code, ErrorCode.SOURCE_NOT_FOUND.value)

    def test_destination_parent_must_exist(self):
        outcome = execute_file_write_text({"path": "missing-dir/note", "content": "x", "overwrite": False}, self.config)
        self.assertEqual(outcome.status, ActionStatus.FAILURE)
        self.assertEqual(outcome.error_code, ErrorCode.ARGUMENT_INVALID.value)
        self.assertFalse((self.root / "missing-dir").exists())


if __name__ == "__main__":
    unittest.main()
