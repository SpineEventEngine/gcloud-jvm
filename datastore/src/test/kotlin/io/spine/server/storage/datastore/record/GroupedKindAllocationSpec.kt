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

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.spine.base.Identifier
import io.spine.core.Event
import io.spine.core.EventId
import io.spine.query.RecordQuery
import io.spine.server.entity.EntityRecord
import io.spine.server.entity.EntityStateKey
import io.spine.server.storage.RecordSpec
import io.spine.server.storage.RecordStorage
import io.spine.server.storage.StorageGroup
import io.spine.server.storage.datastore.DatastoreStorageFactory
import io.spine.server.storage.datastore.Kind
import io.spine.server.storage.datastore.config.CreateRecordStorage
import io.spine.server.storage.datastore.config.FlatLayout
import io.spine.server.storage.datastore.given.DatastoreStorageFactoryTestEnv.DifferentTestEntity
import io.spine.server.storage.datastore.given.DatastoreStorageFactoryTestEnv.TestEntity
import io.spine.server.storage.datastore.record.given.ProjectChildJournalLayout
import io.spine.server.storage.datastore.record.given.HistoryStorageTestEnv.context
import io.spine.server.storage.datastore.record.given.HistoryStorageTestEnv.journalSpec
import io.spine.server.storage.datastore.record.given.HistoryStorageTestEnv.stateHistorySpec
import io.spine.test.storage.StgProject
import io.spine.test.storage.StgProjectId
import io.spine.test.storage.event.StgProjectCreated
import io.spine.test.storage.stgProjectId
import io.spine.testdata.Sample
import io.spine.testing.server.TestEventFactory
import io.spine.testing.server.storage.datastore.EmulatorTest
import io.spine.testing.server.storage.datastore.TestDatastoreStorageFactory
import io.spine.testing.server.storage.datastore.TestDatastores
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests that [DatastoreStorageFactory] allocates a distinct kind per the combination
 * of a record specification and a [StorageGroup], as the framework expects
 * of storage vendors.
 *
 * Without the group taking part in the kind identity, the storages holding records
 * of the same type would conflate: the event journals of all entity types — and
 * the event log of the Bounded Context — store `Event`s, and the state history
 * of an entity type stores `EntityRecord`s, just as its latest-state storage does.
 */
@DisplayName("`DatastoreStorageFactory`, when allocating grouped kinds, should")
@EmulatorTest
internal class GroupedKindAllocationSpec {

    private val projectGroup = StorageGroup.of(TestEntity::class.java)
    private val collegeGroup = StorageGroup.of(DifferentTestEntity::class.java)

    private val factory = TestDatastoreStorageFactory.local()

    /**
     * A factory with a customized builder, created by some tests;
     * torn down by [clearData] along with the default [factory].
     */
    private var customized: TestDatastoreStorageFactory? = null

    @AfterEach
    fun clearData() {
        factory.tearDown()
        customized?.tearDown()
        customized = null
    }

    @Test
    fun `tell apart the latest state, event journal, and state history of an entity type`() {
        val latestState = factory.createRecordStorage(context(), TestEntity.spec())
        val journal = factory.createRecordStorage(context(), journalSpec(), projectGroup)
        val stateHistory = factory.createRecordStorage(context(), stateHistorySpec(), projectGroup)

        latestState.kindName() shouldBe "spine.test.storage.StgProject"
        journal.kindName() shouldBe "spine.test.storage.StgProject-Event"
        stateHistory.kindName() shouldBe "spine.test.storage.StgProject-EntityRecord"
    }

    @Test
    fun `allocate distinct kinds to the event journals of different entity types`() {
        val projectJournal = factory.createRecordStorage(context(), journalSpec(), projectGroup)
        val collegeJournal = factory.createRecordStorage(context(), journalSpec(), collegeGroup)

        projectJournal.kindName() shouldBe "spine.test.storage.StgProject-Event"
        collegeJournal.kindName() shouldBe "spine.test.datastore.College-Event"
    }

    @Test
    fun `keep the events of an entity type out of the journals of other types`() {
        val projectJournal =
            factory.createEntityEventStorage(context(), TestEntity::class.java)
        val collegeJournal =
            factory.createEntityEventStorage(context(), DifferentTestEntity::class.java)

        projectJournal.write(newEvent())

        // Read each journal in full, without filtering by an entity,
        // to observe the whole underlying kind.
        val everythingJournaled =
            RecordQuery.newBuilder(EventId::class.java, Event::class.java)
                .build()
        collegeJournal.readAll(everythingJournaled)
            .asSequence()
            .toList()
            .shouldBeEmpty()
        projectJournal.readAll(everythingJournaled)
            .asSequence()
            .toList() shouldHaveSize 1
    }

    @Test
    fun `serve one physical kind to the repeatedly created storages of one group`() {
        val first = factory.createEntityEventStorage(context(), TestEntity::class.java)
        val second = factory.createEntityEventStorage(context(), TestEntity::class.java)
        val event = newEvent()

        first.write(event)

        second.historyBackward(producerOf(event), batchSize = 1)
            .asSequence()
            .toList() shouldContainExactly listOf(event)
    }

    @Test
    fun `apply a custom record layout only to the storages outside any group`() {
        val customKind = Kind.of("custom_event_log")
        val customized = customizedWith(
            DatastoreStorageFactory.newBuilderWithDefaults(TestDatastores.local())
                .organizeRecords(Event::class.java, FlatLayout<EventId, Event>(customKind))
        )

        val eventLog = customized.createRecordStorage(context(), ungroupedEventsSpec())
        val journal = customized.createRecordStorage(context(), journalSpec(), projectGroup)

        // The name of the ungrouped event log is customized by the record type.
        eventLog.kindName() shouldBe "custom_event_log"
        // The grouped journal of the same record type keeps its generated kind.
        journal.kindName() shouldBe "spine.test.storage.StgProject-Event"
    }

    @Test
    fun `apply a custom storage only to the storages outside any group`() {
        val customStorages = mutableListOf<RecordStorage<EventId, Event>>()
        val createCustomStorage = CreateRecordStorage<EventId, Event> { config ->
            DsRecordStorage(config).also { customStorages.add(it) }
        }
        val customized = customizedWith(
            DatastoreStorageFactory.newBuilderWithDefaults(TestDatastores.local())
                .useRecordStorage(
                    EventId::class.java,
                    Event::class.java,
                    createCustomStorage
                )
        )

        val eventLog = customized.createRecordStorage(context(), ungroupedEventsSpec())
        customStorages shouldContainExactly listOf(eventLog)

        val journal = customized.createRecordStorage(context(), journalSpec(), projectGroup)
        customStorages shouldContainExactly listOf(eventLog)
        journal.kindName() shouldBe "spine.test.storage.StgProject-Event"
    }

    @Test
    fun `organize a grouped storage by the state and the record types it serves`() {
        val customized = customizedWith(
            DatastoreStorageFactory.newBuilderWithDefaults(TestDatastores.local())
                .organizeRecords(
                    StgProject::class.java,
                    Event::class.java,
                    FlatLayout<EventId, Event>(Kind.of("project_journal"))
                )
                .organizeRecords(
                    StgProject::class.java,
                    EntityRecord::class.java,
                    FlatLayout<EntityStateKey, EntityRecord>(Kind.of("project_state_history"))
                )
        )

        val journal = customized.createRecordStorage(context(), journalSpec(), projectGroup)
        val stateHistory =
            customized.createRecordStorage(context(), stateHistorySpec(), projectGroup)
        val latestState = customized.createRecordStorage(context(), TestEntity.spec())
        val collegeJournal = customized.createRecordStorage(context(), journalSpec(), collegeGroup)

        journal.kindName() shouldBe "project_journal"
        stateHistory.kindName() shouldBe "project_state_history"
        // The registration does not affect the ungrouped storage of the same entity type.
        latestState.kindName() shouldBe "spine.test.storage.StgProject"
        // Nor does it affect the group of a different entity type.
        collegeJournal.kindName() shouldBe "spine.test.datastore.College-Event"

        // The custom-named journal is fully operational.
        val storage = customized.createEntityEventStorage(context(), TestEntity::class.java)
        val event = newEvent()
        storage.write(event)
        storage.historyBackward(producerOf(event), batchSize = 1)
            .asSequence()
            .toList() shouldContainExactly listOf(event)
    }

    @Test
    fun `serve an ancestor layout registered for a grouped storage`() {
        val layout = ProjectChildJournalLayout()
        val customized = customizedWith(
            DatastoreStorageFactory.newBuilderWithDefaults(TestDatastores.local())
                .organizeRecords(StgProject::class.java, Event::class.java, layout)
        )

        val journal = customized.createRecordStorage(context(), journalSpec(), projectGroup)
        journal.kindName() shouldBe ProjectChildJournalLayout.KIND

        // The layout shapes the record keys: each journaled event is
        // a child of a project record.
        val wrapper = customized.newDatastoreWrapper(false)
        val eventId = EventId.newBuilder()
            .setValue("evt-under-ancestor")
            .build()
        val key = layout.keyOf(eventId, wrapper)
        key.kind shouldBe ProjectChildJournalLayout.KIND
        val ancestor = key.ancestors.single()
        ancestor.kind shouldBe Kind.of(StgProject::class.java).value()
        ancestor.name shouldBe ProjectChildJournalLayout.PARENT_RECORD_ID
    }

    /**
     * Builds a factory over the given builder, registering it for
     * the [clearData] teardown.
     */
    private fun customizedWith(
        builder: DatastoreStorageFactory.Builder
    ): TestDatastoreStorageFactory =
        TestDatastoreStorageFactory.basedOn(builder)
            .also { customized = it }

    private fun ungroupedEventsSpec(): RecordSpec<EventId, Event> =
        RecordSpec(
            EventId::class.java,
            Event::class.java
        ) { event -> event.id }

    private fun RecordStorage<*, *>.kindName(): String =
        shouldBeInstanceOf<DsRecordStorage<*, *>>()
            .kind()
            .value()

    private fun newEvent(): Event {
        val producer = stgProjectId {
            id = "grouped-kinds-entity"
        }
        val eventFactory = TestEventFactory.newInstance(
            Identifier.pack(producer),
            GroupedKindAllocationSpec::class.java
        )
        return eventFactory.createEvent(Sample.messageOfType(StgProjectCreated::class.java))
    }

    private fun producerOf(event: Event): StgProjectId =
        Identifier.unpack(event.context.producerId, StgProjectId::class.java)
}
