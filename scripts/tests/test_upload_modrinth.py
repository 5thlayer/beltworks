# SPDX-FileCopyrightText: 2026 5thlayer
# SPDX-License-Identifier: MIT
#
# Drives scripts/upload-modrinth.py as a person does, a version, the environment and a maven
# repository holding a jar, against the local stand-in, and checks only what reaches it and the
# exit status. Run with: python3 -m unittest discover scripts/tests
import io
import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

from standin import StandIn

SCRIPT = Path(__file__).resolve().parents[1] / "upload-modrinth.py"
TOKEN = "mrp_standin-secret-token"
PROJECT = "beltworks-standin"

CHANGELOG = """# Changelog

## Unreleased

- Not yet released.

## 0.3.9

- The jar carries its licensing.
- It nests Groundworks 0.4.6.

## 0.3.8

- Older.
"""

PROPERTIES = """mod_name = Beltworks
mod_version = 0.3.9
maven_group = io.github.5thlayer
archives_name = beltworks
minecraft_version = 26.1.2
"""


def jar(entries):
    out = io.BytesIO()
    with zipfile.ZipFile(out, "w") as z:
        for name, data in entries.items():
            z.writestr(name, data)
    return out.getvalue()


def licensed(**overrides):
    entries = {
        "LICENSE": "MIT", "NOTICE": "Credits Rearth and malcolmriley.",
        "LICENSES/MIT.txt": "MIT", "LICENSES/CC-BY-4.0.txt": "CC BY",
        "META-INF/jarjar/groundworks.jar": jar({"LICENSE": "MIT"}),
        "io/github/beltworks/Beltworks.class": b"\xca\xfe\xba\xbe",
    }
    entries.update(overrides)
    return jar({k: v for k, v in entries.items() if v is not None})


class UploadModrinth(unittest.TestCase):
    def setUp(self):
        self.site = StandIn()
        self.addCleanup(self.site.close)
        self.root = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.root)
        # The script reads the changelog and gradle.properties of the checkout it sits in.
        (self.root / "scripts").mkdir()
        shutil.copy(SCRIPT, self.root / "scripts")
        (self.root / "CHANGELOG.md").write_text(CHANGELOG)
        (self.root / "gradle.properties").write_text(PROPERTIES)
        self.maven = self.root / "m2"
        self.env = {"PATH": os.environ["PATH"], "MAVEN_REPO_LOCAL": str(self.maven),
                    "MODRINTH_TOKEN": TOKEN, "MODRINTH_PROJECT_ID": PROJECT,
                    "MODRINTH_API_URL": self.site.url + "/v2"}

    def publish(self, version, data=None):
        folder = self.maven / "io/github/5thlayer/beltworks" / version
        folder.mkdir(parents=True)
        data = licensed() if data is None else data
        (folder / f"beltworks-{version}.jar").write_bytes(data)
        return data

    def upload(self, *args, **env):
        environment = {**self.env, **env}
        environment = {k: v for k, v in environment.items() if v is not None}
        result = subprocess.run([sys.executable, str(self.root / "scripts/upload-modrinth.py"), *args],
                                env=environment, capture_output=True, text=True)
        self.assertNotIn(TOKEN, result.stdout + result.stderr)
        return result

    def sent(self):
        posts = [r for r in self.site.requests if r.method == "POST"]
        self.assertEqual(len(posts), 1)
        return posts[0]

    def test_sends_the_jar_in_the_maven_repository_byte_for_byte(self):
        data = self.publish("0.3.9")
        result = self.upload("0.3.9")
        self.assertEqual(result.returncode, 0, result.stderr)
        parts = self.sent().parts()
        self.assertEqual(parts["file"][0], data)
        self.assertEqual(parts["file"][1], "beltworks-0.3.9.jar")

    def test_authenticates_with_the_token(self):
        self.publish("0.3.9")
        self.upload("0.3.9")
        self.assertEqual(self.sent().headers["Authorization"], TOKEN)

    def test_the_notes_are_the_changelog_section_and_the_metadata_names_the_game(self):
        self.publish("0.3.9")
        self.upload("0.3.9")
        data = json.loads(self.sent().parts()["data"][0])
        self.assertEqual(data["changelog"], "- The jar carries its licensing.\n- It nests Groundworks 0.4.6.")
        self.assertEqual(data["project_id"], PROJECT)
        self.assertEqual(data["version_number"], "0.3.9")
        self.assertEqual(data["game_versions"], ["26.1.2"])
        self.assertEqual(data["loaders"], ["neoforge"])
        self.assertEqual(data["version_type"], "beta")
        self.assertEqual(data["dependencies"], [])
        self.assertEqual(data["file_parts"], ["file"])

    def test_a_release_from_1_0_is_a_release(self):
        (self.root / "CHANGELOG.md").write_text("## 1.0.0\n\n- Stable.\n")
        self.publish("1.0.0")
        self.assertEqual(self.upload("1.0.0").returncode, 0)
        self.assertEqual(json.loads(self.sent().parts()["data"][0])["version_type"], "release")

    def test_the_maven_repository_defaults_to_the_home_one(self):
        home = self.root / "home"
        self.maven = home / ".m2/repository"
        data = self.publish("0.3.9")
        self.assertEqual(self.upload("0.3.9", MAVEN_REPO_LOCAL=None, HOME=str(home)).returncode, 0)
        self.assertEqual(self.sent().parts()["file"][0], data)

    def assertRefusedBeforeAnyRequest(self, result, message):
        self.assertNotEqual(result.returncode, 0)
        self.assertIn(message, result.stderr)
        self.assertEqual(self.site.requests, [])

    def test_refuses_a_version_missing_from_the_maven_repository(self):
        self.assertRefusedBeforeAnyRequest(self.upload("0.3.9"), "not in")

    def test_refuses_a_version_the_changelog_lacks(self):
        self.publish("0.3.7")
        self.assertRefusedBeforeAnyRequest(self.upload("0.3.7"), "CHANGELOG.md")

    def test_refuses_a_jar_that_lacks_its_licensing(self):
        for lacking, entry in [("LICENSE", {"LICENSE": None}), ("NOTICE", {"NOTICE": None}),
                               ("LICENSES/CC-BY-4.0.txt", {"LICENSES/CC-BY-4.0.txt": None}),
                               ("malcolmriley", {"NOTICE": "Credits Rearth."}),
                               ("groundworks.jar", {"META-INF/jarjar/groundworks.jar": jar({"x": "y"})})]:
            with self.subTest(lacking):
                shutil.rmtree(self.maven, ignore_errors=True)
                self.publish("0.3.9", licensed(**entry))
                self.assertRefusedBeforeAnyRequest(self.upload("0.3.9"), lacking)

    def test_refuses_a_version_modrinth_already_has(self):
        self.publish("0.3.9")
        self.site.versions[PROJECT] = ["0.3.9"]
        result = self.upload("0.3.9")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("already has 0.3.9", result.stderr)
        self.assertEqual([r.method for r in self.site.requests], ["GET"])

    def test_a_missing_token_or_project_id_fails_clearly(self):
        self.publish("0.3.9")
        for name in ["MODRINTH_TOKEN", "MODRINTH_PROJECT_ID"]:
            with self.subTest(name):
                self.assertRefusedBeforeAnyRequest(self.upload("0.3.9", **{name: None}), name)
                self.assertRefusedBeforeAnyRequest(self.upload("0.3.9", **{name: ""}), name)

    def test_a_dry_run_prints_the_requests_and_contacts_nothing(self):
        self.publish("0.3.9")
        result = self.upload("--dry-run", "0.3.9")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.site.requests, [])
        self.assertIn(f"GET {self.site.url}/v2/project/{PROJECT}/version", result.stdout)
        self.assertIn(f"POST {self.site.url}/v2/version", result.stdout)
        self.assertIn("Authorization: <redacted>", result.stdout)
        self.assertIn("beltworks-0.3.9.jar", result.stdout)
        self.assertIn('"version_type": "beta"', result.stdout)

    def test_a_site_error_fails_without_the_token(self):
        self.publish("0.3.9")
        self.env["MODRINTH_API_URL"] = self.site.url + "/elsewhere"
        result = self.upload("0.3.9")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("404", result.stderr)


if __name__ == "__main__":
    unittest.main()
