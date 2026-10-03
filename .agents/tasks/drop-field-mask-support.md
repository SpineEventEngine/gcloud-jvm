---
slug: drop-field-mask-support
branch: drop-support-of-field-mask
owner: claude
status: in-review
started: 2026-10-02
---

## Goal

The Datastore storage returns records exactly as stored and ignores `RecordQuery.mask()`,
so `DsRecordStorageTest` passes the `DelegatingRecordStorageTest` fixture of core-jvm
`2.0.0-SNAPSHOT.560`, which now expects storages to ignore field masks.

## Context

- core-jvm PR #1679 (merged as `d6a73060f96`, version `.560`) dropped `FieldMask` support
  on the read path: the API elements carrying masks stay, but are deprecated no-ops.
  Its plan (`core-jvm/.agents/tasks/drop-field-mask-support.md`) lists this repo as
  a follow-up: "drop its own `record/FieldMaskApplier` masking".
- The masks used to be applied in `ToRecords` (for both `ConvertAsIs` and `SortAndLimit`)
  and in `DsLookupByIds.toMaskedRecord(..)`.
- The PR also bumped Base to `.450`, where `withMask(..)` stores nothing, so no mask
  reaches a storage through a `RecordQuery` anymore. The fixture dropped its masked
  `RecordQuery` tests accordingly. The remaining mask tests call the deprecated
  `read(id, mask)` and `readAll(ids, mask)`; `DsRecordStorage` does not override them,
  so core's mask-free delegation covers them.
- `record.FieldMaskApplier` is public in a non-`@Internal` package with published Javadoc,
  so it is deprecated rather than deleted. `PreparedQuery.mask()` is package-private
  in an `@Internal` class, so it is removed.

## Plan

- [x] Bump `versionToPublish` `.231` → `.240`: breaking-change rounding, mirroring
      core-jvm's `.552` → `.560` for the same semantic change.
- [x] Adopt core-jvm `.560`: `CoreJvm` → `.560`. Align the pins of the merged core-jvm,
      since this build force-pins them: `Base` → `.450`, `CoreJvmCompiler` → `.094`.
- [x] Remove masking from the query pipeline:
  - `ToRecords`, `ConvertAsIs`, `SortAndLimit` — drop the `FieldMask` constructor
    parameter and the masker;
  - `DsLookupByIds` — convert entities to records without masking;
  - `DsLookupByQueries` — adjust the constructor calls;
  - `PreparedQuery` — remove `mask()`; state in the class doc that the mask is ignored.
- [x] Deprecate `record.FieldMaskApplier` (the class and `recordMasker`): `@Deprecated`
      plus the `@deprecated` tag; `recordMasker` keeps `checkNotNull` and returns
      `Function.identity()`.
- [x] Tests (Kotlin, Kotest, `internal` `…Spec`):
  - `FieldMaskApplierSpec` (`UtilityClassTest`) — the deprecated masker returns plain
    messages and `EntityRecord`s unchanged, and still rejects a `null` mask.
  - `IgnoredFieldMaskSpec` covered masked queries for each lookup strategy against
    Base `.445`. Its premise check (the query carries the mask) failed by design once
    Base `.450` made `withMask(..)` store nothing, so the spec is removed.
  - `DsLookupByQueriesSpec` (emulator) — reading by a query that runs as several Datastore
    queries: joining the results, removing duplicates, sorting, and limiting after
    sorting. The removed spec was the only test of this path; `master` had none.
- [x] Pull the latest `config` (requested by the user mid-task): `config` → `94a9e08b`.
- [x] Select the "CodeMatters Open-Source" copyright profile in IDEA settings and use it
      for all updated files (requested by the user mid-task). The `update-copyright.sh`
      hook applies the profile on each edit.
- [x] Fix the config-pull fallout: the buildscript classpath failed on a Jackson
      `3.2.3`/`3.2.2` version conflict; force the Jackson V3 artifacts in the root
      buildscript, as core-jvm did in `a99960d706a`.
- [x] Verify: `./gradlew build dokkaGenerate`; grep gate — no `FieldMaskApplier`,
      `recordMasker`, or `mask()` use in `datastore/src/main` outside the deprecated class.
- [x] Reviews: `dependency-audit`, `spine-code-review`, `kotlin-engineer`, `review-docs`;
      their findings are applied.
- [x] Adapt to the merged core-jvm PR #1679 once its version is published, re-verify
      with `--refresh-dependencies`, and open the PR.

## Verification

- `./gradlew build dokkaGenerate --refresh-dependencies` with Corretto 17 and Docker
  Desktop running (the `:datastore` tests use the Datastore Emulator).
- Maven Local holds a stale `.560` built from `ed302323ceb` before the PR merged.
  The Artifact Registry precedes `mavenLocal()`, and `--refresh-dependencies` drops
  the cached lookup misses, so the build resolves the published `.560`.

## Log

- 2026-10-02 18:04 — drafted on `drop-support-of-field-mask`, branched from `origin/master`
  (`3090e2a2`).
- 2026-10-02 18:05 — plan approved; started publishing core-jvm `.560` to Maven Local
  and launched Docker Desktop.
- 2026-10-02 18:22 — ran `./config/pull` and applied the CodeMatters copyright profile,
  as requested. The pull also moved CoreJvm to `.552`, CoreJvmCompiler to `.093`, Time to
  `.251`, Change to `.208`, ToolBase to `.423`, and Jackson to `3.2.3`; the `.560`,
  `.445`, and `.094` pins are applied on top.
- 2026-10-02 18:23 — core-jvm `.560` published to Maven Local.
- 2026-10-02 18:30 — main code, tests, and the Jackson V3 forcing done; `:datastore`
  compiles.
- 2026-10-02 18:34 — `./gradlew build dokkaGenerate` green: `datastore` 332 tests, 0
  failures, 18 skipped (remote Datastore and delivery smoke tests); all 25
  `DelegatingRecordStorageTest` cases pass, including the four flipped ones.
- 2026-10-02 18:44 — review findings applied: `FieldMaskApplierSpec` extends
  `UtilityClassTest`, `IgnoredFieldMaskSpec` asserts its premises, Javadoc wording fixed.
- 2026-10-02 18:49 — re-verified: `./gradlew build dokkaGenerate` green; `datastore` 335
  tests, 0 failures. Nothing committed; ready for review.
- 2026-10-02 20:10 — committed the changes as seven commits, from "Update `config`"
  to "Update dependency reports"; nothing pushed.
- 2026-10-02 20:15 — blocked on core-jvm PR #1679. A session cron job (`421f7037`) checks
  it every 30 minutes from 2026-10-03 10:07.
- 2026-10-03 13:10 — core-jvm PR #1679 merged at 13:03 as `d6a73060f96`: version `.560`,
  Base `.450`, CoreJvm Compiler `.094`.
- 2026-10-03 13:41 — `.560` published to the Artifact Registry; the cron job cancelled.
  Base → `.450`. `IgnoredFieldMaskSpec` failed its premise check in all three cases
  ("The query carries no field mask"), as designed; removed.
- 2026-10-03 18:07 — `./gradlew build dokkaGenerate --refresh-dependencies` green against the
  published `.560`: `datastore` 331 tests, 0 failures, 18 skipped; all 24 fixture cases pass.
- 2026-10-03 18:49 — opened PR #208. Added `DsLookupByQueriesSpec` (4 cases) for the
  multi-query lookup. A mutation check confirmed its value: without `distinct()` in
  `readAndJoin`, or without the in-memory sort in `SortAndLimit`, the matching tests fail.
