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

package io.spine.server.storage.datastore.record;

import com.google.cloud.Timestamp;
import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.Value;
import com.google.cloud.datastore.ValueType;
import com.google.protobuf.Message;
import io.spine.client.OrderBy;
import io.spine.query.Direction;
import io.spine.query.SortBy;
import org.jspecify.annotations.Nullable;

import java.io.Serial;
import java.io.Serializable;
import java.util.Comparator;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.util.Exceptions.newIllegalStateException;
import static java.util.Arrays.stream;
import static java.util.Objects.requireNonNull;

/**
 * A comparator for {@linkplain Entity Datastore entities} by attributes corresponding to
 * {@linkplain io.spine.query.Column record columns}.
 *
 * <p>Comparator instances are supplied column and direction using
 * {@link io.spine.query.SortBy sorting} clause.
 */
public class DsEntityComparator implements Comparator<Entity>, Serializable {

    @Serial
    private static final long serialVersionUID = 0L;
    private final String column;

    private DsEntityComparator(String column) {
        this.column = column;
    }

    @SuppressWarnings("unchecked")  /* Fwk ensures the instances are comparable. */
    @Override
    public int compare(Entity a, Entity b) {
        checkNotNull(a);
        checkNotNull(b);

        var aValue = ComparableValueExtractor.comparable(a.getValue(column));
        var bValue = ComparableValueExtractor.comparable(b.getValue(column));

        if (aValue == null) {
            return -1;
        }
        if (bValue == null) {
            return +1;
        }

        return aValue.compareTo(bValue);
    }

    /**
     * Creates an entity comparator instance which implements the provided {@link OrderBy} clause.
     */
    public static <R extends Message>
    Comparator<Entity> implementing(Iterable<SortBy<?, R>> sorting) {
        checkNotNull(sorting);

        Comparator<Entity> result = null;
        for (SortBy<?, ?> sortBy : sorting) {
            Comparator<Entity> thisComparator;
            var direction = sortBy.direction();
            var columnName = columnPathOf(sortBy);
            var template = new DsEntityComparator(columnName);
            thisComparator = direction == Direction.ASC
                             ? template
                             : template.reversed();
            result = result == null
                     ? thisComparator
                     : result.thenComparing(thisComparator);

        }
        requireNonNull(result, "Cannot create sorting comparator for zero sorting directives.");
        return result;
    }

    private static String columnPathOf(SortBy<?, ?> directive) {
        return directive
                .column()
                .name()
                .value();
    }

    /**
     * An extractor of comparable values from Datastore {@link Value Value}.
     *
     * <p>Only {@link ValueType#NULL NULL}, {@link ValueType#STRING STRING},
     * {@link ValueType#LONG LONG}, {@link ValueType#DOUBLE DOUBLE},
     * {@link ValueType#BOOLEAN BOOLEAN}, and {@link ValueType#TIMESTAMP TIMESTAMP} column types
     * support comparison, thus ordering.
     *
     * <p>The {@link ValueType#ENTITY ENTITY}, {@link ValueType#LIST LIST},
     * {@link ValueType#RAW_VALUE RAW_VALUE}, {@link ValueType#LAT_LNG LAT_LNG},
     * and {@link ValueType#KEY KEY} types are not supported.
     */
    @SuppressWarnings("rawtypes")       /* To avoid generics hell. */
    private enum ComparableValueExtractor {
        NULL(ValueType.NULL) {
            @Override
            @Nullable Comparable extract(Value<?> value) {
                return null;
            }
        },
        STRING(ValueType.STRING) {
            @Override
            Comparable extract(Value<?> value) {
                return (Comparable) value.get();
            }
        },
        LONG(ValueType.LONG) {
            @Override
            @Nullable Comparable extract(Value<?> value) {
                return (Long) value.get();
            }
        },
        DOUBLE(ValueType.DOUBLE) {
            @Override
            @Nullable Comparable extract(Value<?> value) {
                return (Double) value.get();
            }
        },
        BOOLEAN(ValueType.BOOLEAN) {
            @Override
            @Nullable Comparable extract(Value<?> value) {
                return (Boolean) value.get();
            }
        },
        TIMESTAMP(ValueType.TIMESTAMP) {
            @Override
            @SuppressWarnings("OverlyStrongTypeCast")   /* Set the type explicitly. */
            @Nullable Comparable extract(Value<?> value) {
                return (Timestamp) value.get();
            }
        };

        private final ValueType valueType;

        ComparableValueExtractor(ValueType type) {
            this.valueType = type;
        }

        abstract @Nullable Comparable extract(Value<?> value);

        @SuppressWarnings("PMD.ReferenceEquality") // Enum value is checked.
        private boolean matches(ValueType type) {
            return type == valueType;
        }

        private static @Nullable Comparable comparable(Value<?> value) {
            var type = value.getType();
            var extractor = pickForType(type);
            return extractor.extract(value);
        }

        private static ComparableValueExtractor pickForType(ValueType type) {
            return stream(values())
                    .filter(extractType -> extractType.matches(type))
                    .findFirst()
                    .orElseThrow(unrecognized(type));
        }

        private static Supplier<IllegalStateException> unrecognized(ValueType type) {
            return () -> newIllegalStateException("Unrecognized Datastore type %s.", type);
        }
    }
}
