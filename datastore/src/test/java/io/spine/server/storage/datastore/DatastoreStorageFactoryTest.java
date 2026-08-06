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

import com.google.cloud.NoCredentials;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.DatastoreOptions;
import com.google.cloud.datastore.Key;
import com.google.common.testing.NullPointerTester;
import io.spine.base.Identifier;
import io.spine.core.TenantId;
import io.spine.server.BoundedContext;
import io.spine.server.ContextSpec;
import io.spine.server.entity.storage.SpecScanner;
import io.spine.server.storage.RecordSpec;
import io.spine.server.storage.RecordStorage;
import io.spine.server.storage.StorageFactory;
import io.spine.server.storage.datastore.given.DatastoreStorageFactoryTestEnv.CollegeProjection;
import io.spine.server.storage.datastore.given.DatastoreStorageFactoryTestEnv.StgProjectAggregate;
import io.spine.server.storage.datastore.given.TestEnvironment;
import io.spine.server.storage.datastore.record.DsRecordStorage;
import io.spine.server.storage.datastore.record.RecordId;
import io.spine.server.storage.datastore.tenant.TestNamespaceIndex;
import io.spine.server.storage.memory.InMemoryStorageFactory;
import io.spine.test.storage.StgProject;
import io.spine.test.storage.StgProjectId;
import io.spine.test.storage.StgTask;
import io.spine.testing.server.storage.datastore.EmulatorTest;
import io.spine.testing.server.storage.datastore.TestDatastoreStorageFactory;
import io.spine.testing.server.storage.datastore.TestDatastores;
import io.spine.type.TypeName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.server.ContextSpec.multitenant;
import static io.spine.server.storage.datastore.given.DatastoreStorageFactoryTestEnv.factoryFor;
import static io.spine.server.storage.datastore.given.TestEnvironment.singleTenantSpec;
import static io.spine.server.storage.datastore.given.TestRecordSpec.taskSpec;
import static io.spine.server.storage.given.GivenStorageProject.messageSpec;
import static io.spine.server.tenant.TenantAwareRunner.with;
import static io.spine.testing.DisplayNames.NOT_ACCEPT_NULLS;
import static io.spine.testing.server.storage.datastore.TestDatastores.defaultLocalProjectId;
import static io.spine.testing.server.storage.datastore.TestDatastores.local;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("`DatastoreStorageFactory` should")
@EmulatorTest
final class DatastoreStorageFactoryTest {

    private static final DatastoreOptions DUMMY_OPTIONS =
            DatastoreOptions.newBuilder()
                    .setProjectId("dummy-dataset")
                    .setCredentials(NoCredentials.getInstance())
                    .build();

    private static final Datastore datastore = DUMMY_OPTIONS.getService();

    private static final DatastoreStorageFactory factory =
            factoryFor(datastore);

    @Test
    @DisplayName(NOT_ACCEPT_NULLS)
    void testNulls() {
        new NullPointerTester().setDefault(ContextSpec.class, singleTenantSpec())
                               .setDefault(RecordSpec.class, messageSpec())
                               .testAllPublicInstanceMethods(factory);
    }

    @Test
    @DisplayName("create multitenant storages")
    void testCreateMultitenant() {
        StorageFactory factory = factoryFor(datastore);
        RecordStorage<?, ?> storage =
                factory.createRecordStorage(TestEnvironment.multiTenantSpec(), StgProjectAggregate.spec());
        assertTrue(storage.isMultitenant());
        storage.close();
    }

    @Test
    @DisplayName("create separate record storage per state type")
    void testDependsOnStateType() {
        var contextSpec = singleTenantSpec();
        var storage = (DsRecordStorage<?, ?>)
                factory.createRecordStorage(contextSpec, StgProjectAggregate.spec());
        assertNotNull(storage);
        var differentStorage = (DsRecordStorage<?, ?>)
                factory.createRecordStorage(contextSpec, SpecScanner.scan(CollegeProjection.class));
        assertNotNull(differentStorage);
        assertNotEquals(storage.kind(), differentStorage.kind());
    }

    @Test
    @DisplayName("have default column mapping")
    void testDefaultColumnMapping() {
        var factory = factoryFor(datastore);
        var mapping = factory.columnMapping();
        assertNotNull(mapping);
    }

    @Test
    @DisplayName("do nothing on close")
    void testClose() {
        var factory = factoryFor(datastore);
        factory.close();
        // Multiple calls are allowed as no action is performed
        factory.close();
    }

    @Test
    @DisplayName("allow custom namespaces for multitenant storages")
    void namespaceForMultitenant() {
        var tenant = TenantId.newBuilder()
                .setValue("my-company")
                .build();
        var namespace = "Vnon-null-or-empty-namespace";
        var options = local().getOptions()
                .toBuilder()
                .setNamespace(namespace)
                .build();
        var spec = multitenant(testName());
        var datastore = options.getService();
        var factory = factoryFor(datastore);
        var storage =
                factory.createRecordStorage(spec, messageSpec());
        var key = writeForTenant(storage, tenant)
                .setNamespace(namespace + ".V" + tenant.getValue())
                .build();
        var entity = datastore.get(key);
        assertThat(entity).isNotNull();
    }

    @Test
    @DisplayName("allow no custom namespaces for multitenant storages")
    void testDatastoreNamespaceInOptions() {
        var tenant = TenantId.newBuilder()
                .setValue("your-company")
                .build();
        var spec = multitenant(testName());
        var datastore = local();
        var factory = factoryFor(datastore);
        var storage =
                factory.createRecordStorage(spec, messageSpec());
        var key = writeForTenant(storage, tenant)
                .setNamespace('V' + tenant.getValue())
                .build();
        var entity = datastore.get(key);
        assertThat(entity).isNotNull();
    }

    @Test
    @DisplayName("configure `BoundedContextBuilder` with the `TenantIndex`")
    void testProduceBcBuilder() {
        DatastoreStorageFactory factory = TestDatastoreStorageFactory.local();
        var builder = BoundedContext.multitenant(testName());
        assertThat(builder.hasTenantIndex())
              .isFalse();
        var updatedIndex = factory.configureTenantIndex(builder)
                                  .getTenantIndex();
        assertThat(updatedIndex)
              .isNotNull();
        assertThat(updatedIndex).isInstanceOf(TestNamespaceIndex.getType());
    }

    @Nested
    @DisplayName("allow customizing a storage")
    class CustomStorage {

        @Test
        @DisplayName("for plain Protobuf `Message` records")
        void plainRecord() {
            var spec = ContextSpec.singleTenant(testName());
            var customStorage =
                    InMemoryStorageFactory.newInstance()
                                          .createRecordStorage(spec, messageSpec());
            var factory = DatastoreStorageFactory.newBuilder()
                    .setDatastore(TestDatastores.local())
                    .useRecordStorage(StgProjectId.class, StgProject.class,
                                      configuration -> customStorage)
                    .build();
            var actualStorage =
                    factory.createRecordStorage(spec, messageSpec());
            assertThat(actualStorage).isEqualTo(customStorage);
        }

        @Test
        @DisplayName("for an Entity state")
        void entity() {
            var spec = ContextSpec.singleTenant(testName());
            var customEntityStorage =
                    InMemoryStorageFactory.newInstance()
                                          .createRecordStorage(spec, taskSpec());
            var factory = DatastoreStorageFactory.newBuilder()
                    .setDatastore(TestDatastores.local())
                    .useEntityStorage(StgTask.class,
                                      configuration -> customEntityStorage)
                    .build();
            var actualStorage =
                    factory.createRecordStorage(spec, taskSpec());
            assertThat(actualStorage).isEqualTo(customEntityStorage);
        }
    }

    private static String testName() {
        return DatastoreStorageFactoryBuilderTest.class.getSimpleName();
    }

    private static Key.Builder writeForTenant(RecordStorage<StgProjectId, StgProject> storage,
                                              TenantId tenant) {
        var id = StgProjectId.newBuilder()
                .setId(Identifier.newUuid())
                .build();
        var project = StgProject.newBuilder()
                .setId(id)
                .setName("Sample storage project")
                .build();
        with(tenant).run(
                () -> storage.write(id, project)
        );
        var recordId = RecordId.ofEntityId(id);
        return Key.newBuilder(defaultLocalProjectId().value(),
                              TypeName.of(project)
                                      .value(),
                              recordId.value());
    }
}
