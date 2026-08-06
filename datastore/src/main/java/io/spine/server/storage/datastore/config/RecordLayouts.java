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

import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.Message;
import io.spine.annotation.Internal;
import io.spine.base.EntityState;
import io.spine.server.storage.StorageGroup;
import io.spine.server.storage.datastore.Kind;
import io.spine.type.TypeName;

import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.util.Exceptions.newIllegalArgumentException;

/**
 * The record layouts of storage implementations available
 * to the {@linkplain io.spine.server.storage.datastore.DatastoreStorageFactory Datastore
 * storage factory}.
 *
 * <p>This type is internal. The library users are able to supply their custom layouts
 * for stored records via
 * {@link io.spine.server.storage.datastore.DatastoreStorageFactory.Builder#organizeRecords(Class,
 * RecordLayout)
 * DatastoreStorageFactory.newBuilder().organizeRecords(...)}, and for the records of
 * {@linkplain StorageGroup grouped} storages via
 * {@link io.spine.server.storage.datastore.DatastoreStorageFactory.Builder#organizeRecords(Class,
 * Class, RecordLayout)
 * the three-argument flavor of the same method}.
 */
@Internal
public final class RecordLayouts
        extends Settings<RecordLayout<?, ?>, RecordLayouts, RecordLayouts.Builder> {

    /**
     * The layouts of the {@linkplain StorageGroup grouped} storages,
     * per the identity of a grouped storage.
     */
    private final ImmutableMap<GroupedStorage, RecordLayout<?, ?>> groupedValues;

    private RecordLayouts(Builder builder) {
        super(builder);
        groupedValues = ImmutableMap.copyOf(builder.groupedValues);
    }

    /**
     * Creates a new {@code Builder} for this type.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Obtains the record layout for the storage of the records of the specified type.
     *
     * <p>If a custom layout was not provided by the library user,
     * {@linkplain FlatLayout a flat layout} is used.
     *
     * @param domainType
     *         the type of records
     * @param <I>
     *         the type of record identifiers
     * @param <R>
     *         the type of records, as a bounding generic parameter
     * @return the record layout for the storage
     * @throws IllegalArgumentException
     *         if the record layout of wrong type was supplied on the configuration stage
     * @implNote This method relies on the fact that the layout registered for the
     *         storage of {@code <R>}-typed records with {@code <I>}-typed identifiers has the
     *         corresponding generic parameters. Therefore, a plain class-cast is performed
     *         when obtaining. If that fails, the exception is thrown.
     */
    public <I, R extends Message> RecordLayout<I, R> find(Class<? extends Message> domainType) {
        var optional = findValue(domainType);
        if (optional.isEmpty()) {
            return new FlatLayout<>(domainType);
        }
        var raw = optional.get();
        RecordLayout<I, R> result = cast(raw, domainType);
        return result;
    }

    /**
     * Obtains the record layout for the {@linkplain StorageGroup grouped} storage
     * of the records of the specified type, created on behalf of the given group.
     *
     * <p>If a custom layout was not
     * {@linkplain Builder#add(Class, Class, RecordLayout) registered} for the state
     * and the record types of the storage, {@linkplain FlatLayout a flat layout} under
     * the {@linkplain Kind#of(Class, StorageGroup) grouped kind} is used.
     *
     * @param recordType
     *         the type of records
     * @param group
     *         the group the storage belongs to
     * @param <I>
     *         the type of record identifiers
     * @param <R>
     *         the type of records, as a bounding generic parameter
     * @return the record layout for the storage
     * @throws IllegalArgumentException
     *         if the record layout of wrong type was supplied on the configuration stage
     * @implNote See the {@linkplain #find(Class) single-type flavor} on the class-cast
     *         performed here.
     */
    public <I, R extends Message> RecordLayout<I, R> find(Class<R> recordType,
                                                          StorageGroup group) {
        checkNotNull(recordType);
        checkNotNull(group);
        var raw = groupedValues.get(new GroupedStorage(group, recordType));
        if (raw == null) {
            return new FlatLayout<>(Kind.of(recordType, group));
        }
        RecordLayout<I, R> result = cast(raw, recordType);
        return result;
    }

    /**
     * Casts the passed record layout to the instance with types of identifiers and records bound
     * to the values of the contextual generic parameters.
     *
     * <p>This method throws an {@code IllegalArgumentException} in case the passed value
     * cannot be cast — as its actual generic parameters may differ from expected.
     *
     * @param raw
     *         the value to cast
     * @param domainType
     *         the type of records for which the layout is set
     * @param <I>
     *         the type of record identifiers
     * @param <R>
     *         the type of records
     * @return the cast value
     * @throws IllegalArgumentException
     *         in case the value cannot be cast
     * @implNote To simplify the implementation, the cast operation is performed in
     *         a straightforward manner, and all corresponding compiler warnings are suppressed.
     */
    @SuppressWarnings({"unchecked", "OverlyBroadCatchBlock"})
    private static <I, R extends Message>
    RecordLayout<I, R> cast(RecordLayout<?, ?> raw, Class<?> domainType) {
        RecordLayout<I, R> layout;
        try {
            layout = (RecordLayout<I, R>) raw;
        } catch (Exception e) {
            throw andExplainWhy(e, domainType);
        }
        return layout;
    }

    private static IllegalArgumentException andExplainWhy(Exception e, Class<?> domainType) {
        throw newIllegalArgumentException(
                e, "Error using the provided storage layout for type `%s`.", domainType);
    }

    /**
     * A builder for {@code RecordLayouts}.
     */
    public static final class Builder
            extends Settings.Builder<RecordLayout<?, ?>, RecordLayouts, Builder> {

        private final Map<GroupedStorage, RecordLayout<?, ?>> groupedValues = new HashMap<>();

        private Builder() {
            super();
        }

        @Override
        Builder self() {
            return this;
        }

        /**
         * Adds a layout for the {@linkplain StorageGroup grouped} storage serving
         * the entities with the specified state type and storing the records
         * of the specified type.
         *
         * <p>Each next layout added for the same state and record types overwrites
         * the previous one.
         *
         * @param stateType
         *         the type of the state of the entity served by the grouped storage
         * @param recordType
         *         the type of the records stored by the grouped storage
         * @param layout
         *         the layout to use
         * @param <R>
         *         the type of the stored records
         * @return this instance of {@code Builder}
         */
        @CanIgnoreReturnValue
        public <R extends Message> Builder add(Class<? extends EntityState<?>> stateType,
                                               Class<R> recordType,
                                               RecordLayout<?, R> layout) {
            checkNotNull(stateType);
            checkNotNull(recordType);
            checkNotNull(layout);
            groupedValues.put(GroupedStorage.of(stateType, recordType), layout);
            return this;
        }

        /**
         * Creates a new instance of {@code RecordLayouts} on top of this {@code Builder} instance.
         */
        @Override
        public RecordLayouts build() {
            return new RecordLayouts(this);
        }
    }

    /**
     * The identity of a grouped storage: the {@linkplain StorageGroup storage group}
     * paired with the type of the stored records.
     *
     * @param group
     *         the group the storage belongs to
     * @param recordType
     *         the type of the stored records
     */
    private record GroupedStorage(StorageGroup group, Class<? extends Message> recordType) {

        /**
         * Creates the identity of the grouped storage serving the entities with
         * the given state type — after which the framework names the storage group —
         * and storing the records of the given type.
         */
        private static GroupedStorage of(Class<? extends EntityState<?>> stateType,
                                         Class<? extends Message> recordType) {
            var group = new StorageGroup(TypeName.of(stateType).value());
            return new GroupedStorage(group, recordType);
        }
    }
}
