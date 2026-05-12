package org.medialiteracy.domain

enum class ModelVariant(val fileName: String, val displayName: String, val approximateSizeGb: Double) {
    E2B("gemma-4-E2B-it.litertlm", "Gemma 4 E2B", 2.4),
    E4B("gemma-4-E4B-it.litertlm", "Gemma 4 E4B", 3.4)
}
