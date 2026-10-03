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

package io.spine.server.storage.datastore.query;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Message;
import io.spine.annotation.Internal;
import io.spine.query.QueryPredicate;
import io.spine.query.RecordQuery;
import io.spine.query.SortBy;
import io.spine.server.storage.datastore.record.DsEntitySpec;
import io.spine.type.TypeUrl;
import org.jspecify.annotations.Nullable;

/**
 * A {@link RecordQuery} prepared for optimal execution in terms of Datastore
 * {@link com.google.cloud.datastore.Query Query} language.
 *
 * <p>Due to Datastore limitations, some {@code RecordQuery} instances are processed partly
 * via the Datastore querying, and partly by filtering the intermediate results in memory.
 * This type analyzes the queries and determines the execution strategy.
 *
 * <p>The field mask of the {@code RecordQuery} is ignored, as field masks are no longer
 * supported. The records are returned as they are stored.
 *
 * @param <I>
 *         the type of identifiers of the queried records
 * @param <R>
 *         the type of queried records
 */
@Internal
public abstract class PreparedQuery<I, R extends Message> {

    private final RecordQuery<I, R> query;
    private final TypeUrl recordType;
    private final FilterAdapter columnAdapter;
    private final DsEntitySpec<I, R> spec;

    /**
     * Creates a new instance.
     *
     * @param query
     *         an original {@code RecordQuery} to execute
     * @param adapter
     *         an adapter for the values set by Spine-specific predicates
     *         to those applicable to Datastore-native Filters
     * @param spec
     *         a specification of an Entity
     */
    PreparedQuery(RecordQuery<I, R> query, FilterAdapter adapter, DsEntitySpec<I, R> spec) {
        this.query = query;
        this.recordType = TypeUrl.of(query.subject()
                                          .recordType());
        columnAdapter = adapter;
        this.spec = spec;
    }

    /**
     * Executes the query and returns the read result.
     */
    public final Iterable<R> execute() {
        var intermediateResult = fetchFromDatastore();
        var result = toRecords(intermediateResult);
        return result;
    }

    /**
     * Queries Datastore for the {@code RecordQuery} part which may be processed by Datastore means.
     *
     * <p>Returns an intermediate result to be processed further.
     */
    abstract IntermediateResult fetchFromDatastore();

    /**
     * Turns the intermediate results obtained from Datastore into the desired format of records.
     *
     * <p>Some complex {@code RecordQuery} instances may require additional in-memory processing
     * at this stage. Other queries typically only require the conversion of data format.
     */
    abstract Iterable<R> toRecords(IntermediateResult result);

    /**
     * Returns the original {@code RecordQuery}.
     */
    final RecordQuery<I, R> query() {
        return query;
    }

    /**
     * Returns the type URL of the queried records.
     */
    final TypeUrl recordType() {
        return recordType;
    }

    /**
     * Returns the sorting directives of the original {@code RecordQuery}.
     */
    final ImmutableList<SortBy<?, R>> sorting() {
        return query.sorting();
    }

    /**
     * Tells whether the original {@code RecordQuery} has sorting directives.
     */
    final boolean hasSorting() {
        return !sorting().isEmpty();
    }

    /**
     * Returns the predicate of the original {@code RecordQuery}.
     */
    final QueryPredicate<R> predicate() {
        return query.subject()
                    .predicate();
    }

    /**
     * Returns the identifiers which were specified by the original {@code RecordQuery}.
     */
    final ImmutableSet<I> identifiers() {
        return query.subject()
                    .id()
                    .values();
    }

    /**
     * Returns the limit set by the original {@code RecordQuery}.
     *
     * <p>Returns {@code null} if no limit was set.
     */
    final @Nullable Integer limit() {
        return query.limit();
    }

    /**
     * Returns the adapter from the {@code RecordQuery} predicates
     * to native Datastore {@code Filter}s.
     */
    final FilterAdapter columnAdapter() {
        return columnAdapter;
    }

    /**
     * Returns the specification of the Datastore Entity to use in querying.
     */
    final DsEntitySpec<I, R> spec() {
        return spec;
    }
}
