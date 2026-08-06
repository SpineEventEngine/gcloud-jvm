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

package io.spine.server.storage.datastore.record.given;

import io.spine.core.Event;
import io.spine.core.EventId;
import io.spine.query.RecordQuery;
import io.spine.server.storage.datastore.Kind;
import io.spine.server.storage.datastore.config.EntityGroupLayout;
import io.spine.server.storage.datastore.record.RecordId;
import io.spine.test.storage.StgProject;

/**
 * An ancestor-child layout for the event journal of the {@link StgProject} entities,
 * exercising the {@code Kind}-accepting constructor of {@link EntityGroupLayout}:
 * the journaled events are stored as children of a project record.
 *
 * <p>The ancestor is fixed to {@link #PARENT_RECORD_ID} — enough for the tests
 * observing the produced keys.
 */
public final class ProjectChildJournalLayout
        extends EntityGroupLayout<EventId, Event, StgProject> {

    public static final String KIND = "grouped_project_journal";
    public static final String PARENT_RECORD_ID = "the-parent-project";

    public ProjectChildJournalLayout() {
        super(Kind.of(KIND), StgProject.class);
    }

    @Override
    protected RecordId asRecordId(EventId id) {
        return RecordId.ofEntityId(id);
    }

    @Override
    protected RecordId toAncestorRecordId(EventId id) {
        return RecordId.of(PARENT_RECORD_ID);
    }

    @Override
    protected RecordId extractAncestorId(RecordQuery<EventId, Event> query) {
        return RecordId.of(PARENT_RECORD_ID);
    }
}
