---
name: steward
description: How to drive a Beltworks pull request to green, handle its review and merge it.
---

# Stewarding a Beltworks PR

## Before any push

- Run all three of CI's checks locally and push only if they pass:
  `sh ./gradlew build`, `sh ./gradlew runGameTestServer` and
  `python3 -m unittest discover scripts/tests`.
- Commits follow CLAUDE.md's conventional-commit rules.
- A change players notice adds its line under `## Unreleased` in `CHANGELOG.md`.

## Branches

- A branch Claude created is kept current by rebasing onto `main` and pushing
  with `--force-with-lease`. Commits already on `main` drop out in the rebase.
- Never rewrite a branch someone else created.

## CI

- A failing game test is a real failure, never a flake: root-cause it.

## Review

- Fix every finding, optional nits included, in the next push.

## Merging

- Merge with **rebase**: `main` stays linear.
- Claude may merge a PR it drives without asking once:
  1. CI is green on the current head, and there is no conflict;
  2. a code review of the PR at medium effort or higher has run on that head
     and every finding it raised is fixed (a fix is a new head: review again);
  3. no human review thread is open or requesting changes.
- Otherwise the PR waits for the user.

## Never

- Bump `mod_version`, tag, publish or upload from a PR: releases follow
  `docs/agents/releases.md`, on the user's word.
