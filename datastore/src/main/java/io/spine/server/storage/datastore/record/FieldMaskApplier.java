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

package io.spine.server.storage.datastore.record;

import com.google.protobuf.FieldMask;
import com.google.protobuf.Message;

import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Provides a function that returns the passed records unchanged.
 *
 * <p>Formerly, the function applied the provided {@link FieldMask} to the Protobuf messages
 * stored in Datastore as records.
 *
 * @deprecated Field masks are no longer supported. The function created by
 *         {@link #recordMasker(FieldMask)} returns the passed records unchanged.
 *         Please remove the usages of this class.
 */
@Deprecated
public final class FieldMaskApplier {

    /**
     * Prevents the utility class instantiation.
     */
    private FieldMaskApplier() {
    }

    /**
     * Creates a {@code Function} that returns the passed record unchanged.
     *
     * @param fieldMask
     *         the ignored field mask
     * @param <R>
     *         the type of the records
     * @deprecated Field masks are no longer supported. The returned function does nothing
     *         and returns the passed record unchanged. Please remove the usages of this method.
     */
    @Deprecated
    public static <R extends Message> Function<R, R> recordMasker(FieldMask fieldMask) {
        checkNotNull(fieldMask);
        return Function.identity();
    }
}
