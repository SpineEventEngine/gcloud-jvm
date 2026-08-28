/*
 * Copyright 2026, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Redistribution and use in source and/or binary forms, with or without
 * modification, must retain the above copyright notice and the following
 * disclaimer.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import io.spine.dependency.lib.GoogleCloud
import io.spine.dependency.local.Base
import io.spine.dependency.local.BaseTypes
import io.spine.dependency.local.CoreJvm
import io.spine.dependency.local.Logging
import io.spine.gradle.publish.IncrementGuard

apply<IncrementGuard>()

dependencies {
    // Google Cloud Datastore
    api(GoogleCloud.datastore) {
        exclude(group = "com.google.protobuf")
        exclude(group = "com.google.guava")
    }
    api(Base.lib)
    api(BaseTypes.lib)

    api(Logging.lib)

    testImplementation(project(":testlib"))

    // The test fixtures of `spine-server` provide the shared contract suites,
    // such as `DefaultEventStoreTest` extended by `DatastoreDefaultEventStoreTest`.
    // The main `spine-server` classes reach the test classpath transitively:
    // the published fixtures variant depends on its own main component.
    //
    // The capability is requested explicitly because `spine-server` publishes its
    // test fixtures under the `io.spine:server-test-fixtures` capability (derived from
    // the `server` module name), which does not match the name the `testFixtures(...)`
    // helper would expect for the `spine-`-prefixed artifact.
    testImplementation(CoreJvm.server) {
        capabilities {
            requireCapability("io.spine:server-test-fixtures")
        }
    }
}

tasks {

    // Turn to `WARN` and investigate duplicates.
    // See: https://github.com/SpineEventEngine/base/issues/657
    val strategy = DuplicatesStrategy.INCLUDE

    processResources { duplicatesStrategy = strategy }
    processTestResources { duplicatesStrategy = strategy }
    jar { duplicatesStrategy = strategy }
}
