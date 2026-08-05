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

package io.spine.server.storage.datastore.record

import com.google.protobuf.Timestamp
import com.google.protobuf.util.Durations
import com.google.protobuf.util.Timestamps.add
import com.google.protobuf.util.Timestamps.subtract
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.spine.base.Identifier
import io.spine.base.Time.currentTime
import io.spine.core.Event
import io.spine.core.Versions.increment
import io.spine.core.Versions.zero
import io.spine.server.entity.storage.EntityEventStorage
import io.spine.server.storage.datastore.given.DatastoreStorageFactoryTestEnv.TestEntity
import io.spine.server.storage.datastore.record.given.HistoryStorageTestEnv.context
import io.spine.test.storage.StgProjectId
import io.spine.test.storage.event.StgProjectCreated
import io.spine.test.storage.stgProjectId
import io.spine.testdata.Sample
import io.spine.testing.server.TestEventFactory
import io.spine.testing.server.storage.datastore.EmulatorTest
import io.spine.testing.server.storage.datastore.TestDatastoreStorageFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests that the Datastore-backed record storage serves the [EntityEventStorage] contract.
 *
 * The framework logic of the journal is covered in `core-jvm`. These tests prove
 * the Datastore persistence serves the query shapes the journal relies upon: the equality
 * filtering by a packed entity identifier, the descending sorting by the version
 * and the creation time, the read window limits, and the timestamp-comparison
 * deletion behind [EntityEventStorage.truncate].
 */
@DisplayName("Datastore-backed `EntityEventStorage` should")
@EmulatorTest
internal class DsEntityEventStorageSpec {

    private val entityId = stgProjectId { id = "journaled-entity" }
    private val anotherEntity = stgProjectId { id = "another-entity" }
    private val factory = TestDatastoreStorageFactory.local()
    private lateinit var storage: EntityEventStorage<StgProjectId>
    private var version = zero()

    @BeforeEach
    fun createStorage() {
        storage = factory.createEntityEventStorage(context(), TestEntity::class.java)
        version = zero()
    }

    @AfterEach
    fun clearData() {
        factory.tearDown()
    }

    @Test
    fun `provide an empty history for an unknown entity`() {
        storage.historyBackward(entityId, Int.MAX_VALUE)
            .events()
            .shouldBeEmpty()
    }

    @Test
    fun `store an event as-is, keyed by its identifier and producer`() {
        val event = newEvent(entityId)

        storage.write(event)

        val read = storage.historyBackward(entityId, batchSize = 1).next()
        read shouldBe event
    }

    @Test
    fun `read the journaled events newest first`() {
        val written = appendEvents(count = 5)

        val read = storage.historyBackward(entityId, Int.MAX_VALUE)

        read.events() shouldContainExactly written.reversed()
    }

    @Test
    fun `limit the read window to the requested batch size`() {
        val written = appendEvents(count = 5)

        val read = storage.historyBackward(entityId, batchSize = 2)

        read.events() shouldContainExactly listOf(written[4], written[3])
    }

    @Test
    fun `read only the events below the given starting version`() {
        val written = appendEvents(count = 5)
        val versionOfThird = written[2].context.version

        val read = storage.historyBackward(
            entityId,
            batchSize = Int.MAX_VALUE,
            startingFrom = versionOfThird
        )

        read.events() shouldContainExactly listOf(written[1], written[0])
    }

    @Test
    fun `read only the events emitted by the entity with the given identifier`() {
        val written = appendEvents(count = 2)
        appendEvents(count = 3, toEntity = anotherEntity)

        val read = storage.historyBackward(entityId, Int.MAX_VALUE)

        read.events() shouldContainExactly written.reversed()
    }

    @Test
    fun `delete the journaled events by their identifiers`() {
        val written = appendEvents(count = 3)
        val newest = written[2]

        storage.delete(newest.id) shouldBe true
        // Unlike other backends, the Datastore-backed storage reports `true` even
        // for an absent record: telling would take another Datastore request.
        // See `DsRecordStorage.deleteRecord()`.
        storage.delete(newest.id) shouldBe true
        storage.deleteAll(listOf(written[0].id, written[1].id))

        storage.historyBackward(entityId, Int.MAX_VALUE)
            .events()
            .shouldBeEmpty()
    }

    @Test
    fun `truncate the journal, deleting events older than the given time across entities`() {
        val longAgo = subtract(currentTime(), Durations.fromDays(365))
        appendEvents(count = 2, at = longAgo)
        appendEvents(count = 2, at = longAgo, toEntity = anotherEntity)
        val ours = appendEvents(count = 2)
        val theirs = appendEvents(count = 2, toEntity = anotherEntity)
        val cutoff = subtract(currentTime(), Durations.fromDays(30))

        storage.truncate(cutoff)

        storage.historyBackward(entityId, Int.MAX_VALUE)
            .events() shouldContainExactly ours.reversed()
        storage.historyBackward(anotherEntity, Int.MAX_VALUE)
            .events() shouldContainExactly theirs.reversed()
    }

    @Test
    fun `keep the whole journal when every event is newer than the truncation cutoff`() {
        val written = appendEvents(count = 3)
        val pastCutoff = subtract(currentTime(), Durations.fromDays(365))

        storage.truncate(pastCutoff)

        storage.historyBackward(entityId, Int.MAX_VALUE)
            .events() shouldContainExactly written.reversed()
    }

    @Test
    fun `purge the whole journal when the truncation cutoff is in the future`() {
        appendEvents(count = 4)
        val futureCutoff = add(currentTime(), Durations.fromDays(1))

        storage.truncate(futureCutoff)

        storage.historyBackward(entityId, Int.MAX_VALUE)
            .events()
            .shouldBeEmpty()
    }

    /**
     * Appends the given number of events, with sequentially growing versions,
     * to the journal of the entity with the given identifier.
     *
     * The versions continue growing across the calls within one test, so that
     * the batches appended later are the more recent ones.
     *
     * @return the appended events in the order of their versions.
     */
    private fun appendEvents(
        count: Int,
        toEntity: StgProjectId = entityId,
        at: Timestamp? = null
    ): List<Event> {
        val eventFactory = eventFactoryFor(toEntity)
        val events = List(count) {
            version = increment(version)
            val message = Sample.messageOfType(StgProjectCreated::class.java)
            if (at != null) {
                eventFactory.createEvent(message, version, at)
            } else {
                eventFactory.createEvent(message, version)
            }
        }
        events.forEach {
            storage.write(it)
        }
        return events
    }

    private fun newEvent(producer: StgProjectId): Event =
        eventFactoryFor(producer)
            .createEvent(Sample.messageOfType(StgProjectCreated::class.java))

    private fun Iterator<Event>.events(): List<Event> = asSequence().toList()

    private companion object {

        /**
         * Creates an event factory producing the events on behalf of the entity
         * with the passed identifier.
         *
         * The journal stores an event under its producer, so the tests emit
         * the events with the identifier they later read the history by.
         */
        private fun eventFactoryFor(entityId: StgProjectId): TestEventFactory =
            TestEventFactory.newInstance(
                Identifier.pack(entityId),
                DsEntityEventStorageSpec::class.java
            )
    }
}
