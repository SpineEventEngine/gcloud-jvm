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

package io.spine.server.storage.datastore;

import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.StructuredQuery;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.Any;
import io.spine.core.TenantId;
import io.spine.net.EmailAddress;
import io.spine.net.InternetDomain;
import io.spine.server.storage.datastore.record.Entities;
import io.spine.server.storage.datastore.record.RecordId;
import io.spine.server.tenant.TenantAwareFunction0;
import io.spine.server.tenant.TenantAwareOperation;
import io.spine.testing.SlowTest;
import io.spine.testing.server.storage.datastore.EmulatorTest;
import io.spine.testing.server.storage.datastore.TestDatastoreWrapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static com.google.cloud.datastore.Query.newEntityQueryBuilder;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.truth.Truth.assertThat;
import static io.spine.server.storage.datastore.DatastoreWrapper.wrap;
import static io.spine.server.storage.datastore.given.DatastoreWrapperTestEnv.NAMESPACE_HOLDER_KIND;
import static io.spine.server.storage.datastore.given.DatastoreWrapperTestEnv.ensureNamespace;
import static io.spine.server.storage.datastore.given.DatastoreWrapperTestEnv.localDatastore;
import static io.spine.server.storage.datastore.given.DatastoreWrapperTestEnv.remoteDatastore;
import static io.spine.server.storage.datastore.given.TestEnvironment.runsOnCi;
import static io.spine.server.storage.datastore.given.TestNamespaceSuppliers.multitenant;
import static io.spine.server.storage.datastore.given.TestNamespaceSuppliers.singleTenant;
import static io.spine.testing.server.storage.datastore.TestDatastoreWrapper.wrap;
import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@DisplayName("`DatastoreWrapper` should")
@EmulatorTest
final class DatastoreWrapperTest {

    private static final Kind GENERIC_ENTITY_KIND = Kind.of("my.entity");

    @AfterAll
    static void tearDown() {
        var wrapper = wrap(localDatastore(), singleTenant());
        wrapper.dropTable(NAMESPACE_HOLDER_KIND);
    }

    @Nested
    class NotWaiting {

        private TestDatastoreWrapper wrapper;

        @BeforeEach
        void setUp() {
            wrapper = wrap(localDatastore(), false);
        }

        @AfterEach
        void tearDown() {
            wrapper.dropAllTables();
        }

        @SlowTest
        @Test
        @DisplayName("support bulk reads")
        void testBulkRead() throws InterruptedException {
            var bulkSize = 1001;

            var entities = newTestEntities(bulkSize, wrapper);
            var expectedEntities = entities.values();

            wrapper.createOrUpdate(expectedEntities);

            // Wait for some time to make sure the writing is complete
            Thread.sleep(bulkSize * 5L);

            var keys = ImmutableList.copyOf(entities.keySet());
            Collection<Entity> readEntities = wrapper.lookup(keys);
            assertEquals(entities.size(), readEntities.size());
            assertTrue(expectedEntities.containsAll(readEntities));
        }

        @SlowTest
        @Test
        @DisplayName("support big bulk reads")
        void testBigBulkRead() throws InterruptedException {
            var bulkSize = 2001;

            var entities = newTestEntities(bulkSize, wrapper);
            var expectedEntities = entities.values();

            wrapper.createOrUpdate(expectedEntities);

            // Wait for some time to make sure the writing is complete
            Thread.sleep(bulkSize * 3L);

            StructuredQuery<Entity> query = newEntityQueryBuilder()
                    .setKind(GENERIC_ENTITY_KIND.value())
                    .build();
            Collection<Entity> readEntities = newArrayList(wrapper.read(query));
            assertEquals(entities.size(), readEntities.size());
            assertTrue(expectedEntities.containsAll(readEntities));
        }
    }

    /**
     * The acceptance test for the remote datastore.
     *
     * <p>The suite performs basic read/write operations on the real datastore of the
     * {@code spine-dev} project. The purpose of the test is to make sure that everything related
     * to the real storage configuration (like indices) is done correctly.
     *
     * <p>Runs only on CI.
     */
    @Nested
    class Remote {

        private TestDatastoreWrapper wrapper;

        @BeforeEach
        void setUp() {
            // The remote suite needs the `spine-dev.json` credential, which is
            // present only where it is provisioned. The test body already
            // assumes this, but the credential is loaded here first, so the
            // assumption must come before it.
            assumeTrue(runsOnCi());
            wrapper = wrap(remoteDatastore(), true);
        }

        @AfterEach
        void tearDown() {
            // `@AfterEach` still runs when the setup aborts on the assumption
            // above, leaving the wrapper unassigned.
            if (wrapper != null) {
                wrapper.dropAllTables();
            }
        }

        @SlowTest
        @Test
        @DisplayName("read and write entities in the remote datastore")
        void testBulkRead() {
            assumeTrue(runsOnCi());

            var entityCount = 5;
            var entities = newTestEntities(entityCount, wrapper);
            var expectedEntities = entities.values();

            wrapper.createOrUpdate(expectedEntities);

            var keys = ImmutableList.copyOf(entities.keySet());
            Collection<Entity> readEntities = wrapper.lookup(keys);
            assertEquals(entities.size(), readEntities.size());
            assertTrue(expectedEntities.containsAll(readEntities));
        }
    }

    @Nested
    @DisplayName("read entities by keys")
    class ReadByKeys {

        private TestDatastoreWrapper wrapper;

        @BeforeEach
        void setUp() {
            wrapper = wrap(localDatastore(), false);
        }

        @AfterEach
        void tearDown() {
            wrapper.dropAllTables();
        }

        @SlowTest
        @Test
        @DisplayName("replacing missing entities with null")
        void testMissingAreNull() throws InterruptedException {
            var bulkSize = 3;

            var entities = createAndStoreTestEntities(bulkSize);

            // Wait for some time to make sure the writing is complete
            Thread.sleep(bulkSize * 5L);

            List<Key> presentKeys = newArrayList(entities.keySet());

            List<Key> queryKeys = new ImmutableList.Builder<Key>()
                    .add(newKey("missing-key-1", wrapper))
                    .add(presentKeys.get(0))
                    .add(presentKeys.get(1))
                    .add(newKey("missing-key-2", wrapper))
                    .add(presentKeys.get(2))
                    .build();

            var actualEntities = wrapper.lookup(queryKeys);

            assertThat(actualEntities)
                    .containsExactly(
                            null,
                            entities.get(presentKeys.get(0)),
                            entities.get(presentKeys.get(1)),
                            null,
                            entities.get(presentKeys.get(2))
                    );
        }

        @SlowTest
        @Test
        @DisplayName("preserving order")
        void test() throws InterruptedException {
            var bulkSize = 3;

            var entities = createAndStoreTestEntities(bulkSize);

            // Wait for some time to make sure the writing is complete
            Thread.sleep(bulkSize * 5L);

            List<Key> presentKeys = newArrayList(entities.keySet());

            List<Key> queryKeys = new ImmutableList.Builder<Key>()
                    .add(presentKeys.get(2))
                    .add(presentKeys.get(0))
                    .add(presentKeys.get(1))
                    .build();

            var actualEntities = wrapper.lookup(queryKeys);

            assertThat(actualEntities)
                    .containsExactlyElementsIn(entities.values());
        }

        private Map<Key, Entity> createAndStoreTestEntities(int bulkSize) {
            var entities = newTestEntities(bulkSize, wrapper);
            var expectedEntities = entities.values();
            wrapper.createOrUpdate(expectedEntities);
            return entities;
        }
    }

    @Nested
    @DisplayName("read with structured query")
    class ReadWithStructuredQuery {

        private TestDatastoreWrapper wrapper;

        @BeforeEach
        void setUp() {
            wrapper = wrap(localDatastore(), false);
        }

        @AfterEach
        void tearDown() {
            wrapper.dropAllTables();
        }

        @SuppressWarnings({"CheckReturnValue", "ResultOfMethodCallIgnored"})
        // Called to throw exception.
        @Test
        @DisplayName("throws IAE during batch read with limit")
        void testIaeOnBatchReadWithLimit() {
            assertThrows(IllegalArgumentException.class,
                         () -> wrapper.readAll(newEntityQueryBuilder()
                                                       .setLimit(5)
                                                       .build(), 1));
        }

        @SuppressWarnings({"CheckReturnValue", "ResultOfMethodCallIgnored"})
        // Called to throw exception.
        @Test
        @DisplayName("throws IAE during batch read of 0 size")
        void testIaeOnBatchReadWithZeroSize() {
            assertThrows(IllegalArgumentException.class,
                         () -> wrapper.readAll(newEntityQueryBuilder().build(), 0));
        }
    }

    @Test
    @DisplayName("generate key factories aware of tenancy")
    void testGenerateKeyFactory() {
        var wrapper = wrap(localDatastore(), multitenant());
        var tenantId1 = "first-tenant-ID";
        var tenantId1Prefixed = "Vfirst-tenant-ID";
        var tenantId2 = "second@tenant.id";
        var tenantId2Prefixed = "Esecond-at-tenant.id";
        var tenantId3 = "third.id";
        var tenantId3Prefixed = "Dthird.id";
        var datastore = wrapper.datastore();
        ensureNamespace(tenantId1Prefixed, datastore);
        ensureNamespace(tenantId2Prefixed, datastore);
        ensureNamespace(tenantId3Prefixed, datastore);
        var emailAddress2 = EmailAddress.newBuilder()
                .setValue(tenantId2)
                .build();
        var internetDomain3 = InternetDomain.newBuilder()
                .setValue(tenantId3)
                .build();
        var id1 = TenantId.newBuilder()
                .setValue(tenantId1)
                .build();
        var id2 = TenantId.newBuilder()
                .setEmail(emailAddress2)
                .build();
        var id3 = TenantId.newBuilder()
                .setDomain(internetDomain3)
                .build();
        checkTenantIdInKey(tenantId1Prefixed, id1, wrapper);
        checkTenantIdInKey(tenantId2Prefixed, id2, wrapper);
        checkTenantIdInKey(tenantId3Prefixed, id3, wrapper);
    }

    static void checkTenantIdInKey(String id, TenantId tenantId, DatastoreWrapper wrapper) {
        new TenantAwareOperation(tenantId) {
            @Override
            public void run() {
                var key = wrapper.keyFactory(GENERIC_ENTITY_KIND)
                                 .newKey(42L);
                assertEquals(id, key.getNamespace());
            }
        }.execute();
    }

    @Test
    @DisplayName("produce lazy iterator on query read")
    void testLazyIterator() {
        var wrapper = wrap(localDatastore(), singleTenant());
        var count = 2;
        Map<?, Entity> entities = newTestEntities(count, wrapper);
        var expctedEntities = entities.values();
        wrapper.createOrUpdate(expctedEntities);

        StructuredQuery<Entity> query = newEntityQueryBuilder()
                .setKind(GENERIC_ENTITY_KIND.value())
                .build();
        Iterator<Entity> result = wrapper.read(query);

        assertTrue(result.hasNext());
        var first = result.next();
        assertTrue(result.hasNext());
        var second = result.next();

        assertThat(expctedEntities)
                .containsExactly(first, second);
        assertThrows(NoSuchElementException.class, result::next);
    }

    @Test
    @DisplayName("allow to add new namespaces 'on the go'")
    void testNewNamespaces() {
        var wrapper = wrap(localDatastore(), multitenant());
        var tenantId = TenantId.newBuilder()
                .setValue("Luke_I_am_your_tenant.")
                .build();
        var key = "noooooo";
        var entityKey = new TenantAwareFunction0<Key>(tenantId) {
            @Override
            public Key apply() {
                var entityKey = wrapper.keyFactory(NAMESPACE_HOLDER_KIND)
                                       .newKey(key);
                var entity = Entity.newBuilder(entityKey).build();
                wrapper.create(entity);
                return entityKey;
            }
        }.execute();

        // Clean up the namespace.
        wrapper.delete(entityKey);
    }

    /**
     * Cannot be moved to test environment because it uses package-local
     * {@link Entities#fromMessage(com.google.protobuf.Message, Key) fromMessage()}.
     */
    private static Map<Key, Entity> newTestEntities(int n, DatastoreWrapper wrapper) {
        Map<Key, Entity> result = new HashMap<>(n);
        for (var i = 0; i < n; i++) {
            var message = Any.getDefaultInstance();
            var key = newKey(format("record-%s", i), wrapper);
            var entity = Entities.fromMessage(message, key);
            result.put(key, entity);
        }
        return result;
    }

    /**
     * Cannot be moved to test environment because it uses package-local {@link RecordId}.
     */
    private static Key newKey(String id, DatastoreWrapper wrapper) {
        var recordId = new RecordId(id);
        return wrapper.keyFor(GENERIC_ENTITY_KIND, recordId);
    }
}
