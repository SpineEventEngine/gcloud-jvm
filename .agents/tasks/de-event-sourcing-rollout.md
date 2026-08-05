# Adopt the de-event-sourcing storage API of core-jvm (Phase H rollout)

Upstream plan: `core-jvm/.agents/tasks/de-event-sourcing-plan.md`, Phase H.
Branch: `de-event-sourcing`. Depends on core-jvm `2.0.0-SNAPSHOT.522`.
Reference implementation: `jdbc-storage@de-event-sourcing` (PR #181 train),
which settled the vendor conventions with the product owner on 2026-08-04.

## Problem

Event-sourced aggregate loading is removed in `core-jvm`. For storage vendors:

- `AggregateStorage`, `StorageFactory.createAggregateStorage`, and the
  published `AggregateStorageTest`/`AggregateHistoryTruncationTest` fixtures
  are **removed**. Aggregate latest state arrives via
  `createEntityRecordStorage`/`createRecordStorage` with `group == null`.
- `createRecordStorage` gained a `@Nullable StorageGroup group` parameter and
  is now the factory's only abstract method. Non-null groups arrive from the
  per-entity histories (`EntityEventStorage`, `EntityStateHistoryStorage`),
  both named after the entity state type. The vendor must allocate physical
  storage by the **(source type, record type, group)** triple.
- `createEntityStateHistoryStorage` may be invoked concurrently (delivery
  worker threads); the factory must tolerate that.
- The published `RecordStorageDelegateTest` base is now `DelegatingRecordStorageTest`.

Without honoring the group, Datastore kind identity (derived from
`RecordSpec.sourceType()` alone via `RecordLayout` → `Kind.of(domainType)`)
conflates: all event journals with each other **and** with the event log
(`sourceType == Event` everywhere), and an entity's state history with its
latest-state kind (both `sourceType == the state class`,
records stored as `EntityRecord`s).

## Fix

Mechanical half:

- 3-arg `createRecordStorage` in `DatastoreStorageFactory`.
- `DsRecordStorageTest` retargeted at `DelegatingRecordStorageTest`.
- Obsolete `DsAggregateStorageTest`, `DsAggregateStorageTruncationTest`
  deleted (their published bases are gone).
- Compile-driven fallout fixes across `datastore` and `testlib`.

Substantive half:

- Grouped kind naming follows the jdbc rule (generic rule over semantic
  suffixes): grouped kind = group name + `_` + record type simple name,
  e.g. `spine.test.storage.StgProject_Event`. Implemented as a new
  `Kind.of(recordType, group)` factory method.
- `DatastoreStorageFactory.configurationWith(...)` threads the group into
  the layout choice: grouped storages always take a `FlatLayout` with the
  grouped kind. Custom layouts (`organizeRecords`) and custom storages
  (`useRecordStorage`/`useEntityStorage`) keep applying to **ungrouped**
  storages only — honoring either for a grouped storage would re-create the
  collision (or hand the history to a storage meant for latest state).
- `TxSettings` stay keyed by `sourceType` and therefore also serve grouped
  storages (mirrors jdbc's decision that source-type-keyed settings extend
  to grouped tables).
- `DatastoreStorageFactory.wrapperFor(...)` becomes `computeIfAbsent` —
  the check-then-put race matters now that the state history storage is
  created lazily on delivery worker threads.

New Kotlin specs (Datastore emulator, mirroring jdbc's suite):

- `GroupedKindAllocationSpec` — the vendor allocation contract: distinct
  kinds per (source type, record type, group); `null` group unchanged.
- `DsEntityEventStorageSpec` — journal round-trip, `historyBackward` window, `truncate`.
- `DsEntityStateHistoryStorageSpec` — round-trips incl. the `EntityStateKey`
  Message ID, upsert overwrite on same version, `stateAt`, `trim`, `truncate`.
- `ConcurrentHistoryCreationSpec` — concurrent
  `createEntityStateHistoryStorage` tolerance.

## Settled while implementing

- Grouped kind naming: `Kind.of(recordType, group)` → group name + `_` +
  record type simple name (`spine.test.storage.StgProject_Event`), following
  the jdbc naming rule (product owner, 2026-08-04).
- `RecordLayout`/`FlatLayout` gained `Kind`-accepting constructors, so a
  grouped storage can carry a kind not derived from a record type alone.
- The two dead suites (`DsAggregateStorageTest`,
  `DsAggregateStorageTruncationTest`) are deleted;
  `DsRecordStorageTest` retargeted at `DelegatingRecordStorageTest`.
- The branch's `Update config` had reverted the Testcontainers `2.0.5` pin
  (PR #202) back to config's `1.21.4`, breaking `testlib` compilation.
  The pin is re-applied, now in `config`'s KDoc style with the 2.x
  `testcontainers-` artifact renames spelled out — the shape intended for
  the `config` repo, where the lasting fix belongs (flagged as
  a separate task).

- Custom naming/layout for grouped kinds (the Datastore analog of jdbc's
  `setTableName(stateType, recordType, name)` overload, requested for jdbc
  in the review of its PR #181): the new
  `organizeRecords(stateType, recordType, layout)` builder overload
  registers a `RecordLayout` — carrying a custom kind, an ancestor
  structure, or both — for the grouped storage addressed by the storage
  group (named by the framework after the entity state type) paired with
  the record type. Registrations live in `RecordLayouts` keyed by
  `(group name, record type)`; the single-type `organizeRecords` keeps
  applying to ungrouped storages only. `EntityGroupLayout` gained
  a `Kind`-accepting constructor for parity with `FlatLayout`.

## Follow-ups (out of scope)

- `DsRecordStorage.deleteRecord()` always returns `true` (documented: telling
  would take another Datastore request), deviating from the
  `RecordStorage.delete` contract ("`false` if not found") that
  `HistoryStorage.delete` re-exposes. Decide whether the backend should pay
  the existence check, or the framework KDoc should allow the deviation.

## Status

Implemented; the four history specs (30 cases) pass against the emulator.
Delete this file on merge to master.
