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

- core-jvm `drop-support-of-field-mask` (commit `ed302323ceb`, version `.560`) dropped
  `FieldMask` support on the read path: the API elements carrying masks stay, but are
  deprecated no-ops. Its plan (`core-jvm/.agents/tasks/drop-field-mask-support.md`) lists
  this repo as a follow-up: "drop its own `record/FieldMaskApplier` masking".
- The masks used to be applied in `ToRecords` (for both `ConvertAsIs` and `SortAndLimit`)
  and in `DsLookupByIds.toMaskedRecord(..)`.
- The fixture flipped four tests to expect full records. Two of them (`allIgnoringMask`,
  `manyRecordsBySeveralColumnsWithLimitIgnoringMask`) run `readAll(RecordQuery)` built
  with `withMask(..)`. Both yield a single Datastore query, so they reach only
  `DsLookupByQueries` → `ConvertAsIs` → `ToRecords`. The other two call the deprecated
  `read(id, mask)` and `readAll(ids, mask)`; `DsRecordStorage` does not override them,
  so core's mask-free delegation covers them.
- `record.FieldMaskApplier` is public in a non-`@Internal` package with published Javadoc,
  so it is deprecated rather than deleted. `PreparedQuery.mask()` is package-private
  in an `@Internal` class, so it is removed.

## Plan

- [x] Bump `versionToPublish` `.231` → `.240`: breaking-change rounding, mirroring
      core-jvm's `.552` → `.560` for the same semantic change.
- [x] Adopt core-jvm `.560`: `CoreJvm` → `.560`. Align the two pins core-jvm's branch
      moved, since this build force-pins them: `Base` → `.445`, `CoreJvmCompiler` → `.094`.
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
  - `IgnoredFieldMaskSpec` (emulator) — a `RecordQuery` with a mask returns full records
    via a lookup by IDs (`DsLookupByIds`) and via a lookup by one (`ConvertAsIs`) or
    several (`SortAndLimit`) Datastore queries. The fixture reaches neither the by-IDs
    lookup nor `SortAndLimit` with a mask. The spec asserts its premises: that the query
    carries the mask, and that the `OR` query splits into two Datastore queries.
  - `FieldMaskApplierSpec` (`UtilityClassTest`) — the deprecated masker returns plain
    messages and `EntityRecord`s unchanged, and still rejects a `null` mask.
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
- [x] Re-verify after the review fixes.

## Verification prerequisites

- core-jvm `.560` is not published remotely yet; it was published to Maven Local from
  a throwaway clone of `ed302323ceb` in the session scratchpad
  (`publishToMavenLocal -x test`), leaving the active `core-jvm` checkout untouched.
- Docker Desktop must be running for the emulator-backed `:datastore` tests.

## Follow-ups (out of scope)

- `base-libraries` (commit `c444eb217f`, `.450`, branch `drop-field-mask-support`) turns
  `withMask(..)` into a deprecated no-op. Once `Base` is bumped past it, no mask reaches
  a storage, so `IgnoredFieldMaskSpec` checks nothing: its premise check fails with
  a request to delete the spec. Delete it then. The masked-query tests of the core-jvm
  fixture become vacuous at the same point.
- The build regenerated `docs/dependencies/{dependencies.md,pom.xml}`; per `bump-version`,
  they go into a separate "Update dependency reports" commit.

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
