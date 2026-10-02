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
import io.spine.server.storage.given.DelegatingRecordStorageTestEnv.idAndDueDate
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
 * Tests that the Datastore storage ignores the field mask of a [RecordQuery],
 * returning the records as they are stored.
 *
 * Field masks are no longer supported by the framework. The tests cover both lookups
 * a [PreparedQuery] may perform: a lookup by the record identifiers ([DsLookupByIds]) and
 * a lookup by Datastore queries ([DsLookupByQueries]), which converts the results of
 * a single query as is ([ConvertAsIs]) or joins, sorts, and limits the results of
 * several queries in memory ([SortAndLimit]).
 *
 * The `DelegatingRecordStorageTest` suite of the framework passes a masked query only
 * to a lookup by a single Datastore query.
 */
@DisplayName("`DsRecordStorage`, when reading by a query with a field mask, should")
@EmulatorTest
internal class IgnoredFieldMaskSpec {

    private val factory = TestDatastoreStorageFactory.local()

    private val storage = StgProjectStorage(
        ContextSpec.singleTenant(IgnoredFieldMaskSpec::class.java.name),
        factory
    )

    @AfterEach
    fun clearData() {
        storage.close()
        factory.tearDown()
    }

    @Test
    fun `return the full records found by their IDs`() {
        val stored = write(project(CREATED), project(DONE), project(CANCELLED))

        val query = queryBuilder()
            .id().`in`(stored.map { it.id })
            .buildWithMask()

        readAll(query) shouldContainExactlyInAnyOrder stored
    }

    @Test
    fun `return the full records found by a single Datastore query`() {
        val done = write(project(DONE), project(DONE))
        write(project(CREATED))

        val query = queryBuilder()
            .where(status).`is`(DONE.name)
            .buildWithMask()

        readAll(query) shouldContainExactlyInAnyOrder done
    }

    @Test
    fun `return the full records joined from several Datastore queries`() {
        val now = currentTime()
        val first = project(DONE, dueDate = now.plusMinutes(1))
        val second = project(CANCELLED, dueDate = now.plusMinutes(2))
        val third = project(DONE, dueDate = now.plusMinutes(3))
        write(first, second, third, project(CREATED, dueDate = now))

        val query = queryBuilder()
            .either(statusIs(DONE), statusIs(CANCELLED))
            .sortAscendingBy(due_date)
            .limit(2)
            .buildWithMask()
        check(datastoreQueriesIn(query) == 2) {
            "The query must run as several Datastore queries to reach `SortAndLimit`."
        }

        readAll(query) shouldContainExactly listOf(first, second)
    }

    private fun queryBuilder(): ProjectQueryBuilder =
        RecordQuery.newBuilder(StgProjectId::class.java, StgProject::class.java)

    /**
     * Sets the field mask to the query and builds it.
     *
     * Asserts that the built query carries the mask, so that the tests cannot pass
     * vacuously once the query API stops passing field masks to storages.
     * This spec is obsolete then and should be deleted.
     */
    private fun ProjectQueryBuilder.buildWithMask(): ProjectQuery {
        val mask = idAndDueDate()
        val query = withMask(mask).build()
        check(query.mask() == mask) {
            "The query carries no field mask. This spec is obsolete; please delete it."
        }
        return query
    }

    /**
     * Tells how many Datastore queries the passed query is split into.
     */
    private fun datastoreQueriesIn(query: ProjectQuery): Int =
        DsFilters.fromPredicate(query.subject().predicate(), FilterAdapter.of(DsColumnMapping()))
            .size

    private fun statusIs(value: Status): Either<ProjectQueryBuilder> =
        Either { it.where(status).`is`(value.name) }

    private fun project(projectStatus: Status, dueDate: Timestamp = currentTime()): StgProject =
        newState(generateId(), projectStatus, dueDate)

    private fun write(vararg projects: StgProject): List<StgProject> {
        val records = projects.toList()
        storage.writeBatch(records)
        return records
    }

    private fun readAll(query: ProjectQuery): List<StgProject> =
        storage.readAll(query).asSequence().toList()
}

private fun Timestamp.plusMinutes(minutes: Long): Timestamp =
    Timestamps.add(this, Durations.fromMinutes(minutes))
