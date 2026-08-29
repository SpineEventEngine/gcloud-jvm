/*
 * Copyright 2023, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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

package io.spine.testing.server.storage.datastore;

import io.spine.io.Resource;
import io.spine.server.storage.datastore.ProjectId;
import io.spine.testing.UtilityClassTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.testing.server.storage.datastore.TestDatastores.DEFAULT_LOCAL_PROJECT_ID;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@DisplayName("`TestDatastores` utility should")
@EmulatorTest
class TestDatastoresTest extends UtilityClassTest<TestDatastores> {

    TestDatastoresTest() {
        super(TestDatastores.class);
    }

    @Nested
    @DisplayName("create a `Datastore` connected to the Docker-based Datastore emulator running with")
    class CreateLocalDatastore {

        @Test
        @DisplayName("the default project ID")
        void atDefaultAddress() {
            var datastore = TestDatastores.local();
            var project = datastore.getOptions().getProjectId();
            assertThat(project).isEqualTo(DEFAULT_LOCAL_PROJECT_ID.value());
        }

        @Test
        @DisplayName("a custom project ID")
        void atCustomPortWithCustomId() {
            var id = "the-test-project";
            var projectId = ProjectId.of(id);
            var datastore = TestDatastores.local(projectId);

            var options = datastore.getOptions();

            var actualProjectId = options.getProjectId();
            assertThat(actualProjectId).isEqualTo(id);
        }
    }

    @Nested
    @DisplayName("create a `Datastore` connected to the remote Datastore described by")
    class CreateRemoteDatastore {

        private static final String SPINE_DEV_JSON = "spine-dev.json";
        private static final String PROJECT_ID = "spine-dev";

        /**
         * Skips these tests when the service-account credential is absent.
         *
         * <p>The credential is copied into test resources only on machines and CI jobs
         * that have it, and the root build script treats its absence as a warning rather
         * than a failure, expecting the remote suites to skip. This assumption restores
         * that contract.
         */
        @BeforeEach
        void requireCredential() {
            assumeTrue(
                    TestDatastoresTest.class.getClassLoader()
                                            .getResource(SPINE_DEV_JSON) != null,
                    "The `" + SPINE_DEV_JSON + "` credential is not available."
            );
        }

        @Test
        @DisplayName("the service account resource at path")
        void byResourceAtPath() {
            var datastore = TestDatastores.remote(SPINE_DEV_JSON);
            var projectId = datastore.getOptions()
                                     .getProjectId();
            assertThat(projectId).isEqualTo(PROJECT_ID);
        }

        @Test
        @DisplayName("the service account resource")
        void byResource() {
            var serviceAccount = localResource(SPINE_DEV_JSON);
            var datastore = TestDatastores.remote(serviceAccount);
            var projectId = datastore.getOptions()
                                     .getProjectId();
            assertThat(projectId).isEqualTo(PROJECT_ID);
        }
    }

    @Test
    @DisplayName("throw an `ISE` when can't properly parse account credentials from resource")
    void throwOnInvalidResource() {
        var resource = localResource("random.json");
        assertThrows(IllegalStateException.class, () -> TestDatastores.remote(resource));
    }

    private static Resource localResource(String path) {
        return Resource.file(path, TestDatastoresTest.class.getClassLoader());
    }
}
