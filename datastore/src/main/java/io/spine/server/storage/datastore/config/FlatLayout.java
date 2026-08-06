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
import io.spine.query.RecordQuery;
import io.spine.server.storage.datastore.DatastoreMedium;
import io.spine.server.storage.datastore.Kind;
import io.spine.server.storage.datastore.record.RecordId;

import java.util.Optional;

/**
 * Describes the type of storage layout, in which the records are stored in a flat structure.
 *
 * @param <I>
 *         the type of the identifiers of the stored records
 * @param <R>
 *         the type of the stored records
 */
public final class FlatLayout<I, R extends Message> extends RecordLayout<I, R> {

    /**
     * Creates a new flat layout for the stored records of passed type.
     */
    public FlatLayout(Class<? extends Message> domainType) {
        super(domainType);
    }

    /**
     * Creates a new flat layout storing the records under the given
     * Datastore Entity {@code Kind}.
     */
    public FlatLayout(Kind kind) {
        super(kind);
    }

    @Override
    protected RecordId asRecordId(I id) {
        return RecordId.ofEntityId(id);
    }

    @Override
    public Key keyOf(I id, DatastoreMedium datastore) {
        var recordId = asRecordId(id);
        return newKey(recordId, datastore);
    }

    /**
     * Always returns {@code Optional.empty()}, since the layout of records
     * has no ancestor-child hierarchy.
     */
    @Override
    public Optional<StructuredQuery.Filter> ancestorFilter(RecordQuery<I, R> query,
                                                           DatastoreMedium datastore) {
        return Optional.empty();
    }
}
