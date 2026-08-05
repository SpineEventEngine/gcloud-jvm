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

package io.spine.server.storage.datastore.record;

import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.Value;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.Message;
import io.spine.query.RecordQuery;
import io.spine.server.storage.ColumnMapping;
import io.spine.server.storage.RecordStorage;
import io.spine.server.storage.RecordWithColumns;
import io.spine.server.storage.datastore.DatastoreStorageFactory;
import io.spine.server.storage.datastore.DatastoreWrapper;
import io.spine.server.storage.datastore.Kind;
import io.spine.server.storage.datastore.TransactionWrapper;
import io.spine.server.storage.datastore.config.StorageConfiguration;
import io.spine.server.storage.datastore.config.TxSetting;
import io.spine.server.storage.datastore.query.DsLookup;
import io.spine.server.storage.datastore.query.FilterAdapter;
import io.spine.server.storage.datastore.query.PreparedQuery;
import io.spine.type.TypeUrl;

import java.util.Iterator;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterators.transform;
import static io.spine.server.storage.datastore.record.Entities.builderFromMessage;
import static io.spine.server.storage.datastore.record.Entities.toMessage;
import static io.spine.util.Exceptions.newIllegalStateException;

/**
 * {@link RecordStorage} implementation based on Google Cloud Datastore.
 *
 * @param <I>
 *         the type of identifiers of the stored records
 * @param <R>
 *         the type of stored records
 * @see DatastoreStorageFactory
 */
public class DsRecordStorage<I, R extends Message> extends RecordStorage<I, R> {

    private final DatastoreWrapper datastore;
    private final TypeUrl typeUrl;
    private final Kind kind;
    private final DsEntitySpec<I, R> dsSpec;
    private final FilterAdapter columnFilterAdapter;
    private final ColumnMapping<Value<?>> columnMapping;
    private final TxSetting txSetting;

    /**
     * Creates a new instance of the storage according to the passed configuration.
     */
    public DsRecordStorage(StorageConfiguration<I, R> config) {
        super(config.context(), config.recordSpec()
                                      .recordSpec());
        this.datastore = config.datastore();
        columnMapping = config.columnMapping();
        this.columnFilterAdapter = FilterAdapter.of(columnMapping);
        this.txSetting = config.txSetting();
        this.dsSpec = config.recordSpec();
        this.kind = dsSpec.kind();
        this.typeUrl = TypeUrl.of(config.storedType());
    }

    @Override
    public Iterator<I> index() {
        checkNotClosed();
        return Indexes.indexIterator(datastore, kind(), recordSpec().idType());
    }

    @Override
    protected Iterator<I> index(RecordQuery<I, R> query) {
        var spec = recordSpec();
        var recordIterator = readAllRecords(query);
        var result = transform(recordIterator, spec::idValueIn);
        return result;
    }

    @Override
    public Optional<R> read(I id) {
        checkNotClosed();
        var key = keyOf(id);
        var raw = datastore.read(key);
        var result = raw.map(r -> {
            R record = toMessage(raw.get(), typeUrl);
            return record;
        });
        return result;
    }

    @Override
    public void write(I id, R record) {
        checkNotClosed();
        writeRecord(RecordWithColumns.of(id, record));
    }

    @Override
    protected void writeRecord(RecordWithColumns<I, R> record) {
        checkNotNull(record, "Record is null.");
        var entity = entityRecordToEntity(record);
        write((storage) -> storage.createOrUpdate(entity));
    }

    @Override
    protected void writeAllRecords(Iterable<? extends RecordWithColumns<I, R>> records) {
        checkNotNull(records);

        ImmutableList.Builder<Entity> entitiesToWrite = ImmutableList.builder();
        for (var record : records) {
            var entity = entityRecordToEntity(record);
            entitiesToWrite.add(entity);
        }
        var prepared = entitiesToWrite.build();
        write((storage) -> datastore.createOrUpdate(prepared));
    }

    @Override
    protected Iterator<R> readAllRecords(RecordQuery<I, R> query) {
        var result = read((storage) -> lookupWith(query).execute());
        return result.iterator();
    }

    private PreparedQuery<I, R> lookupWith(RecordQuery<I, R> query) {
        return DsLookup.onTopOf(datastore, columnFilterAdapter, dsSpec)
                       .with(query);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Always returns {@code true}, as another request to Datastore is required to tell whether
     * the record has been deleted.
     */
    @CanIgnoreReturnValue
    @Override
    protected boolean deleteRecord(I id) {
        var key = keyOf(id);
        write(storage -> storage.delete(key));
        return true;
    }

    /**
     * Returns the kind of Datastore Entity.
     */
    @VisibleForTesting
    public final Kind kind() {
        return kind;
    }

    /**
     * Creates a new {@code Key} for the passed record identifier.
     */
    protected final Key keyOf(I id) {
        var result = dsSpec.keyOf(id, datastore);
        return result;
    }

    /**
     * Creates a new Datastore {@code Entity} from the passed {@code RecordWithColumns}.
     */
    protected final Entity entityRecordToEntity(RecordWithColumns<I, R> recordWithCols) {
        var record = recordWithCols.record();
        var id = recordWithCols.id();
        var key = keyOf(id);
        var entity = builderFromMessage(record, key);

        recordWithCols.columnNames()
                      .forEach(columnName -> {
                          var columnValue = recordWithCols.columnValue(columnName, columnMapping);
                          entity.set(columnName.value(), columnValue);
                      });

        var completeEntity = entity.build();
        return completeEntity;
    }

    /**
     * Starts a new Datastore transaction, and returns a {@link TransactionWrapper} around it.
     */
    protected final TransactionWrapper newTransaction() {
        return datastore.newTransaction();
    }

    /**
     * Converts a Datastore {@code Entity} to the record of type served by this storage.
     */
    protected final R toRecord(Entity entity) {
        return toMessage(entity, typeUrl);
    }

    private <V> V read(ReadOperation<V> operation) {
        if (txSetting.txEnabled()) {
            try (var tx = newTransaction()) {
                var result = operation.perform(tx);
                tx.commit();
                return result;
            } catch (RuntimeException e) {
                throw exceptionWithMessage(e, "ReadOperation");
            }
        } else {
            var result = operation.perform(datastore);
            return result;
        }
    }

    private static RuntimeException
    exceptionWithMessage(RuntimeException e, String operation) throws IllegalStateException {
        throw newIllegalStateException(e, "Error executing `%s` transactionally.", operation);
    }

    private void write(WriteOperation operation) {
        if (txSetting.txEnabled()) {
            try (var tx = newTransaction()) {
                operation.perform(tx);
                tx.commit();
            } catch (RuntimeException e) {
                throw exceptionWithMessage(e, "WriteOperation");
            }
        } else {
            operation.perform(datastore);
        }
    }
}
