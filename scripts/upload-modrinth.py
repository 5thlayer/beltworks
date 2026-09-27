#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 5thlayer
# SPDX-License-Identifier: MIT
#
# Upload the released <version> to Modrinth: the jar the local maven repository holds for it, with
# that version's changelog section as its notes.
#
#   scripts/upload-modrinth.py [--dry-run] <version>
#
# $MODRINTH_TOKEN and $MODRINTH_PROJECT_ID name the account and project, and the token is never
# printed. $MAVEN_REPO_LOCAL reads somewhere other than ~/.m2/repository, and $MODRINTH_API_URL
# sends somewhere other than Modrinth, to try the script out. --dry-run prints the requests it
# would make and contacts nothing. The rules it keeps are in docs/agents/releases.md.
import io
import json
import os
import re
import sys
import urllib.error
import urllib.request
import uuid
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
API = "https://api.modrinth.com/v2"
# What checkJarLicensing in build.gradle requires of the jar.
LICENSING = ["LICENSE", "NOTICE", "LICENSES/MIT.txt", "LICENSES/CC-BY-4.0.txt"]
CREDITED = ["Rearth", "malcolmriley"]


def fail(message):
    sys.exit(f"upload-modrinth: {message}")


def properties():
    text = (ROOT / "gradle.properties").read_text()
    return dict(re.findall(r"^(\w+) *= *(.*?)\s*$", text, re.MULTILINE))


def changelog(version):
    """The entries between "## <version>" and the next heading, as release.sh reads Unreleased."""
    lines, on, found = [], False, False
    for line in (ROOT / "CHANGELOG.md").read_text().splitlines():
        if line.startswith("## "):
            on = line == f"## {version}"
            found = found or on
        elif on and line.strip():
            lines.append(line)
    if not found or not lines:
        fail(f"CHANGELOG.md has no entries under \"## {version}\"; the notes are that section.")
    return "\n".join(lines)


def lacking_licensing(data):
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        names = set(z.namelist())
        missing = [name for name in LICENSING if name not in names]
        notice = z.read("NOTICE").decode() if "NOTICE" in names else ""
        missing += [f"NOTICE crediting {who}" for who in CREDITED if who not in notice]
        for name in sorted(names):
            if name.startswith("META-INF/jarjar/") and name.endswith(".jar"):
                with zipfile.ZipFile(io.BytesIO(z.read(name))) as nested:
                    if "LICENSE" not in nested.namelist():
                        missing.append(f"LICENSE in {name}")
    return missing


def multipart(fields):
    """A multipart/form-data body from (name, filename or None, content type, bytes) fields."""
    boundary = uuid.uuid4().hex
    body = io.BytesIO()
    for name, filename, content_type, content in fields:
        disposition = f'form-data; name="{name}"' + (f'; filename="{filename}"' if filename else "")
        body.write(f"--{boundary}\r\nContent-Disposition: {disposition}\r\n"
                   f"Content-Type: {content_type}\r\n\r\n".encode())
        body.write(content + b"\r\n")
    body.write(f"--{boundary}--\r\n".encode())
    return f"multipart/form-data; boundary={boundary}", body.getvalue()


def send(method, url, headers, body=None):
    request = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        fail(f"{method} {url} failed with {error.code}: {error.read().decode(errors='replace')[:500]}")
    except urllib.error.URLError as error:
        fail(f"{method} {url} failed: {error.reason}")


def main(args):
    dry_run = "--dry-run" in args
    args = [a for a in args if a != "--dry-run"]
    if len(args) != 1 or not re.fullmatch(r"\d+\.\d+\.\d+", args[0]):
        fail("usage: scripts/upload-modrinth.py [--dry-run] <major.minor.patch>")
    version = args[0]

    token = os.environ.get("MODRINTH_TOKEN")
    project = os.environ.get("MODRINTH_PROJECT_ID")
    for name, value in [("MODRINTH_TOKEN", token), ("MODRINTH_PROJECT_ID", project)]:
        if not value:
            fail(f"${name} is not set; export it where the release runs.")
    api = os.environ.get("MODRINTH_API_URL", API).rstrip("/")

    props = properties()
    group, artifact = props["maven_group"], props["archives_name"]
    repo = Path(os.environ.get("MAVEN_REPO_LOCAL") or Path.home() / ".m2/repository")
    jar = repo / group.replace(".", "/") / artifact / version / f"{artifact}-{version}.jar"
    if not jar.is_file():
        fail(f"{version} is not in {repo}; only a released version is uploaded.")
    data = jar.read_bytes()
    missing = lacking_licensing(data)
    if missing:
        fail(f"{jar.name} lacks its licensing: {', '.join(missing)}")

    metadata = {
        "name": f"{props['mod_name']} {version}",
        "version_number": version,
        "changelog": changelog(version),
        "dependencies": [],  # Groundworks is nested in the jar, not a separate download.
        "game_versions": [props["minecraft_version"]],
        "version_type": "beta" if version.startswith("0.") else "release",
        "loaders": ["neoforge"],
        "featured": True,
        "project_id": project,
        "file_parts": ["file"],
        "primary_file": "file",
    }
    headers = {"Authorization": token, "User-Agent": f"5thlayer/{artifact}/{version}"}
    listing = f"{api}/project/{project}/version"
    content_type, body = multipart([
        ("data", None, "application/json", json.dumps(metadata).encode()),
        ("file", jar.name, "application/java-archive", data),
    ])

    if dry_run:
        shown = {**headers, "Authorization": "<redacted>"}
        print(f"GET {listing}")
        print(f"POST {api}/version")
        for name, value in shown.items():
            print(f"  {name}: {value}")
        print(f"  data: {json.dumps(metadata, indent=2)}")
        print(f"  file: {jar.name} ({len(data)} bytes) from {jar}")
        return

    # A published version is final on Modrinth too: it is never replaced.
    if any(v.get("version_number") == version for v in send("GET", listing, headers)):
        fail(f"Modrinth already has {version} in {project}, and a published version never changes.")
    created = send("POST", f"{api}/version", {**headers, "Content-Type": content_type}, body)
    print(f"Uploaded {jar.name} to Modrinth as {version} ({created.get('id')})")


if __name__ == "__main__":
    main(sys.argv[1:])
