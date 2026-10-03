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
import com.google.common.collect.ImmutableList;
import com.google.protobuf.Message;
import io.spine.query.SortBy;
import io.spine.type.TypeUrl;
import org.jspecify.annotations.Nullable;

import java.util.stream.Stream;

import static io.spine.server.storage.datastore.record.DsEntityComparator.implementing;

/**
 * Sorts and limits the original list of {@code Entity} objects, then converts each of them
 * to the {@code <R>}-typed records.
 *
 * @param <R>
 *         the type of the records to convert each {@code Entity} into
 */
final class SortAndLimit<R extends Message> extends ToRecords<R> {

    /**
     * A limit value which isn't set.
     */
    private static final int UNSET_LIMIT = 0;

    private final ImmutableList<SortBy<?, R>> sorting;
    private final @Nullable Integer limit;

    /**
     * Creates a new instance of this conversion function.
     *
     * @param type
     *         the type of converted records
     * @param sorting
     *         the directives to use for sorting
     * @param limit
     *         if set, a maximum number of records to pass on, in the ascending order of sorting
     */
    SortAndLimit(TypeUrl type, ImmutableList<SortBy<?, R>> sorting, @Nullable Integer limit) {
        super(type);
        this.sorting = sorting;
        this.limit = limit;
    }

    @Override
    protected Stream<Entity> filter(Stream<Entity> entities) {
        var currentStream = entities;
        if (!sorting.isEmpty()) {
            currentStream = currentStream.sorted(implementing(sorting));
        }
        if (limit != null && limit != UNSET_LIMIT) {
            currentStream = currentStream.limit(limit);
        }
        return currentStream;
    }
}
