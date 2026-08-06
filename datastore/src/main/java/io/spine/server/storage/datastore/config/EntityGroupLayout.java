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
import com.google.cloud.datastore.PathElement;
import com.google.cloud.datastore.StructuredQuery;
import com.google.protobuf.Message;
import io.spine.annotation.SPI;
import io.spine.query.RecordQuery;
import io.spine.server.storage.datastore.DatastoreMedium;
import io.spine.server.storage.datastore.Kind;
import io.spine.server.storage.datastore.record.RecordId;
import io.spine.string.Stringifiers;

import java.util.Optional;

import static com.google.cloud.datastore.StructuredQuery.PropertyFilter.hasAncestor;

/**
 * Describes the structure of Datastore Entity groups in which the {@link Message} records
 * served by a certain {@link io.spine.server.storage.RecordStorage RecordStorage} are persisted.
 *
 * <p>By extending this type, the descendants describe how to obtain an ancestor key for a certain
 * stored record and how to query the records with regards to their ancestor-child structure.
 *
 * @param <I>
 *         the type of the identifiers of the stored records
 * @param <R>
 *         the type of the stored records
 * @param <P>
 *         the type of the "parent" records
 */
@SPI
public abstract class EntityGroupLayout<I, R extends Message, P extends Message>
        extends RecordLayout<I, R> {

    private final Kind parentKind;

    /**
     * Creates the layout for the storage, in which records of passed type stored as children
     * of records of the passed parent type.
     *
     * @param recordType
     *         the type of stored records
     * @param parentType
     *         the type of parent records for the stored records
     */
    protected EntityGroupLayout(Class<R> recordType, Class<P> parentType) {
        super(recordType);
        parentKind = Kind.of(parentType);
    }

    /**
     * Creates the layout for the storage keeping its records under the given
     * Datastore Entity {@code Kind}, as children of records of the passed parent type.
     *
     * <p>Serves the storages whose kind is not derived from the record type alone —
     * such as the per-entity histories; see {@link
     * io.spine.server.storage.datastore.DatastoreStorageFactory.Builder#organizeRecords(Class,
     * Class, RecordLayout)
     * DatastoreStorageFactory.Builder.organizeRecords(Class, Class, RecordLayout)}.
     *
     * @param kind
     *         the kind of the stored records
     * @param parentType
     *         the type of parent records for the stored records
     */
    protected EntityGroupLayout(Kind kind, Class<P> parentType) {
        super(kind);
        parentKind = Kind.of(parentType);
    }

    /**
     * Creates a new Datastore {@code Key} for the passed identifier.
     *
     * <p>The returned {@code Key} value includes the reference to the record ancestor via
     * the result of {@link #toAncestorRecordId(Object) toAncestorRecordId(id)}.
     *
     * @param id
     *         the identifier to create a {@code Key} from
     * @param datastore
     *         Datastore connector
     * @return a new {@code Key} instance
     */
    @Override
    public final Key keyOf(I id, DatastoreMedium datastore) {
        var parentRecordId = toAncestorRecordId(id);
        var ancestor = PathElement.of(parentKind.value(), parentRecordId.value());
        var result = datastore.keyFactory(recordKind())
                              .addAncestor(ancestor)
                              .newKey(Stringifiers.toString(id));
        return result;
    }

    /**
     * Determines the {@code RecordId} of ancestor for the record identified by the passed ID value.
     *
     * @param id
     *         the record which ancestor {@code RecordId} is to be determined
     * @return a new instance of {@code RecordId} for the ancestor
     */
    protected abstract RecordId toAncestorRecordId(I id);

    /**
     * Analyzes the passed record query and extracts the value of the ancestor identifier from
     * the query filters.
     *
     * <p>The extracted value is required to set up the Datastore ancestor filtering.
     *
     * @param query
     *         the record query to extract the ID of ancestor
     * @return an identifier of the ancestor, wrapped into a {@code RecordId}
     */
    protected abstract RecordId extractAncestorId(RecordQuery<I, R> query);

    @Override
    public Optional<StructuredQuery.Filter> ancestorFilter(RecordQuery<I, R> query,
                                                           DatastoreMedium datastore) {
        var parentId = extractAncestorId(query);
        var parentKey = datastore.keyFor(parentKind, parentId);
        var ancestorFilter = hasAncestor(parentKey);
        return Optional.of(ancestorFilter);
    }
}
