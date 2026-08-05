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

import com.google.cloud.datastore.Datastore;

import java.io.Serial;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A value-type representing the Google Cloud project ID.
 */
public final class ProjectId extends DsIdentifier {

    @Serial
    private static final long serialVersionUID = 0L;

    private ProjectId(String value) {
        super(value);
    }

    /**
     * Creates a new instance of {@code ProjectId} with the passed value.
     *
     * @param projectId
     *         the actual project ID value
     * @return new instance of {@code ProjectId}
     */
    public static ProjectId of(String projectId) {
        var result = new ProjectId(checkNotNull(projectId));
        return result;
    }

    /**
     * Creates new instance of {@code ProjectId} with the value taken from the passed
     * {@linkplain Datastore Datastore config}.
     *
     * @param datastore
     *         the {@link Datastore} instance to take the value from
     * @return new instance of {@code ProjectId}
     */
    public static ProjectId of(Datastore datastore) {
        checkNotNull(datastore);
        var options = datastore.getOptions();
        var value = options.getProjectId();
        return of(value);
    }
}
