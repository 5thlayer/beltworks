# Releases

A release reaches the Pack through the local maven repository (`~/.m2`) at a version of its own. The Pack pins that version and compares the jar's sha256. That only works if the jar published at a version is the one jar that version ever names.

A released version is then uploaded to Modrinth and CurseForge from the maintainer's machine (below). CI builds and tests on every push but never publishes.

## The changelog

Each change players notice adds its line under `## Unreleased` in `CHANGELOG.md` when it lands, written for players rather than as the commit subject. A release ships what Unreleased lists, so the changelog is written as the work is done and never reconstructed from commits.

## Cutting a release

`scripts/release.sh <version>` from a clean main. libworks' ADR 0001 (https://github.com/5thlayer/libworks/blob/main/docs/adr/0001-below-1-0-an-addition-bumps-the-patch.md) sets the semver: below 1.0 only a breaking change bumps the minor version, and an addition or a fix is the next patch. It refuses a version that is already tagged or in `~/.m2`, an empty Unreleased, and a build under `-PsiblingBuilds`. It then:

1. sets `mod_version` and turns `## Unreleased` into `## <version>` under a fresh, empty Unreleased
2. runs the build and the game tests, putting both files back if either fails
3. commits `chore: release <version>`, runs `publishToMavenLocal`, and tags `beltworks-v<version>` with the jar's sha256
4. uploads the jar to Modrinth and CurseForge with `scripts/upload.py` (below). A failed upload leaves the local release and the tag in place; the script names the site that failed, and `scripts/upload.py --site <site> <version>` retries it. `--no-upload` stops before this step and prints the upload command, for the release train, which uploads only once the user says to push. Under `MAVEN_REPO_LOCAL`, a trial run, the upload is only a dry run.

It pushes nothing to git, and ends by printing the push command. The published jar is the `jar` task's, with Groundworks nested under `META-INF/jarjar/`. The Pack loads the Groundworks that Beltworks bundles, so check that `META-INF/jarjar/metadata.json` names the Groundworks and the range you meant to ship.

A release that must reach another Library or the Pack follows the `release-train` skill.

## Uploading to Modrinth and CurseForge

`scripts/upload.py <version>` uploads a version already in `~/.m2` to each site whose project `gradle.properties` names: the jar there, byte for byte, with that version's changelog section as its notes, for Minecraft `minecraft_version` on NeoForge, on client and server, as the release type `upload_release_type` names (`release`, `beta` or `alpha`) or, left empty, as beta below 1.0 and release from it. An unknown type is refused before either site is contacted, and `--dry-run` shows the type it would send.

- `modrinth_project_id` (`p4zxipln`) and `curseforge_project_id` (`1714527`) are the projects; `MODRINTH_PROJECT_ID` and `CURSEFORGE_PROJECT_ID` override them. With neither set, the script refuses and `scripts/release.sh` skips the upload.
- `modrinth_dependencies` (Modrinth project ids) and `curseforge_dependencies` (CurseForge slugs) are the required dependencies, comma separated. Beltworks has none: Groundworks is nested, so neither site lists it.

Each site's token comes from the environment and is never printed. The tokens live in 1Password, and `publish/upload.env` names them there; when a token is missing the upload runs itself again through `op run --env-file=publish/upload.env`, which fills them in for that run only. So `scripts/release.sh <version>` and `scripts/upload.py <version>` need nothing exported; 1Password asks to be unlocked.

- Modrinth: `MODRINTH_TOKEN`, the 1Password item "Beltworks Modrinth": a personal access token with the scopes Create versions, Read versions and Read projects.
- CurseForge: `CURSEFORGE_TOKEN`, the 1Password item "Beltworks CurseForge": an upload API token. The upload API can't list a project's files, so the check for a version CurseForge already has reads the website's own listing (`www.curseforge.com/api/v1/mods/<id>/files`), which needs no key but is undocumented: if it changes, that check fails and the upload stops. It doesn't show a file still under CurseForge's review, so a version is never uploaded again while one waits: 0.3.9 was uploaded by hand on 2026-09-27, and `--site modrinth` is the way to upload it elsewhere. CurseForge needs the environments named too, Client and Server, or it refuses the file.

It refuses, before contacting either site, a version missing from `~/.m2` or from the changelog, and a jar lacking the licensing `checkJarLicensing` requires: `LICENSE`, `NOTICE` crediting Rearth and malcolmriley, `LICENSES/MIT.txt` and `LICENSES/CC-BY-4.0.txt`, and a `LICENSE` in the nested Groundworks. Each site then goes on its own: a site that already has the version, or whose upload fails, is refused without touching the other, and `--site modrinth` or `--site curseforge` retries just that one. `--dry-run` prints the requests and contacts nothing. `MODRINTH_API_URL`, `CURSEFORGE_UPLOAD_URL` and `CURSEFORGE_API_URL` (the listing's site) point it elsewhere, and its tests (`python3 -m unittest discover scripts/tests`) run it against a stand-in server on localhost.

## A published version is final

A version in `~/.m2` never changes. A fix, or a rebuild against another Groundworks, is the next patch version. `publishToMavenLocal` refuses a version that is already there (`build.gradle`), and nothing is deleted or overwritten by hand to get past it. The same holds on Modrinth and CurseForge: `scripts/upload.py` refuses a version a site already has, and nothing there is deleted or replaced.

## Trying an unreleased Groundworks

`-PsiblingBuilds` is for trying a change across Groundworks and Beltworks before Groundworks is released (#54). It includes the Groundworks checkout (`-PgroundworksDir`, default `~/minecraft_mods/groundworks`, the Pack's property and default) as a composite, so the compile, the nested jar and every dev run, `runGameTestServer` included, use the checkout and never `~/.m2`. The build prints one `siblingBuilds:` line naming the checkout, its version and HEAD, and fails if the checkout's version is outside the range Beltworks nests Groundworks under: a Groundworks minor needs that range moved in this checkout too. Under the Pack's `-PsiblingBuilds` this build sees the same properties, so it names the same checkout.

Nothing publishes under it: `publishToMavenLocal` and `scripts/release.sh` refuse. A green run under it proves nothing about a release, which still goes Groundworks, then Beltworks, then the Pack, through release-train. Under the composite the nested jar is named `io.github.5thlayer.groundworks-<minecraft>-<version>.jar`, so read `metadata.json`, not the file name.

## Tags

A Beltworks tag is `beltworks-v<version>`. This repo still carries upstream SimpleBelts' `v0.1.0` to `v0.2.2`, and Beltworks' own versions restarted at 0.1.0, so a bare `v<version>` would collide with them. `git tag -l 'beltworks-v*' -n` lists the releases.

## Releasing an older commit

To release a commit behind main, check it out in a worktree, publish it with `-Pmod_version=<version>` instead of committing the bump, and tag that commit. The tag's message records the command and the jar's sha256. `beltworks-v0.1.1` is one: the #49 commit, the last to nest Groundworks 0.1.

## The stray 0.1.0

The `0.1.0` in `~/.m2` predates these rules and nests Groundworks 0.3. It has no tag, is not a release, and is never pinned.
