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

import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.StructuredQuery;
import com.google.cloud.datastore.StructuredQuery.CompositeFilter;
import com.google.cloud.datastore.StructuredQuery.Filter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import com.google.protobuf.Message;
import io.spine.query.QueryPredicate;
import io.spine.query.RecordQuery;
import io.spine.server.storage.datastore.DatastoreMedium;
import io.spine.server.storage.datastore.record.DsEntitySpec;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;

/**
 * An {@code Entity} lookup using {@linkplain QueryPredicate Spine query predicates}.
 *
 * @param <I>
 *         the type of identifiers of the searched records
 * @param <R>
 *         the type of searched records
 * @implNote Due to Datastore restrictions, execution of a single
 *         {@link io.spine.query.Query Query} may result into several Datastore reads.
 *         See {@link DsFilters} for details.
 */
final class DsLookupByQueries<I, R extends Message> extends PreparedQuery<I, R> {

    private final DatastoreMedium datastore;

    /**
     * An ancestor filter specific to the record layout according to which the queried records
     * are stored.
     *
     * <p>If the records are stored flat (i.e., no ancestor-child hierarchy is used)
     * this filter is {@code null}.
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private final Optional<Filter> ancestorFilter;

    /**
     * A converter from {@link Entity} to {@code <R>} instances.
     *
     * <p>Initialized upon fetching the entities from the Datastore.
     */
    private @MonotonicNonNull ToRecords<R> transformer = null;

    /**
     * Creates a new lookup for the passed {@code RecordQuery}.
     *
     * @param datastore
     *         Datastore connector
     * @param query
     *         a query to create this lookup for
     * @param columnAdapter
     *         an adapter of {@code RecordQuery} parameter values to Datastore-native types
     * @param spec
     *         Entity specification of the queried records
     */
    DsLookupByQueries(DatastoreMedium datastore,
                      RecordQuery<I, R> query,
                      FilterAdapter columnAdapter,
                      DsEntitySpec<I, R> spec) {
        super(query, columnAdapter, spec);
        this.datastore = datastore;
        this.ancestorFilter = ancestorFilter(query, datastore);
    }

    private Optional<Filter> ancestorFilter(RecordQuery<I, R> query, DatastoreMedium datastore) {
        var result = spec().layout()
                           .ancestorFilter(query, datastore);
        return result;
    }

    @Override
    IntermediateResult fetchFromDatastore() {
        var rawEntities = findByPredicates(query());
        return new IntermediateResult(rawEntities);
    }

    @Override
    Iterable<R> toRecords(IntermediateResult result) {
        checkNotNull(transformer,
                     "In-memory transformer `Datastore Entity`->`Stored record` isn't set.");
        var records = transformer.apply(result);
        return records;
    }

    private ImmutableList<Entity> findByPredicates(RecordQuery<?, R> query) {
        ImmutableList<Entity> results;
        var queries = split(query);
        if (queries.size() == 1) {
            results = runSingleQuery(queries.get(0));
            transformer = new ConvertAsIs<>(recordType());
        } else {
            results = readAndJoin(queries);
            transformer = new SortAndLimit<>(recordType(), sorting(), limit());
        }

        return results;
    }

    private List<StructuredQuery<Entity>> split(RecordQuery<?, R> query) {
        var rootPredicate = query.subject()
                                 .predicate();
        var kind = spec().kind();
        if (rootPredicate.isEmpty()) {
            var result = new QueryWithFilter(query, kind).withNoFilter();
            return ImmutableList.of(result);
        }

        List<StructuredQuery<Entity>> queries = toDatastoreFilters(rootPredicate)
                .stream()
                .map(new QueryWithFilter(query, kind))
                .collect(toImmutableList());
        return queries;
    }

    private Collection<Filter> toDatastoreFilters(QueryPredicate<R> rootPredicate) {
        var filters = DsFilters.fromPredicate(rootPredicate, columnAdapter());
        return filters;
    }

    private ImmutableList<Entity> runSingleQuery(StructuredQuery<Entity> query) {
        var adjustedForLayout = adjustForLayout(query);
        var iterator = datastore.read(adjustedForLayout);
        var result = ImmutableList.copyOf(iterator);
        return result;
    }

    /**
     * Appends the Datastore's native ancestor filter, if the queried records are stored
     * in ancestor-child hierarchy.
     */
    private StructuredQuery<Entity> adjustForLayout(StructuredQuery<Entity> query) {
        if (ancestorFilter.isEmpty()) {
            return query;
        }
        var filter = query.getFilter();
        var filterWithNesting = CompositeFilter.and(filter, ancestorFilter.get());

        var result = query.toBuilder()
                .setFilter(filterWithNesting)
                .build();
        return result;
    }

    /**
     * Runs multiple Datastore queries in series.
     *
     * <p>Joins the results of each query into a single {@code ImmutableList}. Duplicate entities
     * are filtered out.
     *
     * <p>Each query is run without the {@code limit} set.
     */
    private ImmutableList<Entity> readAndJoin(Collection<StructuredQuery<Entity>> queries) {
        @SuppressWarnings("UnstableApiUsage")   /* Relying onto Guava's API. */
        var entities =
                queries.stream()
                        .map(DsLookupByQueries::clearLimit)
                        .map(this::adjustForLayout)
                        .map(datastore::read)
                        .flatMap(Streams::stream)
                        .distinct()
                        .collect(toImmutableList());
        return entities;
    }

    private static <R> StructuredQuery<R> clearLimit(StructuredQuery<R> q) {
        return q.toBuilder()
                .setLimit(Integer.MAX_VALUE)
                .build();
    }
}
