Boxed 3.4.0 brings support for **Trial Chambers** and their trial spawners, fixes cross-game-mode advancement resets, and modernises the entire build and test stack for Paper 1.21.11 / BentoBox 3.13.0.

## Highlights

* **Trial Chambers support** — Boxed now captures and restores Trial Spawner state (including the normal *and* ominous configurations) when structures are pulled from the seed world into a player's box, and recognises `trial_chambers` as a tracked structure.
* **No more cross-game-mode progress loss** — Boxed no longer clears a player's advancements and statistics when an island is reset in a *different*, non-Boxed game mode.
* **Modernised build & test stack** — upgraded to Paper 1.21.11 and BentoBox 3.13.0, with the test suite migrated to JUnit 5 + Mockito + MockBukkit.

## New Features

### Trial Chambers & Trial Spawners

Trial Chambers can now appear inside player boxes. When a box is generated from the seed world, Boxed copies the Trial Spawner tile-entity data so the spawners function correctly, preserving whether each spawner is in its **normal** or **ominous** configuration. Trial Chambers are also tracked for advancement-driven box growth. [[PR #123](https://github.com/BentoBoxWorld/Boxed/pull/123)]

## Bug Fixes

* **Advancement/statistic reset leaked across game modes** — `IslandNewIslandEvent` handling now no-ops unless the reset island belongs to a Boxed world, so resetting an island in another game mode no longer wipes Boxed progress. [[PR #125](https://github.com/BentoBoxWorld/Boxed/pull/125)]
* **Structure pasting into deleted islands** — pending structure pastes are now cancelled when an island is deleted, preventing structures being placed into a box that no longer exists.
* **Ominous trial spawners restored incorrectly** — trial spawners now restore the correct configuration rather than always applying the normal one.

## Other Improvements

* **Test suite migration** — replaced the PowerMock-based setup with JUnit 5 (Jupiter), Mockito `mockStatic`, and MockBukkit, and expanded coverage with new listener and placeholder tests (113 tests).
* **Reliable test dependency** — MockBukkit is now pinned to the stable Maven Central artifact `org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.110.0` instead of a floating jitpack snapshot that could break the build without any code change.
* **Documentation** — the README now documents Flags, Placeholders, `structures.yml`, config options, and Regionerator usage. [[PR #124](https://github.com/BentoBoxWorld/Boxed/pull/124)]

## Compatibility

✔️ BentoBox API 3.13.0
✔️ Minecraft 1.21.x – 26.1.x (Paper 1.21.11)
✔️ Java 21

## Upgrading

1. Stop your server.
2. Replace the old `Boxed` jar in `plugins/BentoBox/addons` with this release.
3. Ensure BentoBox is **3.13.0 or newer**.
4. Start the server.

> **Note:** Trial Chambers are captured from the seed world when a box is generated, so boxes created *before* 3.4.0 will not retroactively gain Trial Chambers. New boxes (and newly expanded regions) will include them.

## What's Changed

* Add trial chambers support: fix trial spawner tile entity copying and advancement tracking by @Copilot in https://github.com/BentoBoxWorld/Boxed/pull/123
* Update Boxed README: add Flags, Placeholders, structures.yml, config options, and Regionerator docs by @Copilot in https://github.com/BentoBoxWorld/Boxed/pull/124
* Prevent Boxed from resetting player progress on non-Boxed island resets by @Copilot in https://github.com/BentoBoxWorld/Boxed/pull/125
* Release 3.4.0 (Paper 1.21.11 / BentoBox 3.13.0, JUnit 5 + MockBukkit test migration, build fixes) by @tastybento in https://github.com/BentoBoxWorld/Boxed/pull/126

**Full Changelog**: https://github.com/BentoBoxWorld/Boxed/compare/3.3.0...3.4.0
