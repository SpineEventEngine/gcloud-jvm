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
import com.google.protobuf.Message;
import io.spine.server.storage.datastore.record.Entities;
import io.spine.type.TypeUrl;

import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableList.toImmutableList;

/**
 * Filters the contents of {@link IntermediateResult} and converts them
 * to the {@code R}-typed records.
 *
 * @param <R>
 *         the type of records
 */
abstract class ToRecords<R extends Message> implements Function<IntermediateResult, Iterable<R>> {

    private final TypeUrl recordType;

    /**
     * Creates an instance of this function.
     *
     * @param type
     *         a type URL of records to convert
     */
    ToRecords(TypeUrl type) {
        recordType = type;
    }

    @Override
    public Iterable<R> apply(IntermediateResult result) {
        var entities = result.entities();
        var stream = entities.stream().filter(Objects::nonNull);
        stream = filter(stream);
        @SuppressWarnings("ConstantConditions") /* `null` were already filtered out. */
        var records =
                stream.map(this::toRecord)
                      .collect(toImmutableList());
        return records;
    }

    private R toRecord(Entity e) {
        return Entities.toMessage(e, recordType);
    }

    /**
     * Filters the entities.
     *
     * <p>Descendant types are expected to provide their own rules for filtering.
     */
    protected abstract Stream<Entity> filter(Stream<Entity> entities);
}
