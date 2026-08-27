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
import com.google.cloud.datastore.Value;
import com.google.common.testing.NullPointerTester;
import io.spine.core.BoundedContextName;
import io.spine.core.BoundedContextNames;
import io.spine.core.TenantId;
import io.spine.server.storage.ColumnMapping;
import io.spine.server.storage.datastore.config.DsColumnMapping;
import io.spine.server.storage.datastore.config.FlatLayout;
import io.spine.server.storage.datastore.config.RecordLayout;
import io.spine.server.storage.datastore.given.TestColumnMapping;
import io.spine.server.storage.datastore.tenant.NamespaceConverter;
import io.spine.server.storage.datastore.tenant.NamespaceConverterFactory;
import io.spine.test.storage.StgProject;
import io.spine.testing.server.storage.datastore.EmulatorTest;
import io.spine.testing.server.storage.datastore.TestDatastores;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.server.ContextSpec.singleTenant;
import static io.spine.testing.Assertions.assertHasPrivateParameterlessCtor;
import static io.spine.testing.DisplayNames.HAVE_PARAMETERLESS_CTOR;
import static io.spine.testing.DisplayNames.NOT_ACCEPT_NULLS;
import static io.spine.testing.server.storage.datastore.TestDatastores.defaultLocalProjectId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("`DatastoreStorageFactory.Builder` should")
@EmulatorTest
final class DatastoreStorageFactoryBuilderTest {

    @Test
    @DisplayName(HAVE_PARAMETERLESS_CTOR)
    void testPrivateCtor() {
        assertHasPrivateParameterlessCtor(DatastoreStorageFactory.Builder.class);
    }

    @Test
    @DisplayName(NOT_ACCEPT_NULLS)
    void testNulls() {
        new NullPointerTester()
                .setDefault(Datastore.class,
                            datastore())
                .setDefault(ColumnMapping.class, new DsColumnMapping())
                .setDefault(RecordLayout.class, new FlatLayout<>(StgProject.class))
                .setDefault(BoundedContextName.class, BoundedContextNames.newName("NullChecks"))
                .testInstanceMethods(DatastoreStorageFactory.newBuilder(),
                                     NullPointerTester.Visibility.PACKAGE);
    }

    @Test
    @DisplayName("construct factories with default column mapping")
    void testDefaultColumnMapping() {
        var factory = DatastoreStorageFactory.newBuilder()
                .setDatastore(datastore())
                .build();
        var mapping = factory.columnMapping();
        assertNotNull(mapping);
    }

    @Test
    @DisplayName("construct factories with custom column mapping")
    void testCustomColumnMapping() {
        ColumnMapping<Value<?>> mapping = new TestColumnMapping();
        var factory = DatastoreStorageFactory.newBuilder()
                .setDatastore(datastore())
                .setColumnMapping(mapping)
                .build();
        var mappingUsedByFactory = factory.columnMapping();
        assertNotNull(mappingUsedByFactory);

        var someString = "some-test-string";
        var value = mappingUsedByFactory.of(String.class)
                                        .applyTo(someString);
        assertThat(value).isEqualTo(TestColumnMapping.STRING_MAPPING_RESULT);
    }

    @Test
    @DisplayName("use the namespace converter factory set explicitly")
    void testCustomConverterFactory() {
        var converterFactory = NamespaceConverterFactory.defaults();
        var factory = DatastoreStorageFactory.newBuilder()
                .setDatastore(datastore())
                .setConverterFactory(converterFactory)
                .build();
        assertThat(factory.namespaceConverterFactory()).isSameInstanceAs(converterFactory);
    }

    @Test
    @DisplayName("reject setting both a namespace converter and a converter factory")
    void testRejectBothConverterOptions() {
        var withConverter = DatastoreStorageFactory.newBuilder()
                .setDatastore(datastore())
                .setNamespaceConverter(new NoOpNamespaceConverter());
        assertThrows(IllegalStateException.class,
                     () -> withConverter.setConverterFactory(NamespaceConverterFactory.defaults()));

        var withFactory = DatastoreStorageFactory.newBuilder()
                .setDatastore(datastore())
                .setConverterFactory(NamespaceConverterFactory.defaults());
        assertThrows(IllegalStateException.class,
                     () -> withFactory.setNamespaceConverter(new NoOpNamespaceConverter()));
    }

    @Test
    @DisplayName("wrap a namespace converter and freeze it at build time")
    void testNamespaceConverterFrozen() {
        var first = new NoOpNamespaceConverter();
        var builder = DatastoreStorageFactory.newBuilder()
                .setDatastore(datastore())
                .setNamespaceConverter(first);
        var factory = builder.build();

        // Reuse and reconfigure the builder after the factory was already built.
        builder.setNamespaceConverter(new NoOpNamespaceConverter());

        var produced = factory.namespaceConverterFactory()
                              .get(true);
        assertThat(produced).isSameInstanceAs(first);
    }

    @Nested
    class Namespaces {

        private DatastoreOptions.Builder builder;

        @BeforeEach
        void setUp() {
            builder = DatastoreOptions.newBuilder()
                    .setProjectId(defaultLocalProjectId().value())
                    .setCredentials(NoCredentials.getInstance());
        }

        @Test
        @DisplayName("allow custom namespace for single-tenant instances")
        void testCustomNamespace() {
            var namespace = "my.custom.namespace";
            var options =
                    builder.setNamespace(namespace)
                           .build();
            var spec =
                    singleTenant(DatastoreStorageFactoryBuilderTest.class.getSimpleName());
            var factory = DatastoreStorageFactory.newBuilder()
                    .setDatastore(options.getService())
                    .build();
            assertNotNull(factory);
            var actualNamespace = factory.wrapperFor(spec)
                                         .datastore()
                                         .getOptions()
                                         .getNamespace();
            assertEquals(namespace, actualNamespace);
        }
    }

    @Test
    @DisplayName("fail to construct without `Datastore`")
    void testRequireDatastore() {
        assertThrows(NullPointerException.class, DatastoreStorageFactory.newBuilder()::build);
    }

    private static Datastore datastore() {
        return TestDatastores.local();
    }

    /**
     * A minimal {@link NamespaceConverter} used only to exercise the builder validation;
     * its conversion methods are never invoked by these tests.
     */
    private static final class NoOpNamespaceConverter extends NamespaceConverter {

        @Override
        protected String toString(TenantId tenantId) {
            return tenantId.getValue();
        }

        @Override
        protected TenantId toTenantId(String namespace) {
            return TenantId.newBuilder()
                    .setValue(namespace)
                    .build();
        }
    }
}
