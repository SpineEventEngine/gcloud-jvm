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

package io.spine.server.storage.datastore.config

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.spine.core.BoundedContextNames.newName
import io.spine.core.Event
import io.spine.core.EventId
import io.spine.server.storage.StorageGroup
import io.spine.server.storage.datastore.Kind
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests the registration of the layouts for context-grouped storages,
 * such as the event store of a Bounded Context.
 */
@DisplayName("`RecordLayouts` should")
internal class RecordLayoutsSpec {

    @Test
    fun `serve the layout registered for a context-grouped storage`() {
        val custom = FlatLayout<EventId, Event>(Kind.of("BillingJournal"))
        val layouts = RecordLayouts.newBuilder()
            .add(newName("Billing"), Event::class.java, custom)
            .build()

        val group = StorageGroup.of(newName("Billing"))
        val found = layouts.find<EventId, Event>(Event::class.java, group)

        found shouldBeSameInstanceAs custom
    }

    @Test
    fun `default to a flat layout under the grouped kind for an unregistered context`() {
        val layouts = RecordLayouts.newBuilder().build()

        val group = StorageGroup.of(newName("Shipping"))
        val found = layouts.find<EventId, Event>(Event::class.java, group)

        found.shouldBeInstanceOf<FlatLayout<EventId, Event>>()
        found.recordKind() shouldBe Kind.of(Event::class.java, group)
    }
}
