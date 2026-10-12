import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from zipfile import ZipFile


class AndroidArtifactTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.apk = self.root / "app-debug.apk"
        self.metadata = self.root / "output-metadata.json"
        self.metadata.write_text(json.dumps({
            "applicationId": "dev.lain.os", "variantName": "debug", "elements": [{
                "outputFile": self.apk.name, "versionCode": 5, "versionName": "0.1.4-interface",
            }],
        }))
        self.package = {"application_id": "dev.lain.os", "version_code": 5,
                        "version_name": "0.1.4-interface", "min_sdk": 24,
                        "target_sdk": 35, "compile_sdk": 35}
        self.write_apk()

    def write_apk(self, abis=("arm64-v8a", "x86_64")):
        with ZipFile(self.apk, "w") as archive:
            archive.writestr("AndroidManifest.xml", "fixture manifest")
            archive.writestr("classes.dex", "fixture dex")
            for abi in abis:
                archive.writestr(f"lib/{abi}/liblain_gguf_probe.so", "fixture JNI")
                archive.writestr(f"lib/{abi}/libpython3.11.so", "fixture Python")

    def test_manifest_identifies_binary_source_and_embedded_runtime(self):
        from scripts.android_artifact import describe_debug_apk
        manifest = describe_debug_apk(self.apk, self.metadata, self.package, "a" * 40, "123")
        self.assertEqual(manifest["sha256"], hashlib.sha256(self.apk.read_bytes()).hexdigest())
        self.assertEqual(manifest["source_commit"], "a" * 40)
        self.assertEqual(manifest["workflow_run_id"], "123")
        self.assertEqual(manifest["abis"], ["arm64-v8a", "x86_64"])
        self.assertEqual(manifest["variant"], "debug")
        self.assertEqual(manifest["application_id"], "dev.lain.os")

    def test_wrong_variant_or_stale_package_metadata_is_rejected(self):
        from scripts.android_artifact import describe_debug_apk
        original = json.loads(self.metadata.read_text())
        for key, value in (("variantName", "release"), ("applicationId", "another.app")):
            self.metadata.write_text(json.dumps({**original, key: value}))
            with self.subTest(key=key), self.assertRaises(ValueError):
                describe_debug_apk(self.apk, self.metadata, self.package, "a" * 40)
        original["elements"][0]["versionCode"] = 4
        self.metadata.write_text(json.dumps(original))
        with self.assertRaises(ValueError):
            describe_debug_apk(self.apk, self.metadata, self.package, "a" * 40)

    def test_missing_abi_runtime_or_invalid_source_commit_is_rejected(self):
        from scripts.android_artifact import describe_debug_apk
        with self.assertRaises(ValueError):
            describe_debug_apk(self.apk, self.metadata, self.package, "main")
        self.write_apk(abis=("arm64-v8a",))
        with self.assertRaises(ValueError):
            describe_debug_apk(self.apk, self.metadata, self.package, "a" * 40)
