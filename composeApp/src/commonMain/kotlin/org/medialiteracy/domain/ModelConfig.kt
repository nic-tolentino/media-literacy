package org.medialiteracy.domain

object ModelConfig {
    const val BASE_URL = "https://huggingface.co/nic-tolentino/news-decoder/resolve/main/"
    fun urlForVariant(variant: ModelVariant) = "$BASE_URL${variant.fileName}"
}

