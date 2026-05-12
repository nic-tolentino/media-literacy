package org.medialiteracy.domain

object ModelConfig {
    const val BASE_URL = "https://models.newsdecoder.app/"   // placeholder — set real URL before ship
    fun urlForVariant(variant: ModelVariant) = "$BASE_URL${variant.fileName}"
}
