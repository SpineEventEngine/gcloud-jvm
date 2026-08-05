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

package io.spine.server.storage.datastore.config;

import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.StructuredQuery;
import com.google.protobuf.Message;
import io.spine.annotation.SPI;
import io.spine.query.RecordQuery;
import io.spine.server.storage.datastore.DatastoreMedium;
import io.spine.server.storage.datastore.Kind;
import io.spine.server.storage.datastore.record.RecordId;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Describes the layout of Datastore Entities in which the {@link Message} records of certain
 * type are stored.
 *
 * <p>Datastore allows to store Entities in flat structure or by grouping the Entities into
 * Entity groups according to ancestor-child relations.
 *
 * <p>Descendants may define their own layout for records and then plug it into
 * their storage factory via
 * {@link io.spine.server.storage.datastore.DatastoreStorageFactory.Builder#organizeRecords(Class,
 * RecordLayout) DatastoreStorageFactory.newBuilder().organizeRecords(typeOfRecord, recordLayout)}.
 *
 * @param <I>
 *         the type of identifiers of the stored records
 * @param <R>
 *         the type of the stored records
 */
@SPI
public abstract class RecordLayout<I, R extends Message> {

    private final Kind kind;

    /**
     * Creates the layout for the records of the given type.
     */
    protected RecordLayout(Class<? extends Message> domainType) {
        this(Kind.of(domainType));
    }

    /**
     * Creates the layout with the given Datastore Entity {@code Kind}.
     *
     * <p>Serves the storages whose kind is not derived from the record type alone —
     * such as the per-entity histories, whose kind also carries their
     * {@linkplain io.spine.server.storage.StorageGroup storage group}.
     */
    protected RecordLayout(Kind kind) {
        this.kind = checkNotNull(kind);
    }

    /**
     * Returns the Datastore Entity {@code Kind} for the stored records.
     */
    public final Kind recordKind() {
        return kind;
    }

    /**
     * Creates a new Datastore {@code Key} from the passed record identifier.
     *
     * @param id
     *         the record identifier to create a {@code Key} from
     * @param datastore
     *         Datastore connector
     * @return a new {@code Key} instance
     */
    protected Key newKey(RecordId id, DatastoreMedium datastore) {
        var key = datastore.keyFor(recordKind(), id);
        return key;
    }

    /**
     * Wraps the given identifier into a new {@code RecordId}.
     */
    protected abstract RecordId asRecordId(I id);

    /**
     * Creates a new Datastore {@code Key} for the passed identifier.
     *
     * @param id
     *         the identifier to create a {@code Key} from
     * @param datastore
     *         Datastore connector
     * @return a new instance of Datastore {@code Key}
     */
    public abstract Key keyOf(I id, DatastoreMedium datastore);

    /**
     * If applicable, creates a Datastore ancestor filter by analyzing the passed record query.
     *
     * <p>Such a special filtering is required by Datastore, so that it could efficiently
     * fetch the records as children of some ancestor. Descendants of {@code RecordLayout}
     * must supply such a Datastore-specific filtering criterion in case their storage layout
     * is hierarchical.
     *
     * <p>If the structure of storage is flat (i.e. no ancestor-child relations are established),
     * this method returns {@code Optional.empty()}.
     *
     * @param query
     *         record query to create an ancestor filter from
     * @param datastore
     *         Datastore connector
     * @return a new Datastore ancestor filter,
     *         or {@code Optional.empty()} if such an organizational is not applicable
     *         for the current type of {@code RecordLayout}
     */
    public abstract Optional<StructuredQuery.Filter> ancestorFilter(RecordQuery<I, R> query,
                                                                    DatastoreMedium datastore);
}
