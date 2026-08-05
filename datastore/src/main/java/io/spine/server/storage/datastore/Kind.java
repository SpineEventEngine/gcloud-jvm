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

package io.spine.server.storage.datastore;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Message;
import io.spine.annotation.Internal;
import io.spine.server.storage.StorageGroup;
import io.spine.type.TypeName;
import io.spine.type.TypeUrl;
import io.spine.value.StringTypeValue;

import java.io.Serial;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.util.Preconditions2.checkNotEmptyOrBlank;

/**
 * A data transfer object representing a Datastore
 * <a href="https://cloud.google.com/datastore/docs/concepts/entities#kinds_and_identifiers">kind</a>.
 */
public final class Kind extends StringTypeValue {

    @Serial
    private static final long serialVersionUID = 0L;

    private static final String INVALID_KIND_ERROR_MESSAGE =
            "Datastore kind cannot start with \"__\". See " +
                    "https://cloud.google.com/datastore/docs/concepts/entities#kinds_and_identifiers" +
                    " for more info.";
    private static final String FORBIDDEN_PREFIX = "__";

    private static final String NAMESPACE_KIND = "__namespace__";

    private Kind(String value) {
        super(checkValidKind(value));
    }

    /**
     * Creates a new instance of {@code Kind} representing an ancillary Datastore kind.
     *
     * @param value
     *         the name of the {@code Kind}
     * @param ancillary
     *         the flag showing that the {@code Kind} is ancillary; must be set to {@code true}
     */
    private Kind(String value, boolean ancillary) {
        super(value);
        checkArgument(ancillary);
    }

    /**
     * Creates a new {@code Kind} using the passed {@code String}
     * as the {@code Kind}'s name.
     */
    public static Kind of(String value) {
        checkNotEmptyOrBlank(value);
        return new Kind(value);
    }

    /**
     * Creates a new {@code Kind} using the {@code TypeName} form of
     * the passed {@code TypeUrl} as the {@code Kind}'s name.
     */
    public static Kind of(TypeUrl typeUrl) {
        checkNotNull(typeUrl);
        return new Kind(typeUrl.typeName()
                               .value());
    }

    /**
     * Creates a new {@code Kind} with the name equal to the type name
     * corresponding to the passed {@code Message} type.
     */
    public static Kind of(Class<? extends Message> recordType) {
        checkNotNull(recordType);
        var typeUrl = TypeUrl.of(recordType);
        var result = of(typeUrl);
        return result;
    }

    /**
     * Creates a new {@code Kind} for the records of the given type stored on behalf
     * of the given {@linkplain StorageGroup storage group}.
     *
     * <p>The name is composed of the group name and the simple name of the record type,
     * e.g. {@code spine.test.storage.StgProject_Event}. Such a kind tells a grouped
     * storage — a per-entity history — apart from the ungrouped storage of records
     * of the same type, and from the same-typed histories of other entity classes.
     */
    public static Kind of(Class<? extends Message> recordType, StorageGroup group) {
        checkNotNull(recordType);
        checkNotNull(group);
        var name = group.getName() + '_' + recordType.getSimpleName();
        return new Kind(name);
    }

    /**
     * Creates a new {@code Kind} with the name equal to the full name
     * of the passed {@code Descriptor}.
     */
    public static Kind of(Descriptor descriptor) {
        checkNotNull(descriptor);
        return new Kind(descriptor.getFullName());
    }

    /**
     * Creates a new {@code Kind} with the name equal to the type name
     * of the passed {@code Message} instance.
     */
    public static Kind of(Message record) {
        checkNotNull(record);
        return of(record.getDescriptorForType());
    }

    /**
     * Creates a new {@code Kind} naming it after the passed {@code TypeName}.
     */
    public static Kind of(TypeName typeName) {
        checkNotNull(typeName);
        return new Kind(typeName.value());
    }

    /**
     * Produces a {@code Kind} representing the Datastore namespace kind.
     */
    @Internal
    public static Kind ofNamespace() {
        return new Kind(NAMESPACE_KIND, true);
    }

    private static String checkValidKind(String kind) {
        checkNotNull(kind);
        checkArgument(!kind.startsWith(FORBIDDEN_PREFIX), INVALID_KIND_ERROR_MESSAGE);
        return kind;
    }
}
