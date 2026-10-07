import copy
import json
import tempfile
import unittest
from pathlib import Path

from scripts.verify import check_release_state


BASE_RELEASE_STATE = {
    "version": 1,
    "package": {
        "python": {"name": "lain-local", "version": "0.0.1"},
        "android": {
            "application_id": "dev.lain.os",
            "compile_sdk": 35,
            "min_sdk": 24,
            "target_sdk": 35,
            "version_code": 5,
            "version_name": "0.1.4-interface",
        },
    },
    "tasks": {"TASK-015": "In Progress"},
    "evidence": {
        "phase2_voice_physical": {
            "required_class": "physical",
            "recorded_classes": ["automated", "emulator"],
            "status": "UNVERIFIED",
        }
    },
}


class ReleaseStateVerifierTest(unittest.TestCase):
    def make_repo(self, state=None):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        root = Path(temp.name)

        (root / "android/app").mkdir(parents=True)
        (root / ".tasks").mkdir()
        (root / "release").mkdir()

        (root / "pyproject.toml").write_text(
            '[project]\nname = "lain-local"\nversion = "0.0.1"\n',
            encoding="utf-8",
        )
        (root / "android/app/build.gradle.kts").write_text(
            '''android {
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.lain.os"
        minSdk = 24
        targetSdk = 35
        versionCode = 5
        versionName = "0.1.4-interface"
    }
}
''',
            encoding="utf-8",
        )
        (root / ".tasks/config.json").write_text(
            json.dumps(
                {
                    "states": [
                        {"name": "Next", "fileName": "NEXT.md"},
                        {"name": "In Progress", "fileName": "IN_PROGRESS.md"},
                        {"name": "Done", "fileName": "DONE.md"},
                    ]
                }
            ),
            encoding="utf-8",
        )
        (root / ".tasks/NEXT.md").write_text("# Next\n", encoding="utf-8")
        (root / ".tasks/IN_PROGRESS.md").write_text(
            "# In Progress\n\n## TASK-015: Voice acceptance\n",
            encoding="utf-8",
        )
        (root / ".tasks/DONE.md").write_text("# Done\n", encoding="utf-8")
        (root / "release/state.json").write_text(
            json.dumps(state or BASE_RELEASE_STATE),
            encoding="utf-8",
        )
        return root

    def test_stale_task_state_names_claim_and_authoritative_source(self):
        state = copy.deepcopy(BASE_RELEASE_STATE)
        state["tasks"]["TASK-015"] = "Done"
        root = self.make_repo(state)

        violations = check_release_state(root)

        self.assertTrue(
            any(
                "TASK-015" in item
                and "claimed Done" in item
                and ".tasks/IN_PROGRESS.md" in item
                for item in violations
            ),
            violations,
        )

    def test_package_and_android_metadata_mismatch_are_rejected(self):
        state = copy.deepcopy(BASE_RELEASE_STATE)
        state["package"]["python"]["version"] = "9.9.9"
        state["package"]["android"]["min_sdk"] = 35
        root = self.make_repo(state)

        violations = check_release_state(root)

        self.assertTrue(
            any("python.version" in item and "pyproject.toml" in item for item in violations),
            violations,
        )
        self.assertTrue(
            any("android.min_sdk" in item and "build.gradle.kts" in item for item in violations),
            violations,
        )

    def test_emulator_evidence_cannot_satisfy_physical_acceptance(self):
        state = copy.deepcopy(BASE_RELEASE_STATE)
        state["evidence"]["phase2_voice_physical"]["status"] = "PASSED"
        root = self.make_repo(state)

        violations = check_release_state(root)

        self.assertTrue(
            any(
                "phase2_voice_physical" in item
                and "physical" in item
                and "emulator" in item
                for item in violations
            ),
            violations,
        )

    def test_unstructured_prose_is_not_treated_as_authority(self):
        root = self.make_repo()
        (root / "README.md").write_text(
            "TASK-015 is Done. Physical acceptance passed. minSdk is 99.\n",
            encoding="utf-8",
        )

        self.assertEqual([], check_release_state(root))

    def test_current_repository_release_state_matches_machine_sources(self):
        root = Path(__file__).resolve().parents[1]
        self.assertEqual([], check_release_state(root))


if __name__ == "__main__":
    unittest.main()
