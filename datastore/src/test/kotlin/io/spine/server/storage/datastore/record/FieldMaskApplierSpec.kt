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

package io.spine.server.storage.datastore.record

import com.google.protobuf.FieldMask
import com.google.protobuf.fieldMask
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.spine.server.entity.EntityRecord
import io.spine.server.storage.given.GivenStorageProject.newEntityRecordWithCols
import io.spine.server.storage.given.GivenStorageProject.newState
import io.spine.test.storage.StgProject
import io.spine.testing.UtilityClassTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests that the deprecated [FieldMaskApplier] returns the records unchanged,
 * as field masks are no longer supported.
 */
@Suppress("DEPRECATION") // Reason: pins the no-op kept for compatibility; no replacement exists.
@DisplayName("`FieldMaskApplier` should")
internal class FieldMaskApplierSpec :
    UtilityClassTest<FieldMaskApplier>(FieldMaskApplier::class.java) {

    @Test
    fun `return a plain message unchanged`() {
        val project = newState()
        val masker = FieldMaskApplier.recordMasker<StgProject>(maskOf("id"))

        masker.apply(project) shouldBeSameInstanceAs project
    }

    @Test
    fun `return an 'EntityRecord' with a packed state unchanged`() {
        val record = newEntityRecordWithCols().record()
        val masker = FieldMaskApplier.recordMasker<EntityRecord>(maskOf("entity_id"))

        masker.apply(record) shouldBeSameInstanceAs record
    }

    private fun maskOf(path: String): FieldMask = fieldMask { paths += path }
}
