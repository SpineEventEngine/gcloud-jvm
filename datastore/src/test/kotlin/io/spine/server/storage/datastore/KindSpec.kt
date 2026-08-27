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

package io.spine.server.storage.datastore

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.spine.core.BoundedContextNames.newName
import io.spine.core.Event
import io.spine.server.storage.StorageGroup
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Supplements the Java-based [KindTest] with the cases of composing kinds
 * for the grouped storages, including the event stores of Bounded Contexts.
 */
@DisplayName("`Kind` should")
internal class KindSpec {

    @Test
    fun `compose a grouped kind from the group name and the record type`() {
        Kind.of(Event::class.java, StorageGroup("Billing")).value() shouldBe "Billing-Event"
    }

    @Test
    fun `compose the kind of a context event store from the context-derived group`() {
        val group = StorageGroup.of(newName("Billing"))

        Kind.of(Event::class.java, group).value() shouldBe "Billing-Event"
    }

    @Test
    fun `keep the group name verbatim, telling case-different names apart`() {
        val upper = Kind.of(Event::class.java, StorageGroup("Billing"))
        val lower = Kind.of(Event::class.java, StorageGroup("billing"))

        lower.value() shouldBe "billing-Event"
        lower shouldNotBe upper
    }
}
