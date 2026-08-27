# Migrating the event log

*Applies when upgrading to the library versions based on `core-jvm`
`2.0.0-SNAPSHOT.540` or later.*

## What changed

Previously, the event stores of all Bounded Contexts of an application wrote to one
Datastore kind, `spine.core.Event`: the kind was derived from the stored record type
alone, so the events of every context — and of every System context configured to
persist its events — intermingled under that single kind.

Starting with `core-jvm` `2.0.0-SNAPSHOT.540`, the event store of each context is
a grouped storage, and its records land under a per-context kind: `Billing-Event`,
`Shipping-Event`, `Billing_System-Event`, and so on. The kind is composed of the
context name — taken verbatim — and the simple name of the record type, joined with
a dash; see `Kind.of(Class, StorageGroup)`. The rationale is discussed in
the `core-jvm` pull request [#1673][core-pr].

Unlike RDBMS identifiers, Datastore kinds involve no quoting, folding, truncation,
or case-insensitivity: the composed kind is stored exactly as written, and
case-different context names denote distinct kinds.

The library does **not** move the previously stored events: an upgraded application
starts reading and writing the per-context kinds, while the historical events stay
under `spine.core.Event`. Deployments that rely on the stored event log — event
replay, projection catch-up, audit — should migrate the historical events before
switching to the upgraded version.

No other kinds are renamed by the upgrade.

## Migrating the stored events

The entities under `spine.core.Event` carry no explicit context marker. The `type`
property — holding the qualified Proto type name of the event — is the
discriminator: each event type belongs to the domain of exactly one context.

The copy has no ready-made tool: the Google-provided Dataflow templates for
Datastore either delete entities or move them verbatim, and a managed
[export and import][ds-export-import] restores every entity under its original
kind. Write the copy yourself — as a script over the [Datastore client
library][ds-client], or, for a large log, as a custom [Apache Beam
pipeline][beam-datastore] run on Dataflow.

> [!WARNING]
> Take a managed [export][ds-export-import] of the `spine.core.Event` kind
> before starting. The steps below only add entities, but an export is the
> cheapest insurance against a mistake in a hand-written migration.

For each context, copy the entities of its event types to the per-context kind,
keeping the key names and all properties intact:

1. Query `spine.core.Event` filtering the `type` property by the event types of
   the context.
2. Re-create each entity under the `<Context>-Event` kind with the same key name
   and properties.
3. Verify the counts per kind. Then keep the `spine.core.Event` entities as an
   archive until the migrated deployment is verified; the upgraded application
   does not touch them.

For a multi-tenant application, repeat the copy in every [namespace][ds-namespace] —
namespaces hold the per-tenant data and are not affected by this change.

## Special cases

* **An event type used by several contexts.** If the same Proto event type is
  emitted by more than one context, the `type` property cannot tell their events
  apart. Decide the ownership per deployment — usually the entities belong to
  the producing context — and split by additional criteria, such as the producer
  identifiers within the serialized records.
* **A custom kind for the event log.** By default, the event log of a context
  lands under the derived `<Context>-Event` kind, as described above. A layout
  registered via the single-type `organizeRecords(Event.class, ...)` no longer
  applies: the event store is a grouped storage now. To choose the kind
  yourself, register the layout per context — the migration then targets that
  custom kind instead of `<Context>-Event`:

  ```java
  .organizeRecords(BoundedContextNames.newName("Billing"), Event.class,
                   new FlatLayout<>(Kind.of("BillingJournal")))
  ```

* **Composite indexes.** If `index.yaml` declares composite indexes for the
  `spine.core.Event` kind, declare the same indexes for each per-context kind.

[core-pr]: https://github.com/SpineEventEngine/core-jvm/pull/1673
[ds-namespace]: https://cloud.google.com/datastore/docs/concepts/multitenancy
[ds-export-import]: https://cloud.google.com/datastore/docs/export-import-entities
[ds-client]: https://cloud.google.com/datastore/docs/reference/libraries
[beam-datastore]: https://beam.apache.org/documentation/io/built-in/google-cloud-datastore/
