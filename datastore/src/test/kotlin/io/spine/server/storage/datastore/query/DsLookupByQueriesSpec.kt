/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package io.spine.server.storage.datastore.query

import com.google.protobuf.Timestamp
import com.google.protobuf.util.Durations
import com.google.protobuf.util.Timestamps
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.spine.base.Time.currentTime
import io.spine.query.Either
import io.spine.query.RecordQuery
import io.spine.query.RecordQueryBuilder
import io.spine.server.ContextSpec
import io.spine.server.storage.datastore.config.DsColumnMapping
import io.spine.server.storage.given.DelegatingRecordStorageTestEnv.generateId
import io.spine.server.storage.given.GivenStorageProject.StgProjectColumns.due_date
import io.spine.server.storage.given.GivenStorageProject.StgProjectColumns.status
import io.spine.server.storage.given.GivenStorageProject.newState
import io.spine.server.storage.given.StgProjectStorage
import io.spine.test.storage.StgProject
import io.spine.test.storage.StgProject.Status
import io.spine.test.storage.StgProject.Status.CANCELLED
import io.spine.test.storage.StgProject.Status.CREATED
import io.spine.test.storage.StgProject.Status.DONE
import io.spine.test.storage.StgProjectId
import io.spine.testing.server.storage.datastore.EmulatorTest
import io.spine.testing.server.storage.datastore.TestDatastoreStorageFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

private typealias ProjectQuery = RecordQuery<StgProjectId, StgProject>
private typealias ProjectQueryBuilder = RecordQueryBuilder<StgProjectId, StgProject>

/**
 * Tests reading the records by a query which runs as several Datastore queries.
 *
 * The storage runs a query with alternatives, such as `either(..)`, as one Datastore
 * query per conjunctive filter. [DsLookupByQueries] joins the results of these queries
 * and removes the duplicates; then [SortAndLimit] sorts and limits the joined records
 * in memory.
 *
 * A query which runs as a single Datastore query is covered by `DsRecordStorageTest`.
 */
@DisplayName("`DsLookupByQueries`, when a query runs as several Datastore queries, should")
@EmulatorTest
internal class DsLookupByQueriesSpec {

    private val factory = TestDatastoreStorageFactory.local()

    private val storage = StgProjectStorage(
        ContextSpec.singleTenant(DsLookupByQueriesSpec::class.java.name),
        factory
    )

    private val now = currentTime()

    @AfterEach
    fun clearData() {
        storage.close()
        factory.tearDown()
    }

    @Test
    fun `return the records matching any of the alternatives`() {
        val done = project(DONE)
        val cancelled = project(CANCELLED)
        write(done, cancelled, project(CREATED))

        val query = queryBuilder()
            .either(statusIs(DONE), statusIs(CANCELLED))
            .buildSplit()

        readAll(query) shouldContainExactlyInAnyOrder listOf(done, cancelled)
    }

    @Test
    fun `return a record matching several alternatives once`() {
        val cutoff = now.plusMinutes(10)
        val later = cutoff.plusMinutes(1)
        val doneEarly = project(DONE, dueDate = now)
        val doneLate = project(DONE, dueDate = later)
        val createdEarly = project(CREATED, dueDate = now)
        write(doneEarly, doneLate, createdEarly, project(CREATED, dueDate = later))

        val query = queryBuilder()
            .either(statusIs(DONE), dueBefore(cutoff))
            .buildSplit()

        readAll(query) shouldContainExactlyInAnyOrder listOf(doneEarly, doneLate, createdEarly)
    }

    @Test
    fun `sort the joined records`() {
        val first = project(DONE, dueDate = now.plusMinutes(1))
        val second = project(CANCELLED, dueDate = now.plusMinutes(2))
        val third = project(DONE, dueDate = now.plusMinutes(3))
        write(first, second, third)

        val query = queryBuilder()
            .either(statusIs(DONE), statusIs(CANCELLED))
            .sortAscendingBy(due_date)
            .buildSplit()

        readAll(query) shouldContainExactly listOf(first, second, third)
    }

    @Test
    fun `limit the joined records after sorting them`() {
        val latestDone = project(DONE, dueDate = now.plusMinutes(5))
        val latestCancelled = project(CANCELLED, dueDate = now.plusMinutes(4))
        write(
            project(DONE, dueDate = now.plusMinutes(1)),
            latestDone,
            project(CANCELLED, dueDate = now.plusMinutes(2)),
            latestCancelled,
            project(CREATED, dueDate = now.plusMinutes(9))
        )

        val query = queryBuilder()
            .either(statusIs(DONE), statusIs(CANCELLED))
            .sortDescendingBy(due_date)
            .limit(2)
            .buildSplit()

        readAll(query) shouldContainExactly listOf(latestDone, latestCancelled)
    }

    private fun queryBuilder(): ProjectQueryBuilder =
        RecordQuery.newBuilder(StgProjectId::class.java, StgProject::class.java)

    /**
     * Builds the query, asserting that it runs as two Datastore queries.
     *
     * The premise is asserted so that the tests cannot silently stop covering
     * the joining of several Datastore queries if the query model changes.
     */
    private fun ProjectQueryBuilder.buildSplit(): ProjectQuery {
        val query = build()
        val datastoreQueries = DsFilters.fromPredicate(
            query.subject().predicate(),
            FilterAdapter.of(DsColumnMapping())
        )
        check(datastoreQueries.size == 2) {
            "The query must run as 2 Datastore queries, but runs as ${datastoreQueries.size}."
        }
        return query
    }

    private fun statusIs(value: Status): Either<ProjectQueryBuilder> =
        Either { it.where(status).`is`(value.name) }

    private fun dueBefore(time: Timestamp): Either<ProjectQueryBuilder> =
        Either { it.where(due_date).isLessThan(time) }

    private fun project(projectStatus: Status, dueDate: Timestamp = now): StgProject =
        newState(generateId(), projectStatus, dueDate)

    private fun write(vararg projects: StgProject) = storage.writeBatch(projects.toList())

    private fun readAll(query: ProjectQuery): List<StgProject> =
        storage.readAll(query).asSequence().toList()
}

private fun Timestamp.plusMinutes(minutes: Long): Timestamp =
    Timestamps.add(this, Durations.fromMinutes(minutes))
