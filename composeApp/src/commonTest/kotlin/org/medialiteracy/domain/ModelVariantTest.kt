package org.medialiteracy.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModelVariantTest {

    @Test
    fun e2bHasCorrectFileName() {
        assertEquals("gemma-4-E2B-it.litertlm", ModelVariant.E2B.fileName)
    }

    @Test
    fun e4bHasCorrectFileName() {
        assertEquals("gemma-4-E4B-it.litertlm", ModelVariant.E4B.fileName)
    }

    @Test
    fun e4bIsLargerThanE2b() {
        assertTrue(ModelVariant.E4B.approximateSizeGb > ModelVariant.E2B.approximateSizeGb)
    }

    @Test
    fun urlForVariantContainsBaseUrlAndFileName() {
        val url = ModelConfig.urlForVariant(ModelVariant.E2B)
        assertTrue(url.startsWith(ModelConfig.BASE_URL))
        assertTrue(url.endsWith(ModelVariant.E2B.fileName))
    }

    @Test
    fun urlForVariantsDiffer() {
        val e2bUrl = ModelConfig.urlForVariant(ModelVariant.E2B)
        val e4bUrl = ModelConfig.urlForVariant(ModelVariant.E4B)
        assertTrue(e2bUrl != e4bUrl)
    }

    @Test
    fun allVariantsHaveNonBlankDisplayNames() {
        ModelVariant.entries.forEach { variant ->
            assertTrue(variant.displayName.isNotBlank(), "Empty displayName for $variant")
        }
    }
}
