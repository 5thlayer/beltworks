# SPDX-FileCopyrightText: 2026 5thlayer
# SPDX-License-Identifier: MIT
#
# Drives scripts/upload.py as a person does, a version, the environment and a maven repository
# holding a jar, against the local stand-in, and checks only what reaches it and the exit status.
# Run with: python3 -m unittest discover scripts/tests
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

SCRIPT = Path(__file__).resolve().parents[1] / "upload.py"
SECRETS = {"MODRINTH_TOKEN": "mrp_standin-secret-token", "CURSEFORGE_TOKEN": "cf-upload-secret-token",
           "CURSEFORGE_API_KEY": "cf-core-secret-key"}
MODRINTH_PROJECT = "beltworks-standin"
CF_PROJECT = "123456"
NOTES = "- The jar carries its licensing.\n- It nests Groundworks 0.4.6."

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


class Upload(unittest.TestCase):
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
        self.env = {"PATH": os.environ["PATH"], "MAVEN_REPO_LOCAL": str(self.maven), **SECRETS,
                    "MODRINTH_PROJECT_ID": MODRINTH_PROJECT, "MODRINTH_API_URL": self.site.url + "/modrinth",
                    "CURSEFORGE_PROJECT_ID": CF_PROJECT, "CURSEFORGE_UPLOAD_URL": self.site.url + "/cf-upload",
                    "CURSEFORGE_API_URL": self.site.url + "/cf-core"}

    def publish(self, version, data=None):
        folder = self.maven / "io/github/5thlayer/beltworks" / version
        folder.mkdir(parents=True)
        data = licensed() if data is None else data
        (folder / f"beltworks-{version}.jar").write_bytes(data)
        return data

    def upload(self, *args, **env):
        environment = {**self.env, **env}
        environment = {k: v for k, v in environment.items() if v is not None}
        result = subprocess.run([sys.executable, str(self.root / "scripts/upload.py"), *args],
                                env=environment, capture_output=True, text=True)
        for secret in SECRETS.values():
            self.assertNotIn(secret, result.stdout + result.stderr)
        return result

    def modrinth_post(self):
        posts = self.site.sent("POST", "/modrinth/")
        self.assertEqual(len(posts), 1)
        return posts[0]

    def curseforge_post(self):
        posts = self.site.sent("POST", "/cf-upload/")
        self.assertEqual(len(posts), 1)
        return posts[0]

    def assertRefusedBeforeAnyRequest(self, result, message):
        self.assertNotEqual(result.returncode, 0)
        self.assertIn(message, result.stderr)
        self.assertEqual(self.site.requests, [])

    # Both sites

    def test_both_sites_get_the_jar_in_the_maven_repository_byte_for_byte(self):
        data = self.publish("0.3.9")
        result = self.upload("0.3.9")
        self.assertEqual(result.returncode, 0, result.stderr)
        for post in [self.modrinth_post(), self.curseforge_post()]:
            self.assertEqual(post.parts()["file"], (data, "beltworks-0.3.9.jar"))

    def test_the_maven_repository_defaults_to_the_home_one(self):
        home = self.root / "home"
        self.maven = home / ".m2/repository"
        data = self.publish("0.3.9")
        self.assertEqual(self.upload("0.3.9", MAVEN_REPO_LOCAL=None, HOME=str(home)).returncode, 0)
        self.assertEqual(self.modrinth_post().parts()["file"][0], data)
        self.assertEqual(self.curseforge_post().parts()["file"][0], data)

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

    def test_a_missing_token_key_or_project_id_fails_clearly_and_leaves_the_other_site(self):
        self.publish("0.3.9")
        curseforge, modrinth = ("cf-core", "cf-upload"), ("modrinth",)
        for name, skipped, other in [("MODRINTH_TOKEN", modrinth, "/cf-upload/"),
                                     ("MODRINTH_PROJECT_ID", modrinth, "/cf-upload/"),
                                     ("CURSEFORGE_TOKEN", curseforge, "/modrinth/"),
                                     ("CURSEFORGE_PROJECT_ID", curseforge, "/modrinth/"),
                                     ("CURSEFORGE_API_KEY", curseforge, "/modrinth/")]:
            for unset in [None, ""]:
                with self.subTest(name, unset=unset):
                    self.site.requests.clear()
                    self.site.modrinth.clear()
                    self.site.curseforge.clear()
                    result = self.upload("0.3.9", **{name: unset})
                    self.assertNotEqual(result.returncode, 0)
                    self.assertIn(name, result.stderr)
                    self.assertFalse({r.path.split("/")[1] for r in self.site.requests} & set(skipped))
                    self.assertEqual(len(self.site.sent("POST", other)), 1)

    def test_a_failure_on_one_site_leaves_the_other_and_it_can_be_retried_alone(self):
        self.publish("0.3.9")
        self.site.broken = {"modrinth"}
        result = self.upload("0.3.9")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("500", result.stderr)
        self.assertEqual(len(self.site.sent("POST", "/cf-upload/")), 1)

        self.site.broken = set()
        self.site.requests.clear()
        result = self.upload("--site", "modrinth", "0.3.9")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual({r.path.split("/")[1] for r in self.site.requests}, {"modrinth"})
        self.modrinth_post()

    def test_a_failure_on_curseforge_leaves_modrinth(self):
        self.publish("0.3.9")
        self.site.broken = {"cf-upload"}
        result = self.upload("0.3.9")
        self.assertNotEqual(result.returncode, 0)
        self.modrinth_post()

    def test_refuses_an_unknown_site(self):
        self.publish("0.3.9")
        self.assertRefusedBeforeAnyRequest(self.upload("--site", "planetminecraft", "0.3.9"), "usage")

    def test_a_dry_run_prints_the_requests_for_both_sites_and_contacts_nothing(self):
        self.publish("0.3.9")
        result = self.upload("--dry-run", "0.3.9")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.site.requests, [])
        for line in [f"GET {self.site.url}/modrinth/project/{MODRINTH_PROJECT}/version",
                     f"POST {self.site.url}/modrinth/version",
                     "Authorization: <redacted>",
                     f"GET {self.site.url}/cf-core/v1/mods/{CF_PROJECT}/files",
                     "x-api-key: <redacted>",
                     f"GET {self.site.url}/cf-upload/api/game/versions",
                     f"POST {self.site.url}/cf-upload/api/projects/{CF_PROJECT}/upload-file",
                     "X-Api-Token: <redacted>",
                     "beltworks-0.3.9.jar", '"version_type": "beta"', '"releaseType": "beta"']:
            self.assertIn(line, result.stdout)

    # Modrinth

    def test_modrinth_authenticates_with_the_token(self):
        self.publish("0.3.9")
        self.upload("0.3.9")
        self.assertEqual(self.modrinth_post().headers["Authorization"], SECRETS["MODRINTH_TOKEN"])

    def test_modrinth_gets_the_changelog_section_and_the_game(self):
        self.publish("0.3.9")
        self.upload("0.3.9")
        data = json.loads(self.modrinth_post().parts()["data"][0])
        self.assertEqual(data["changelog"], NOTES)
        self.assertEqual(data["project_id"], MODRINTH_PROJECT)
        self.assertEqual(data["version_number"], "0.3.9")
        self.assertEqual(data["game_versions"], ["26.1.2"])
        self.assertEqual(data["loaders"], ["neoforge"])
        self.assertEqual(data["version_type"], "beta")
        self.assertEqual(data["dependencies"], [])
        self.assertEqual(data["file_parts"], ["file"])

    def test_a_release_from_1_0_is_a_release_on_both_sites(self):
        (self.root / "CHANGELOG.md").write_text("## 1.0.0\n\n- Stable.\n")
        self.publish("1.0.0")
        self.assertEqual(self.upload("1.0.0").returncode, 0)
        self.assertEqual(json.loads(self.modrinth_post().parts()["data"][0])["version_type"], "release")
        self.assertEqual(json.loads(self.curseforge_post().parts()["metadata"][0])["releaseType"], "release")

    def test_refuses_a_version_modrinth_already_has(self):
        self.publish("0.3.9")
        self.site.modrinth[MODRINTH_PROJECT] = ["0.3.9"]
        result = self.upload("--site", "modrinth", "0.3.9")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Modrinth already has 0.3.9", result.stderr)
        self.assertEqual([r.method for r in self.site.requests], ["GET"])

    def test_a_modrinth_error_fails_the_site(self):
        self.publish("0.3.9")
        result = self.upload("--site", "modrinth", "0.3.9", MODRINTH_API_URL=self.site.url + "/elsewhere")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("404", result.stderr)

    # CurseForge

    def test_curseforge_authenticates_the_upload_with_the_token_and_the_listing_with_the_key(self):
        self.publish("0.3.9")
        self.upload("0.3.9")
        self.assertEqual(self.curseforge_post().headers["X-Api-Token"], SECRETS["CURSEFORGE_TOKEN"])
        for listing in self.site.sent("GET", "/cf-core/"):
            self.assertEqual(listing.headers["x-api-key"], SECRETS["CURSEFORGE_API_KEY"])

    def test_curseforge_gets_the_changelog_section_and_the_game(self):
        self.publish("0.3.9")
        self.upload("0.3.9")
        metadata = json.loads(self.curseforge_post().parts()["metadata"][0])
        self.assertEqual(metadata["changelog"], NOTES)
        self.assertEqual(metadata["changelogType"], "markdown")
        self.assertEqual(metadata["displayName"], "Beltworks 0.3.9")
        # 26.1.2 the Minecraft version, not the Bukkit one, and NeoForge the loader.
        self.assertEqual(sorted(metadata["gameVersions"]), [101, 301])
        self.assertEqual(metadata["releaseType"], "beta")
        self.assertNotIn("relations", metadata)

    def test_refuses_a_version_curseforge_already_has(self):
        self.publish("0.3.9")
        # Past the first page of the listing.
        self.site.curseforge[CF_PROJECT] = ["beltworks-0.3.6.jar", "beltworks-0.3.7.jar", "beltworks-0.3.9.jar"]
        result = self.upload("--site", "curseforge", "0.3.9")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("CurseForge already has 0.3.9", result.stderr)
        self.assertEqual(self.site.sent("POST", "/cf-upload/"), [])

    def test_refuses_a_version_curseforge_has_under_another_file_name(self):
        self.publish("0.3.9")
        self.site.curseforge[CF_PROJECT] = ["Beltworks 0.3.9"]
        result = self.upload("--site", "curseforge", "0.3.9")
        self.assertIn("CurseForge already has 0.3.9", result.stderr)
        self.assertEqual(self.site.sent("POST", "/cf-upload/"), [])

    def test_uploads_a_version_curseforge_lacks_among_others(self):
        self.publish("0.3.9")
        self.site.curseforge[CF_PROJECT] = ["beltworks-0.3.6.jar", "beltworks-0.3.7.jar", "beltworks-0.3.8.jar"]
        self.assertEqual(self.upload("--site", "curseforge", "0.3.9").returncode, 0)
        self.curseforge_post()

    def test_refuses_a_game_version_curseforge_does_not_know(self):
        self.publish("0.3.9")
        (self.root / "gradle.properties").write_text(PROPERTIES.replace("26.1.2", "27.0.0"))
        result = self.upload("--site", "curseforge", "0.3.9")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("27.0.0", result.stderr)
        self.assertEqual(self.site.sent("POST", "/cf-upload/"), [])


if __name__ == "__main__":
    unittest.main()
