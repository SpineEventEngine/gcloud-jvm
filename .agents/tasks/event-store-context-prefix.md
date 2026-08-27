# Per-context event kinds: the `gcloud-jvm` part

## Status

Implementation in progress. The rationale and the cross-repo scenario live in
core-jvm's task plan for the same branch name; the `jdbc-storage` part is merged
(jdbc-storage PR #182).

## Background

Core-jvm `2.0.0-SNAPSHOT.540` groups the event store storage by the Bounded
Context (`StorageGroup.of(BoundedContextName)`, core-jvm PR #1673). This library
already honors groups: `createRecordStorage(..)` with a non-null group routes to
`RecordLayouts.find(recordType, group)`, defaulting to a flat layout under
`Kind.of(recordType, group)` — e.g. the `Billing-Event` kind. Once the core-jvm
dependency is bumped, each context's event log lands in its own kind with **no
storage-code changes here**.

## Engine semantics (why this repo needs less than `jdbc-storage`)

Checked per the engine-semantics checklist seeded in `jdbc-storage`:

- **No sanitization.** Datastore kinds are arbitrary UTF-8; the only guard —
  no `__` prefix — already exists in `Kind` and fails fast.
- **No collision detection.** `Kind.of(recordType, group)` composes
  `<group>-<SimpleName>` verbatim: no character replacement, no truncation,
  and kinds are case-sensitive. Record simple names cannot contain `-`, so the
  composition is injective for distinct group names, and the dash keeps grouped
  kinds apart from type-name-derived ungrouped kinds (documented in `Kind`).
- **Namespaces** serve multitenancy, not contexts — unchanged.

## Work items

0. Bump `CoreJvm` `2.0.0-SNAPSHOT.522` → `2.0.0-SNAPSHOT.540` — the behavioral
   pivot.
1. Context-addressed layout registration, mirroring the `setTableName` overloads
   of `jdbc-storage`:
   - `RecordLayouts.Builder.add(BoundedContextName, Class<R>, RecordLayout<?, R>)`,
     deriving the group via `StorageGroup.of(BoundedContextName)`;
   - `DatastoreStorageFactory.Builder.organizeRecords(BoundedContextName,
     Class<R>, RecordLayout<I, R>)` delegating to it.
   Release note: single-type `organizeRecords(Event.class, ..)` and
   `useRecordStorage(..)` registrations never serve grouped storages (existing,
   documented behavior) — the event store is grouped now.
2. Tests (Docker-backed Datastore emulator via `TestDatastoreStorageFactory`):
   - `KindSpec` (supplements the Java `KindTest`): `Billing-Event` composition,
     verbatim case-sensitivity;
   - `RecordLayouts` honors a context-registered layout via
     `find(recordType, group)`;
   - two-context event-log isolation over one factory;
   - `DatastoreDefaultEventStoreTest` — core's `DefaultEventStoreTest` contract
     against the emulator, with per-test cleanup (`clear()`) and the constructor
     seam installing the factory into the test `ServerEnvironment` (the pattern
     established by `JdbcDefaultEventStoreTest`). Requires the
     `server-test-fixtures` capability dependency, added the way `jdbc-storage`
     declares it.
3. Migration notes: `docs/event-log-migration.md` — historical events stay under
   the `spine.core.Event` kind; copy entities to `<Context>-Event` split by the
   `type` property, iterating namespaces for multi-tenant deployments. Kinds are
   stored verbatim and case-sensitively — no quoting or folding concerns, unlike
   the RDBMS counterpart. Verified against the emulator, not only by reading
   `Kind`: `EventLogIsolationSpec` pins that case-different contexts keep
   separate event logs at the storage level. Linked from `README.md`.
