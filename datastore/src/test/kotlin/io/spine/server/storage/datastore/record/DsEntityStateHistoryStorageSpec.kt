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
import io.spine.core.Versions
import io.spine.protobuf.AnyPacker
import io.spine.server.entity.EntityRecord
import io.spine.server.entity.entityRecord
import io.spine.server.entity.entityStateKey
import io.spine.server.entity.storage.EntityStateHistoryStorage
import io.spine.server.storage.datastore.given.DatastoreStorageFactoryTestEnv.TestEntity
import io.spine.server.storage.datastore.record.given.HistoryStorageTestEnv.context
import io.spine.test.storage.StgProjectId
import io.spine.test.storage.stgProject
import io.spine.test.storage.stgProjectId
import io.spine.testing.server.storage.datastore.EmulatorTest
import io.spine.testing.server.storage.datastore.TestDatastoreStorageFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests that the Datastore-backed record storage serves
 * the [EntityStateHistoryStorage] contract.
 *
 * The framework logic of the state history is covered in `core-jvm`. These tests prove
 * the Datastore persistence serves what the history relies upon:
 * the [EntityStateKey][io.spine.server.entity.EntityStateKey] record identifier —
 * a Protobuf message — including the same-key overwrite, the descending sorting
 * by the creation time and the version, and the timestamp comparisons
 * behind [stateAt][EntityStateHistoryStorage.stateAt]
 * and [truncate][EntityStateHistoryStorage.truncate].
 */
@DisplayName("Datastore-backed `EntityStateHistoryStorage` should")
@EmulatorTest
internal class DsEntityStateHistoryStorageSpec {

    private val entityId = stgProjectId { id = "state-tracked-entity" }
    private val anotherEntity = stgProjectId { id = "another-entity" }
    private val factory = TestDatastoreStorageFactory.local()
    private lateinit var storage: EntityStateHistoryStorage<StgProjectId>
    private var lastVersion = 0

    @BeforeEach
    fun createStorage() {
        storage = factory.createEntityStateHistoryStorage(context(), TestEntity::class.java)
        lastVersion = 0
    }

    @AfterEach
    fun clearData() {
        factory.tearDown()
    }

    @Test
    fun `provide an empty history for an unknown entity`() {
        storage.historyBackward(entityId, Int.MAX_VALUE)
            .records()
            .shouldBeEmpty()
    }

    @Test
    fun `store a record keyed by the entity and the version`() {
        val record = record(number = 1)

        storage.write(record)

        storage.historyBackward(entityId, batchSize = 1)
            .records() shouldContainExactly listOf(record)
        val key = entityStateKey {
            entityId = record.entityId
            version = record.version.number
        }
        storage.delete(key) shouldBe true
    }

    @Test
    fun `overwrite the record stored with the same entity and version`() {
        val original = record(number = 1)
        val overwriting = record(number = 1)

        storage.write(original)
        storage.write(overwriting)

        storage.historyBackward(entityId, Int.MAX_VALUE)
            .records() shouldContainExactly listOf(overwriting)
    }

    @Test
    fun `read the recorded states newest first`() {
        val written = appendRecords(count = 5)

        val read = storage.historyBackward(entityId, Int.MAX_VALUE)

        read.records() shouldContainExactly written.reversed()
    }

    @Test
    fun `limit the read window to the requested batch size`() {
        val written = appendRecords(count = 5)

        val read = storage.historyBackward(entityId, batchSize = 2)

        read.records() shouldContainExactly listOf(written[4], written[3])
    }

    @Test
    fun `read only the records of the entity with the given identifier`() {
        val written = appendRecords(count = 2)
        appendRecords(count = 3, toEntity = anotherEntity)

        val read = storage.historyBackward(entityId, Int.MAX_VALUE)

        read.records() shouldContainExactly written.reversed()
    }

    @Test
    fun `answer the state at the given time with the newest record at or before it`() {
        val start = currentTime()
        val first = writeRecord(number = 1, at = at(start, 10))
        val second = writeRecord(number = 2, at = at(start, 20))
        writeRecord(number = 3, at = at(start, 30))

        storage.stateAt(entityId, at(start, 20)) shouldBe second
        storage.stateAt(entityId, at(start, 25)) shouldBe second
        storage.stateAt(entityId, at(start, 15)) shouldBe first
    }

    @Test
    fun `break the same-instant tie in favor of the higher version`() {
        val instant = currentTime()
        writeRecord(number = 1, at = instant)
        val higher = writeRecord(number = 2, at = instant)

        storage.stateAt(entityId, instant) shouldBe higher
    }

    @Test
    fun `answer with null when the time precedes the oldest retained record`() {
        val start = currentTime()
        writeRecord(number = 3, at = at(start, 20))
        writeRecord(number = 4, at = at(start, 30))

        storage.stateAt(entityId, at(start, 10)) shouldBe null
    }

    @Test
    fun `answer with null for an unknown entity`() {
        storage.stateAt(entityId, currentTime()) shouldBe null
    }

    @Test
    fun `trim the per-entity history, keeping the requested number of the most recent records`() {
        val written = appendRecords(count = 5)
        val theirs = appendRecords(count = 3, toEntity = anotherEntity)

        storage.trim(entityId, keepMostRecent = 2)

        storage.historyBackward(entityId, Int.MAX_VALUE)
            .records() shouldContainExactly listOf(written[4], written[3])
        storage.historyBackward(anotherEntity, Int.MAX_VALUE)
            .records() shouldContainExactly theirs.reversed()
    }

    @Test
    fun `purge the whole history of an entity when trimming to zero`() {
        appendRecords(count = 3)

        storage.trim(entityId, keepMostRecent = 0)

        storage.historyBackward(entityId, Int.MAX_VALUE)
            .records()
            .shouldBeEmpty()
    }

    @Test
    fun `truncate the history, deleting records older than the given time across entities`() {
        val longAgo = subtract(currentTime(), Durations.fromDays(365))
        appendRecords(count = 2, at = longAgo)
        appendRecords(count = 2, at = longAgo, toEntity = anotherEntity)
        val ours = appendRecords(count = 2)
        val theirs = appendRecords(count = 2, toEntity = anotherEntity)
        val cutoff = subtract(currentTime(), Durations.fromDays(30))

        storage.truncate(cutoff)

        storage.historyBackward(entityId, Int.MAX_VALUE)
            .records() shouldContainExactly ours.reversed()
        storage.historyBackward(anotherEntity, Int.MAX_VALUE)
            .records() shouldContainExactly theirs.reversed()
    }

    /**
     * Builds a state record of the entity with the given identifier.
     */
    private fun record(
        entity: StgProjectId = entityId,
        number: Int,
        at: Timestamp = currentTime()
    ): EntityRecord = entityRecord {
        entityId = Identifier.pack(entity)
        state = AnyPacker.pack(
            stgProject {
                id = entity
                name = "State at version $number"
            }
        )
        version = Versions.newVersion(number, at)
    }

    /**
     * Builds and stores a state record of the entity under test.
     */
    private fun writeRecord(number: Int, at: Timestamp): EntityRecord {
        val result = record(number = number, at = at)
        storage.write(result)
        return result
    }

    /**
     * Appends the given number of records, with sequentially growing versions,
     * to the history of the entity with the given identifier.
     *
     * The versions continue growing across the calls within one test, so that
     * the batches appended later are the more recent ones.
     *
     * @return the appended records in the order of their versions.
     */
    private fun appendRecords(
        count: Int,
        toEntity: StgProjectId = entityId,
        at: Timestamp? = null
    ): List<EntityRecord> {
        val records = List(count) {
            lastVersion++
            record(entity = toEntity, number = lastVersion, at = at ?: currentTime())
        }
        records.forEach {
            storage.write(it)
        }
        return records
    }

    private fun Iterator<EntityRecord>.records(): List<EntityRecord> = asSequence().toList()

    private companion object {

        private fun at(start: Timestamp, seconds: Long): Timestamp =
            add(start, Durations.fromSeconds(seconds))
    }
}
