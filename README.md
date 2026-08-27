# gcloud-jvm

[![codecov][codecov-badge]][codecov]
[![Ubuntu build][ubuntu-build-badge]][gh-actions] &nbsp;
[![license][license-badge]][license]


[codecov]: https://codecov.io/gh/SpineEventEngine/gcloud-jvm
[codecov-badge]: https://codecov.io/gh/SpineEventEngine/gcloud-jvm/branch/master/graph/badge.svg
[gh-actions]: https://github.com/SpineEventEngine/gcloud-jvm/actions
[ubuntu-build-badge]: https://github.com/SpineEventEngine/gcloud-jvm/actions/workflows/build-on-ubuntu-gcloud.yml/badge.svg
[license]: http://www.apache.org/licenses/LICENSE-2.0
[license-badge]: https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat

 
Support for Spine-based Java apps running at Google Cloud.

### Java Version

Starting version `2.0.0-SNAPSHOT.180`, the artifacts of this library are being built with a Java 17
compilation target. Therefore, consumer applications have to use Java 17 or higher.

Versions starting from `2.0.0-SNAPSHOT.63` were built with Java 11, and earlier versions with Java 8.

### Artifacts

Gradle:

```kotlin
dependencies {
    // Datastore Storage support library.
    implementation("io.spine.gcloud:spine-datastore:$version")

    // Pub/Sub messaging support library.
    implementation("io.spine.gcloud:spine-pubsub:$version")

    // Datastore-related test utilities (if needed).
    testImplementation("io.spine.tools:gcloud-testlib:$version")
}
```

These artifacts should be used as a part of the Spine server application.
 
For the details on setting up the server environment please refer to
[Spine `core` modules][core-jvm] documentation. 

[core-jvm]: https://github.com/SpineEventEngine/core-jvm/

### Tracing

Distributed tracing is provided through OpenTelemetry. See
[Enabling OpenTelemetry tracing on Google Cloud](docs/opentelemetry.md) for setup.

### Migrating the event log

The event store of each Bounded Context lands in its own Datastore kind, such as
`Billing-Event`. Applications upgrading from the versions that kept a single
`spine.core.Event` kind shared by all contexts should migrate the stored events;
see [Migrating the event log](docs/event-log-migration.md).

### Configuring Datastore

#### Datastore indexes

To run the application built on top of `gcloud-jvm`, Datastore instance requires some 
preliminary configuration. In particular, the indexes for the Spine internal record types should 
be set. Please notice a special index configuration for your custom `Aggregate` types.

Please see the `datastore/config` folder the configuration files.
The configuration file for tests is located at `./datastore/src/test/config/index.yaml`.

Please see the [Google Cloud Platform documentation][datastore-index] for the details.

[datastore-index]: https://cloud.google.com/datastore/docs/tools/indexconfig

##### Custom indexes

It is possible to store some of the Spine `Entity` fields in separate Datastore kind fields. 
Such an approach is useful to optimize read-side querying. In this case more Datastore indexes may
 be created.

__Example:__
Assuming you have a Projection type called `CustomerProjection`. Its state is declared in 
the Protobuf type `my.company.Customer`. It has Entity Columns `country` and `companySize`. 
Once you try to make a query in those Columns, the Datastore will fail with 
an internal Exception. To prevent this, you should create an index for your `CustomerProjection`:

```yaml
- kind: my.company.Customer
    ancestor: no
    properties:
    - name: country
    - name: companySize
```

### Testing

This section describes testing the `gcloud-jvm` library itself.

#### Preconditions

The library utilizes Testcontainers in order 
to run a [local Datastore emulator](https://java.testcontainers.org/modules/gcloud/#datastore).

Therefore, a local Docker is required up and running to launch tests. 

#### Running the tests

To start a local Docker-based emulator and run test against it, run `./gradlew check`.

The emulator container is reused across tests and shuts down automatically after the test run
completes.

Some tests also verify a connection to a remote Datastore instance. In order to run those,
the corresponding credential file called `spine-dev.json` should be placed under
`<project root>/datastore/src/test/resources/` and `<project root>/testlib/src/test/resources/`.

Gradle build script is arranged to do that automatically upon running on CI.
