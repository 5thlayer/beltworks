# Licensing facts: CC BY 4.0 mixing and Minecraft mod licenses

Research for [#5](https://github.com/adamico/SimpleBelts/issues/5). Sources were read on 2026-09-24. Every claim links to a source in [Sources](#sources) by number, for example [1].

This file collects facts from the license texts, the licensors' own FAQs and the hosting platforms' published rules. It is not legal advice. Where two primary sources disagree, both are quoted and the disagreement is left open.

**Question.** What does CC BY 4.0 require of a project that keeps some Upstream code and textures and licenses its own code differently? That covers the attribution form, marking changes, no implied endorsement, and whether code and assets can carry different licenses. What are the common licenses for Minecraft mods (MIT, LGPL-3.0, MPL-2.0, ARR, and so on), and what does each imply for Modrinth and CurseForge hosting and for redistribution in modpacks?

## Summary

- **Attribution (CC BY 4.0 §3(a)(1)).** Anyone who *Shares* the material must keep the creator's name, the copyright notice, the license notice, the warranty-disclaimer notice and a link to the material, but only the items the licensor supplied. They must also say that the material was modified, keep any earlier notes about modifications, and name CC BY 4.0 with its text or a link. Any reasonable manner is enough, including a link to a page that holds the information (§3(a)(2)) [1].
- **What the licensors supplied.** Upstream supplies a name (`Rearth` / `rearth`), the CC BY 4.0 legal code (which contains the disclaimer), and a source URL. It supplies **no copyright notice**. Its README credit that the textures are "based on modified assets from" malcolmriley/unused-textures is an earlier note about modifications, and §3(a)(1)(B) requires keeping it. unused-textures supplies the author `malcolmriley`, a CC BY 4.0 statement and a LICENSE file, and also no copyright notice. See [§1](#1-what-the-mod-contains-today).
- **Marking changes is mandatory in 4.0 for any modification, even a small one.** Trivial fixes are exempt [1][3][5].
- **No endorsement and names.** CC BY does not permit implying a connection with the licensor (§2(a)(6)), and it licenses no trademark rights (§2(b)(2)) [1]. Modrinth's rules forbid misrepresenting affiliation [18]. CurseForge's policies require a fork to have a "distinct" name and to credit and link the original [23]. Upstream is published as **"Simple Conveyor Belts"**, with repo `SimpleBelts` and mod id `belts`. The Mod currently shows `displayName = "Simple Belts"` and the same mod id `belts`.
- **Mixing is allowed, but the Upstream parts stay CC BY 4.0.** CC's FAQ says "the original CC license always applies to the material you are adapting even once adapted" [3]. The license you choose for your own contributions (the "Adapter's License") must not stop recipients from complying with CC BY (§3(a)(4)) [1]. The recommended way to show this state is per-file or per-directory marking plus several license files (choosealicense "Mixed projects" [12], REUSE [31], SPDX `AND` expressions [30]).
- **CC recommends against CC licenses for software.** It also says its licenses "are currently not compatible with the major software licenses" [3]. The **FSF's license list says the opposite for CC BY 4.0 specifically**: "compatible with all versions of the GNU GPL" (though it still should not be used on software) [35].
- **Modrinth modpacks.** Any file hosted on Modrinth may be used in Modrinth modpacks, whatever its license. Files hosted elsewhere need an open-source license that allows redistribution, or the author's permission [20]. `.mrpack` files may download only from `cdn.modrinth.com`, `github.com`, `raw.githubusercontent.com` and `gitlab.com`. Other jars must be embedded in `overrides/` [21].
- **CurseForge modpacks.** A mod hosted on CurseForge is referenced through the manifest. A non-CurseForge mod must be on the Approved Non-CurseForge list, which requires "MIT/GPL or an equivalent" with no conditions "beyond requiring credit". Mods with "link back" or "text file credit" requirements "will not be considered" [24].
- **Possibly relevant to #6.** All 71 commits the Mod adds on top of Upstream carry a `Co-Authored-By: Claude` trailer. Modrinth requires an AI-content disclosure when "a substantial portion of the project's code is a product of AI output". It bars public projects that are "primarily or entirely" AI output [18]. The US Copyright Office's position is that AI output is protected only where a human "has determined sufficient expressive elements" [32]. See [§7](#7-facts-that-may-bear-on-the-license-decision-6).

## 1. What the Mod contains today

These are repository facts as of `planetaryfactory` @ `2fa8756`.

| Item | State |
|---|---|
| Upstream code | Rearth/SimpleBelts, `LICENSE` = CC BY 4.0 full legal code. The file has no copyright line and no creator name. |
| Upstream's public identity | Modrinth `simple-conveyor-belts`, title "Simple Conveyor Belts", author `rearth`, license `CC-BY-4.0` [33]. CurseForge `simple-conveyor-belts` [33]. GitHub `Rearth/SimpleBelts`. The GitHub API reports its license as `NOASSERTION` [33]. |
| Upstream README credit | "Conveyor textures are based on modified assets from malcolriley's unused-textures repo" (link to `github.com/malcolmriley/unused-textures`). |
| Texture source | malcolmriley/unused-textures. Its README says: "The resources in this repository are available under the Creative Commons Attribution 4.0 International License". A `LICENSE` file is present and there is no copyright notice [34]. |
| Mod's `neoforge.mods.toml` | `license = "CC-BY-4.0"`, `displayName = "Simple Belts"`, `modId = "belts"`, `authors = "Rearth; Planetary Factory fork"`. |
| Mod's README | Still Upstream's README, including the credits line. |
| Mod's asset changes vs Upstream | Upstream textures modified (`chute.png`, `conveyorbelt/*`, `improved_conveyorbelt/*`). Many new textures added (express, turbo, splitter, loader, and others). |
| Jar packaging | Neither `build.gradle` nor `neoforge/build.gradle` copies `LICENSE` into the jar (a grep for `license`/`from(` found only the shadow/zipTree lines). |

## 2. CC BY 4.0 obligations

### 2.1 When they apply

The conditions attach when you **Share** the material, meaning you provide it to the public by any means that needs permission under copyright [1 §1, §3(a)]. The FAQ says use that is personal or within an organization needs no attribution [3 "Do I always have to attribute"]. Publishing a repo, a jar or a modpack that contains the material counts as Sharing.

### 2.2 The exact attribution obligations (§3(a))

Paraphrased from the legal code [1 §3(a)]. The wording is in the repo's `LICENSE`.

- **§3(a)(1)(A)**: *retain, if supplied by the Licensor*:
  1. identification of the creator(s) and anyone designated to receive attribution, in any reasonable manner the Licensor requests (including a pseudonym);
  2. a copyright notice;
  3. a notice that refers to the Public License;
  4. a notice that refers to the disclaimer of warranties;
  5. a URI or hyperlink to the Licensed Material, to the extent reasonably practicable.
- **§3(a)(1)(B)**: indicate if You modified the Licensed Material, **and retain an indication of any previous modifications**.
- **§3(a)(1)(C)**: indicate that the material is licensed under CC BY 4.0, and include the text of the license or its URI.
- **§3(a)(2)**: the conditions may be met "in any reasonable manner based on the medium, means, and context", including by "providing a URI or hyperlink to a resource that includes the required information."
- **§3(a)(3)**: if the Licensor asks, you must remove the §3(a)(1)(A) information "to the extent reasonably practicable". The FAQ says this applies when an adapter makes something the licensor no longer wants to be associated with [3 "What can I do if… I do not like the way someone uses it"].
- **§3(a)(4)**: if you Share Adapted Material, the Adapter's License you apply "must not prevent recipients of the Adapted Material from complying with this Public License."

Related points from the FAQ and the deed:

- The title is **not** required in 4.0, but it is recommended if one is supplied [3 "How do I properly attribute"][4].
- "CC licenses have a flexible attribution requirement, so there is not necessarily one correct way", and a licensor cannot insist on exact placement [3 "Can I insist on the exact placement"].
- When you use an adaptation, "you may need to give credit to the creator(s) of the pre-existing work(s), in addition to giving credit to the creator of the adaptation" [3 "Do I need to be aware of anything else"]. Here that means crediting both Rearth and malcolmriley for the textures.
- CC's recommended pattern is **TASL** (Title, Author, Source, License). It gives a model line for an adaptation: *This work, "X", is adapted from "Y" by Z, used under CC BY 4.0. "X" is licensed under [license] by [you].* [4 "This is a great attribution for when you have created an adaptation"]. When there are several sources, "it is clear which attribution belongs to which work" [4 "Attributing materials from multiple sources"].
- A separate credits page is a recognized option for media where inline credit is awkward [4 "Publishing attribution on a separate web page"].

### 2.3 Meeting them in a repo, a jar and a mod page

CC names no required location. The table below lists the places the sources above treat as reasonable for each medium.

| Medium | What the sources support |
|---|---|
| **Repo** | A notice that names each creator (Rearth, malcolmriley), links each source, names CC BY 4.0 and includes or links its text, and states what was modified. CC's third-party marking page recommends a general notice ("Except where otherwise noted…") and marking of the specific parts that the main license does not cover, ideally both [6]. choosealicense: "you can include multiple licenses, as long as you are explicit about which license applies to each part of the project" [12]. REUSE requires a license file in `LICENSES/` for every license used, plus per-file `SPDX-License-Identifier` / `SPDX-FileCopyrightText` headers or a `REUSE.toml` [31]. |
| **Jar** | The jar is itself a copy that gets Shared (modpacks redistribute the jar alone). §3(a)(2) allows a URI to a page with the attribution [1], so a notice file in the jar, a link to one, or both would meet "reasonable manner". NeoForge's `mods.toml` has a **mandatory** `license` field (SPDX identifier suggested) and optional `credits` and `authors` fields shown on the in-game mod list [29]. SPDX expresses "both licenses apply" as `LicenseRef-… AND CC-BY-4.0` or `MIT AND CC-BY-4.0` [30]. The current build does **not** put `LICENSE` in the jar (see §1). |
| **Mod page** | A credits section naming the creators, linking the sources and the license, and stating the modifications. Modrinth: "When uploading content you have the necessary permissions to distribute but did not author yourself, you must make a meaningful effort to credit each original source properly" [18 §4]. CurseForge: for a fork, "credit and link the original creator" and "describe what has changed from the original and you cannot copy the description of the original project" [23]. |

### 2.4 Marking changes

- In 4.0 you must note modifications "regardless of whether the modification is significant enough to merit a derivative work" [5 "Adding a CC license to your derivative work"]. Examples include taking an excerpt or cropping. "It is not necessary to note trivial alterations, such as correcting a typo or changing a font size" [3 "How do I properly attribute"].
- Earlier notes about modifications must be kept (§3(a)(1)(B)) [1]. Upstream's README line about modified unused-textures assets is one such note.
- CC's example for a slight modification: *"Title" by Author, used under CC BY 4.0 / Cropped from original* [4].
- The legal code does not require a per-file changelog. The level of detail falls under "any reasonable manner" (§3(a)(2)) [1].

### 2.5 No implied endorsement, trademarks and the "SimpleBelts" name

- §2(a)(6): nothing in the license "may be construed as permission to assert or imply that You are, or that Your use of the Licensed Material is, connected with, or sponsored, endorsed, or granted official status by, the Licensor" or the people it credits [1].
- §2(b)(2): "Patent and trademark rights are not licensed under this Public License." [1]. choosealicense lists "Trademark use" as a limitation of CC BY 4.0 [10].
- The FAQ says wrongly implying endorsement "may be unlawful", and repeats the duty to remove credit on request [3 "Do I need to be aware of anything else"].
- The FAQ adds that a licensor applying a CC license "may create an implied license to use the trademark in connection with the licensed material, although not in ways that require permission under trademark law" [3 "Can I offer material under a CC license that has my trademark on it"].
- **Platform rules on names:**
  - Modrinth: content must not "misrepresent your identity or affiliation with any person or organization" or "give the impression that they emanate from or are endorsed by … any other person or entity" [18 §1].
  - CurseForge: "Your project should contain distinct content and assets, including in its name, avatar, summary and description. If your project is a fork of another project and allowed by their license - credit and link the original creator" [23].
- **Facts about the name:**
  - Upstream's published title is "Simple Conveyor Belts" [33], its repo is `SimpleBelts`, and its mod id is `belts`.
  - The Mod's current `displayName` is "Simple Belts" and its mod id is `belts` (§1).
  - I found no source saying whether "SimpleBelts" or "Simple Conveyor Belts" is a registered trademark. Whether reusing it implies a connection is a question of fact that the license does not settle.
  - The sources give two firm points. Reusing a name is **not** licensed by CC BY (§2(b)(2)). CurseForge's written policy asks forks for a distinct name.

### 2.6 No downstream restrictions, termination

- §2(a)(5)(B): you may not impose "additional or different terms or conditions on", or apply technological measures to, the Licensed Material if doing so restricts any recipient's exercise of the licensed rights [1]. The deed says the same: "You may not apply legal terms or technological measures that legally restrict others from doing anything the license permits" [2].
- §6: rights end automatically on non-compliance. They are reinstated automatically if the violation is cured within 30 days of discovering it [1][3 "How can I lose my rights"].
- The grant is "non-sublicensable" (§2(a)(1)). Every recipient receives the offer directly from the Licensor (§2(a)(5)(A)) [1]. The FAQ applies this to platforms: "you cannot grant a license to a platform with respect to the rights in any CC-licensed content you do not own" [3 "Do I need to worry about website terms of service"].

## 3. CC BY material inside a codebase under another license

### 3.1 What CC says about adaptations and collections

- **Adaptations.** "The original CC license always applies to the material you are adapting even once adapted. The license you may choose for your own contribution (called your 'adapter's license') depends on which license applies to the original material. Recipients of the adaptation must comply with both the CC license on the original and your adapter's license." [3 "If I derive or adapt…"].
- For BY material, CC "generally recommend[s] that your adapter's license include at least the same license elements" [3].
- In CC's adapter's-license chart, every CC license is green for a BY original, and **PD is yellow** ("technically permitted" but not recommended). If you use a yellow option, "take additional care to mark the adaptation as involving multiple copyrights under different terms" [3]. The chart does not list any non-CC license. For those, the binding test is §3(a)(4) [1].
- **Collections.** If combining does not create an adaptation, "you may combine any CC-licensed content so long as you provide attribution" [3 "Can I combine material…"]. A collection may carry its own license, but "this does not change the license applicable to the original material" [3 "If I create a collection…"].
- Whether a change counts as an adaptation "depends primarily on the applicable copyright law" [3 "When is my use considered an adaptation?"].

### 3.2 Per license

Each license's own text says the following about sitting next to, or around, CC BY material.

| License | What its text / its steward says | Relevant to holding CC BY parts |
|---|---|---|
| **MIT** (Expat) | "Licensed works, modifications, and larger works may be distributed under different terms and without source code" [7]. The only condition is that the copyright and permission notice "shall be included in all copies or substantial portions" [13]. The FSF calls it a "lax, permissive" license compatible with the GPL, and recommends saying "Expat" [35]. | MIT sets no terms on the other parts of a larger work [7], so CC BY files can sit beside MIT files, each under its own license. |
| **LGPL-3.0** | The LGPL "incorporates the terms and conditions of version 3 of the GNU General Public License" plus extra permissions [14]. GPLv3 §5(c): "You must license the entire work, as a whole, under this License … regardless of how they are packaged" [15]. GPLv3 §5 also defines an "aggregate" of separate works, which does not spread the license [15]. GPLv3 §7 allows extra terms such as attribution notices only "for material you add … (if authorized by the copyright holders of that material)" [15]. | CC's FAQ: CC licenses "are currently not compatible with the major software licenses, so it would be difficult to integrate CC-licensed work with other free software" [3]. The FSF: CC BY 4.0 "is compatible with all versions of the GNU GPL" [35]. These two primary sources differ. |
| **MPL-2.0** | Copyleft applies per file: "The copyleft applies to any files containing MPLed code" [17 Q12]. A "Larger Work" combines Covered Software "with other material, in a separate file or files" and "may be distributed under terms of Your choice" [16 §1.7, §3.3]. New files with no MPL code "do not need to be distributed under the terms of the MPL" [17 Q11]. The notice must be attached to each file, or put where a recipient "would be likely to look" [17 Q22]. | Upstream CC BY files and MPL files would be separate files in one Larger Work. MPL says nothing about the non-MPL files' licenses. |
| **ARR / no license** | "Nobody else can copy, distribute, or modify your work" without permission. A public GitHub repo grants only the right to view and fork under GitHub's ToS [11]. | ARR can cover only the author's own contributions. CC BY material stays CC BY (§2(a)(5)(B) [1]; FAQ "original CC license always applies" [3]). So an ARR codebase with Upstream parts is still mixed. |

### 3.3 Marking the mixed state

- CC recommends a general notice plus specific marking, and describing "all of the elements of the work … to which the license does not apply" [6 "Tips for a clear and informative notice"].
- choosealicense says to be "explicit about which license applies to each part of the project" [12].
- The REUSE spec requires a license file per license in `LICENSES/`, and copyright plus `SPDX-License-Identifier` information for every covered file, either in a header or in `REUSE.toml` [31].
- SPDX uses `AND` when several licenses apply at once, for example `MIT AND CC-BY-4.0`. A custom license is written `LicenseRef-<id>` [30].
- NeoForge's `license` field in `mods.toml` is mandatory, and an SPDX identifier is suggested [29].

## 4. CC's own guidance against CC licenses for software

From the FAQ entry "Can I apply a Creative Commons license to software?" [3]:

- "We recommend against using Creative Commons licenses for software." CC recommends licenses that the FSF lists as free and the OSI lists as open source instead.
- The reasons it gives: CC licenses "do not contain specific terms about the distribution of source code" and do not address patents, and they "are currently not compatible with the major software licenses".
- The only software-compatibility mechanism it names is **BY-SA 4.0 → GPLv3 (one-way)**. CC0 is also "GPL-compatible and acceptable for software".
- CC licenses "may be used for software documentation, as well as for separate artistic elements such as game art or music."

Other sources say the same:

- choosealicense marks CC BY 4.0 "Not recommended for software" [10] and says "Creative Commons does not recommend its licenses be used for software" [12].
- Modrinth's licensing guide says: "Creative Commons licenses as a whole are not recommended for software" [22].
- SPDX lists CC-BY-4.0 as FSF-libre but **not OSI-approved** (from the SPDX license list JSON). Modrinth's modpack checklist asks whether a license is "open-source" and "allows redistribution" [20].

## 5. Common mod licenses: hosting and modpack redistribution

### 5.1 Platform mechanics that apply whatever the license

**Modrinth**

- Uploading grants Modrinth a right "to display and distribute your Gaming Content to our users". Content already inside other users' packs may stay there after you delete it [19 "About the Service"].
- Content Rules §4: you must have the rights to share what you upload. Re-uploads need permission unless the project is "a license-abiding 'fork'", which Modrinth defines as a modified copy that has "diverged substantially from the original project". Non-authored material must be credited [18].
- §5 asks for license metadata "consistent with information found elsewhere" [18].
- The modpack checklist [20] works in order:
  1. The file is hosted on Modrinth → usable, because "authors grant Modrinth the right to allow Modrinth users to use that file in Modrinth modpacks".
  2. An open-source license that allows redistribution → usable.
  3. The description grants permission → usable.
  4. Explicit author permission → usable, with a screenshot sent to Moderation.
  5. Otherwise not usable. Modrinth's example is that ARR content forbids redistribution without permission.
- `.mrpack` download URLs must come from `cdn.modrinth.com`, `github.com`, `raw.githubusercontent.com` or `gitlab.com`. Any other file is copied from `overrides/` into the instance, which means the pack itself redistributes it [21].

**CurseForge**

- The Mod Authors Terms grant Overwolf "an unrestricted, worldwide, royalty-free license to use, store, reproduce, distribute, display, perform, operate, modify, adapt, and create derivative works of your Mod". The rights in (b) marketing/distribution on the Platform and (e) usage data are "granted exclusively to Overwolf". The author warrants they have the rights needed [27 §2, §4].
- By contrast, CC BY is non-sublicensable (§2(a)(1)) [1]. CC's FAQ says you "cannot grant a license to a platform" for CC content you do not own [3].
- The Distribution Toggle controls whether third-party apps may fetch the project through the API. "The API will respect the toggle choice over the license type choice" [25].
- Modpacks reference CurseForge-hosted mods through `manifest.json`. A non-CurseForge mod may go in `overrides/mods` only if it is on the Approved Non-CurseForge list [24][26].
  - List criteria: "MIT/GPL or an equivalent", "not conditional beyond requiring credit", and "Mods with 'link back', 'text file credit', or other requirements will not be considered".
  - The jar must be "an official unmodified distributable … or a licensed and documented fork".
  - Personal permissions are not accepted [24].
- If a pack contains mods not hosted on CurseForge, its description "must include a list of those mods and credit for the authors" [24].

**Minecraft EULA**

- A "Mod" is "something original … that doesn't contain a substantial part of our copyrightable code or content".
- Mods "are okay to distribute". You may do "whatever you want with them, as long as you don't sell them for money / try to make money from them" [28].

### 5.2 Per license

| License | Author's hosting on Modrinth | Author's hosting on CurseForge | Modpack redistribution of the jar |
|---|---|---|---|
| **MIT** | Allowed. MIT is the most popular license on Modrinth according to Modrinth's 2021 guide [22]. | Allowed. MIT is the example CurseForge gives for off-platform override approval [24]. | Condition: include the copyright and permission notice [13]. Modrinth checklist step 2 is met [20]. CurseForge override list: meets "MIT/GPL or an equivalent" [24]. |
| **LGPL-3.0** | Allowed. It was the second most common license on Modrinth in 2021. Modrinth says GPL-3.0/AGPL-3.0 "are incompatible if linking into Minecraft" unless an exception is added, and recommends LGPL-3.0 instead [22]. | Allowed. | Every conveyance of object code must come with the Corresponding Source by one of GPLv3 §6's methods. §6(d) allows object code on one server and source on another, with "clear directions next to the object code" [15]. LGPL §4 adds notice duties and the GPL/LGPL texts for a Combined Work [14]. Modrinth step 2 is met [20]. CurseForge: "GPL or an equivalent" [24]. |
| **MPL-2.0** | Allowed. | Allowed. | Executable-form distribution must say how to get the source, and the source must be under MPL [16 §3.1–3.2]. Someone passing on an unchanged binary "typically" needs to do nothing if the upstream complied [17 Q7]. Modrinth step 2 is met [20]. Whether CurseForge counts MPL as "equivalent" is not stated [24]. |
| **ARR** | Allowed. Modrinth notes contributions become "difficult or impossible" [22]. | Allowed. | Modrinth: usable in Modrinth packs **only if the file is hosted on Modrinth** (step 1) or with permission; otherwise forbidden [20]. CurseForge: usable if hosted on CurseForge. Off-platform ARR mods are rejected unless the author gives the "Allowed to be included in CurseForge Client/CurseForge Mod Packs" permission through the list process [24]. |
| **CC BY 4.0** (Upstream today) | Allowed (Upstream is hosted this way [33]). | Allowed (Upstream is hosted this way [33]). | Condition: §3(a) attribution with each copy [1]. Modrinth: step 1 covers files hosted on Modrinth. Step 2 asks for an "open-source license", and CC BY 4.0 is not OSI-approved (see §4) [20]. CurseForge override list: CC BY requires the license text or a URI (§3(a)(1)(C)). CurseForge does not say whether it treats that as a "link back" condition [24]. |
| **Mixed** (e.g. `MIT AND CC-BY-4.0`) | Allowed. Modrinth §5 asks for license metadata consistent with the project [18]. | Allowed. | A redistributor must meet the conditions of **each** license that applies to the parts (FAQ: "Recipients of the adaptation must comply with both" [3]). |

## 6. Where to put things: what each source requires

This section lists requirements, not recommendations.

- **CC BY (Upstream code and textures, and malcolmriley textures):** creator names, any copyright notice supplied (none was), a license notice with a link or text, a disclaimer notice, a source URI, the note on modifications, and the earlier modification notes. This applies wherever the material is Shared: repo, jar and download page [1].
- **The Mod's own code license:** that license's own notice requirements, such as the MIT notice in copies [13], MPL headers per file [17 Q22], or the LGPL notice and texts [14].
- **Platform metadata:** Modrinth license metadata must be consistent with the project [18]. NeoForge `license` is mandatory [29]. CurseForge's License tab holds the distribution toggle [25].

## 7. Facts that may bear on the license decision (#6)

1. **CC and the FSF disagree about CC BY and the GPL.** CC says its licenses are "not compatible with the major software licenses" [3]. The FSF says CC BY 4.0 is "compatible with all versions of the GNU GPL" [35]. Both still say CC licenses should not be used for software.
2. **The Upstream parts cannot be relicensed.** Whatever the Mod's own license, the 21% of Upstream lines and the textures remain CC BY 4.0 [3]. An "ARR" or "MIT" label for the whole jar would be inaccurate unless the CC BY parts are marked, and §2(a)(5)(B) bars adding restrictive terms to those parts [1].
3. **CurseForge's rule on off-platform packs is strict about attribution conditions.** It rejects mods whose licenses require "link back" or "text file credit" [24]. This matters only if the modpack is on CurseForge and the Mod is not hosted there.
4. **Hosting the Mod on Modrinth removes most modpack-license questions for Modrinth packs**, because files hosted on Modrinth are usable in Modrinth modpacks whatever their license [20]. Hosting elsewhere makes the license decide the question.
5. **The name.** CurseForge asks forks for a "distinct" name [23]. The Mod currently shares Upstream's mod id `belts` and uses a near-identical display name. Upstream's CC BY license grants no trademark rights [1].
6. **CurseForge's author terms ask for broad and partly exclusive rights**, while CC BY is non-sublicensable [27][1][3]. CC's FAQ notes that platforms rarely require you to own everything you post [3], but CurseForge's terms include a warranty that you hold the needed rights [27 §4].
7. **AI authorship** (outside the ticket's question, but it affects what a license can cover):
   - All 71 commits the Mod adds on top of Upstream (`origin/26.1.2..planetaryfactory`) carry `Co-Authored-By: Claude` trailers.
   - Modrinth requires the "Contains AI-generated content" disclosure when "a substantial portion of the project's code is a product of AI output". It says projects "may not be published publicly if the contents are primarily or entirely a product of AI output" [18 §6].
   - The US Copyright Office (Part 2, 2025-01-29) concludes that AI outputs "can be protected by copyright only where a human author has determined sufficient expressive elements", and that prompts alone do not suffice. Using AI to assist, or including AI material in a larger human-authored work, "does not bar copyrightability" [32]. A license covers only rights the licensor holds [5 "Noting third-party content"][11].

## Sources

1. Creative Commons, *Attribution 4.0 International Public License* (legal code). <https://creativecommons.org/licenses/by/4.0/legalcode.en>. The repo's `LICENSE` has the same operative text. It leaves out the preamble, the clause letters and CC's closing notice.
2. Creative Commons, *CC BY 4.0 deed*. <https://creativecommons.org/licenses/by/4.0/deed.en>
3. Creative Commons, *Frequently Asked Questions*. <https://creativecommons.org/faq/>. Entries cited by heading: [software](https://creativecommons.org/faq/#can-i-apply-a-creative-commons-license-to-software), [attribution](https://creativecommons.org/faq/#how-do-i-properly-attribute-material-offered-under-a-creative-commons-license), [anything else](https://creativecommons.org/faq/#do-i-need-to-be-aware-of-anything-else-when-providing-attribution), [exact placement](https://creativecommons.org/faq/#can-i-insist-on-the-exact-placement-of-the-attribution-credit), [trademark](https://creativecommons.org/faq/#can-i-offer-material-under-a-cc-license-that-has-my-trademark-on-it-without-also-licensing-or-affecting-rights-in-the-trademark), [adaptation](https://creativecommons.org/faq/#when-is-my-use-considered-an-adaptation), [combine](https://creativecommons.org/faq/#can-i-combine-material-under-different-creative-commons-licenses-in-my-work), [adapter's license](https://creativecommons.org/faq/#if-i-derive-or-adapt-material-offered-under-a-creative-commons-license-which-cc-licenses-can-i-use), [collection](https://creativecommons.org/faq/#if-i-create-a-collection-that-includes-a-work-offered-under-a-cc-license-which-licenses-may-i-choose-for-the-collection), [platforms](https://creativecommons.org/faq/#do-i-need-to-worry-about-website-terms-of-service-when-i-share-cc-licensed-content-on-social-media-platforms), [termination](https://creativecommons.org/faq/#how-can-i-lose-my-rights-under-a-creative-commons-license-if-that-happens-how-do-i-get-them-back), [name removal](https://creativecommons.org/faq/#what-can-i-do-if-i-offer-my-material-under-a-creative-commons-license-and-i-do-not-like-the-way-someone-uses-it)
4. Creative Commons wiki, *Recommended practices for attribution*. <https://wiki.creativecommons.org/wiki/Recommended_practices_for_attribution>
5. Creative Commons wiki, *Marking your work with a CC license*. <https://wiki.creativecommons.org/wiki/Marking_your_work_with_a_CC_license>
6. Creative Commons wiki, *Marking third party content*. <https://wiki.creativecommons.org/wiki/Marking/Creators/Marking_third_party_content>
7. choosealicense.com, *MIT License*. <https://choosealicense.com/licenses/mit/>
8. choosealicense.com, *GNU LGPLv3*. <https://choosealicense.com/licenses/lgpl-3.0/>
9. choosealicense.com, *Mozilla Public License 2.0*. <https://choosealicense.com/licenses/mpl-2.0/>
10. choosealicense.com, *CC BY 4.0*. <https://choosealicense.com/licenses/cc-by-4.0/>
11. choosealicense.com, *No License*. <https://choosealicense.com/no-permission/>
12. choosealicense.com, *Non-Software Licenses* (Mixed projects). <https://choosealicense.com/non-software/>
13. Open Source Initiative, *The MIT License*. <https://opensource.org/license/mit>
14. Open Source Initiative, *GNU Lesser General Public License v3*. <https://opensource.org/license/lgpl-3-0>
15. Open Source Initiative, *GNU General Public License v3* (§§5, 6, 7). <https://opensource.org/license/gpl-3-0>
16. Mozilla, *Mozilla Public License 2.0*. <https://www.mozilla.org/en-US/MPL/2.0/>
17. Mozilla, *MPL 2.0 FAQ*. <https://www.mozilla.org/en-US/MPL/2.0/FAQ/>
18. Modrinth, *Content Rules* (last modified 2026-08-13). <https://modrinth.com/legal/rules>
19. Modrinth, *Terms of Use* (last modified 2026-08-13). <https://modrinth.com/legal/terms>
20. Modrinth Help Center, *Obtaining modpack permissions* (2025-10-15). <https://support.modrinth.com/en/articles/8797527-obtaining-modpack-permissions>
21. Modrinth Help Center, *Modrinth Modpack format (.mrpack)*. <https://support.modrinth.com/en/articles/8802351-modrinth-modpack-format-mrpack>
22. Modrinth News, *Beginner's Guide to Licensing your Mods* (2021-05-16). <https://modrinth.com/news/article/licensing-guide/>
23. CurseForge Support, *Project and Modpack Moderation Policies*. <https://support.curseforge.com/support/solutions/articles/9000197279-project-and-modpack-moderation-policies>
24. CurseForge Support, *Exporting a Modpack for CurseForge Project Submission* (Modpack Requirements, override rules). <https://support.curseforge.com/support/solutions/articles/9000197908-exporting-a-modpack-for-curseforge-project-submission>
25. CurseForge Support, *Project Distribution Toggle* (modified 2024-10-31). <https://support.curseforge.com/support/solutions/articles/9000207877-project-distribution-toggle>
26. CurseForge Support, *Non-CurseForge Mods* (modified 2025-01-12). <https://support.curseforge.com/support/solutions/articles/9000197913-non-curseforge-mods>
27. Overwolf, *CurseForge Mod Authors Terms and Conditions* (updated 2025-07-21). <https://legal.overwolf.com/docs/curseforge/mod-authors-terms/>
28. Mojang, *Minecraft End User License Agreement* ("Using mods"). <https://www.minecraft.net/en-us/eula>
29. NeoForged docs, *Mod Files* (`neoforge.mods.toml` `license`, `credits`, `authors`). <https://docs.neoforged.net/docs/gettingstarted/modfiles>
30. SPDX Specification 2.3, Annex D *SPDX License Expressions*. <https://spdx.github.io/spdx-spec/v2.3/SPDX-license-expressions/>
31. FSFE, *REUSE Specification 3.3*. <https://reuse.software/spec-3.3/>
32. U.S. Copyright Office, *Copyright Office Releases Part 2 of Artificial Intelligence Report*, NewsNet 1060 (2025-01-29). <https://www.copyright.gov/newsnet/2025/1060.html>
33. Upstream listings: Modrinth API <https://api.modrinth.com/v2/project/simple-conveyor-belts>; CurseForge <https://www.curseforge.com/minecraft/mc-mods/simple-conveyor-belts>; GitHub <https://github.com/Rearth/SimpleBelts>
34. malcolmriley, *unused-textures* (README and LICENSE). <https://github.com/malcolmriley/unused-textures> (local copy `~/minecraft_mods/unused-textures`)
35. Free Software Foundation, *Various Licenses and Comments about Them* ([#ccby](https://www.gnu.org/licenses/license-list.html#ccby), [#Expat](https://www.gnu.org/licenses/license-list.html#Expat), [#MPL-2.0](https://www.gnu.org/licenses/license-list.html#MPL-2.0), [#LGPLv3](https://www.gnu.org/licenses/license-list.html#LGPLv3)). <https://www.gnu.org/licenses/license-list.html>
