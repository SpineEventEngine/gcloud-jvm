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
import com.google.cloud.datastore.Key;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.Message;
import io.spine.query.RecordQuery;
import io.spine.server.storage.datastore.DatastoreMedium;
import io.spine.server.storage.datastore.record.DsEntitySpec;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.Streams.stream;
import static io.spine.server.storage.datastore.record.DsEntityComparator.implementing;
import static io.spine.server.storage.datastore.record.Entities.toMessage;

/**
 * An {@code Entity} lookup in Google Datastore using {@code Entity} identifiers.
 *
 * @param <I>
 *         the type of identifiers of the searched records
 * @param <R>
 *         the type of searched records
 * @implNote Lookup is performed by reading all the entities with Datastore Keys matching
 *         the provided IDs first and then applying other query constraints in-memory.
 */
final class DsLookupByIds<I, R extends Message> extends PreparedQuery<I, R> {

    private final DatastoreMedium datastore;

    /**
     * Creates a lookup for a passed {@code RecordQuery}.
     *
     * @param datastore
     *         Datastore connector
     * @param query
     *         a query to create this lookup for
     * @param adapter
     *         an adapter of values of {@code RecordQuery} parameters to Datastore-native types
     * @param spec
     *         Entity specification of the queried records
     */
    DsLookupByIds(DatastoreMedium datastore,
                  RecordQuery<I, R> query,
                  FilterAdapter adapter,
                  DsEntitySpec<I, R> spec) {
        super(query, adapter, spec);
        this.datastore = datastore;
    }

    @Override
    IntermediateResult fetchFromDatastore() {
        var rawEntities = readList(identifiers());
        return new IntermediateResult(rawEntities);
    }

    @Override
    Iterable<R> toRecords(IntermediateResult intermediateResult) {
        var rawEntities = intermediateResult.entities();
        var predicate = columnPredicate();
        var stream = rawEntities
                .stream()
                .filter(Objects::nonNull)
                .filter(predicate);
        if (hasSorting()) {
            stream = stream.sorted(implementing(sorting()));
        }
        var recordStream = stream.map(this::toRecord);
        if (limit() != null && limit() > 0) {
            recordStream = recordStream.limit(limit());
        }
        var result = recordStream.collect(toImmutableList());
        return result;
    }

    private Predicate<Entity> columnPredicate() {
        if (predicate().isEmpty()) {
            return entity -> true;
        }
        var result = new ColumnPredicate<>(query().subject(), columnAdapter());
        return result;
    }

    private List<@Nullable Entity> readList(Iterable<I> ids) {
        var keys = toKeys(ids);
        var entities = datastore.lookup(keys);
        return entities;
    }

    private ImmutableList<Key> toKeys(Iterable<I> ids) {
        var keys = stream(ids)
                .map(id -> spec().keyOf(id, datastore))
                .collect(toImmutableList());
        return keys;
    }

    private R toRecord(Entity entity) {
        return toMessage(entity, recordType());
    }
}
