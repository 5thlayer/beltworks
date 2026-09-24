# Code is MIT, assets are CC BY 4.0, and Upstream's code is kept rather than rewritten

Beltworks licenses its own code under MIT and its own assets under CC BY 4.0. Upstream's code and textures stay CC BY 4.0 because the original license always applies to them, so each file is marked with what it contains: SPDX headers in Java and `REUSE.toml` globs for everything else. A Java file that still carries Upstream's work is `CC-BY-4.0 AND MIT` until none of those lines remain. MIT keeps friction low for the packs and addon authors that "works with any tech mod" depends on. It also narrows what is at stake if copyright in the AI co-authored code turns out to be thin. Assets follow the art they sit beside, so a texture has one license whatever its origin, and reworking an Upstream texture never changes its license.

## Considered Options

- **CC BY 4.0 for everything.** Uniform and needs no per-file marking, but CC advises against it for software and its fit with the GPL is disputed.
- **LGPL-3.0 or MPL-2.0.** Copyleft on the Mod's files. Rejected because every redistributor, including modpacks, would owe a pointer to the source.
- **All rights reserved.** Rejected because packs could not redistribute the Mod unless it came from a host.
- **Rewrite Upstream's remaining code to get a single license.** Rejected because CC BY is already permissive. A rewrite driven only by licensing buys almost nothing, and Upstream's share (about 21% of lines at the time of this decision) shrinks as features replace it.
