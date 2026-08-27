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

package io.spine.server.storage.datastore

import io.spine.environment.Tests
import io.spine.server.ServerEnvironment
import io.spine.server.event.store.DefaultEventStoreTest
import io.spine.testing.server.storage.datastore.EmulatorTest
import io.spine.testing.server.storage.datastore.TestDatastoreStorageFactory
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.DisplayName

/**
 * Runs the [DefaultEventStoreTest] contract against [DatastoreStorageFactory]
 * over the local Datastore emulator.
 *
 * The base suite builds a Bounded Context in its `@BeforeEach`, which runs
 * before any `@BeforeEach` of this class could. JUnit creates a new test
 * instance per test method, so the constructor of this class is the only
 * seam ahead of the base setup: it wipes the emulator data left by
 * the previous test and points the test server environment to the factory,
 * keeping the persistent event kinds from leaking between the tests.
 */
@DisplayName("Datastore-backed `EventStore` should")
@EmulatorTest
internal class DatastoreDefaultEventStoreTest : DefaultEventStoreTest() {

    init {
        factory.clear()
        ServerEnvironment.`when`(Tests::class.java).use(factory)
    }

    companion object {

        private val factory = TestDatastoreStorageFactory.local()

        /**
         * Detaches the storage configuration from the test server environment
         * and wipes the emulator data.
         */
        @AfterAll
        @JvmStatic
        fun resetEnvironment() {
            ServerEnvironment.instance().reset()
            factory.tearDown()
        }
    }
}
