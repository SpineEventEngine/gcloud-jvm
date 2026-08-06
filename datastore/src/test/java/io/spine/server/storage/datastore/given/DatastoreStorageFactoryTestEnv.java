/*
 * Copyright 2023, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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

package io.spine.server.storage.datastore.given;

import com.google.cloud.datastore.Datastore;
import io.spine.server.aggregate.Aggregate;
import io.spine.server.entity.EntityRecord;
import io.spine.server.entity.storage.SpecScanner;
import io.spine.server.projection.Projection;
import io.spine.server.storage.RecordSpec;
import io.spine.server.storage.datastore.DatastoreStorageFactory;
import io.spine.test.datastore.College;
import io.spine.test.datastore.CollegeId;
import io.spine.test.storage.StgProject;
import io.spine.test.storage.StgProjectId;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Test environment for {@link io.spine.server.storage.datastore.DatastoreStorageFactoryTest}.
 */
public final class DatastoreStorageFactoryTestEnv {

    /**
     * Prevents this test environment from instantiating.
     */
    private DatastoreStorageFactoryTestEnv() {
    }

    /**
     * Creates a new {@code DatastoreStorageFactory} with its default settings by wrapping the given
     * {@code Datastore} instance.
     */
    public static DatastoreStorageFactory factoryFor(Datastore datastore) {
        checkNotNull(datastore);
        return DatastoreStorageFactory.newBuilder()
                .setDatastore(datastore)
                .build();
    }

    public static class StgProjectAggregate
            extends Aggregate<StgProjectId, StgProject, StgProject.Builder> {

        public static RecordSpec<StgProjectId, EntityRecord> spec() {
            return SpecScanner.scan(StgProjectAggregate.class);
        }
    }

    public static class CollegeProjection
            extends Projection<CollegeId, College, College.Builder> {
    }
}
