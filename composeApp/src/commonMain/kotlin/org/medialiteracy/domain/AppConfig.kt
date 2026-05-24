package org.medialiteracy.domain

object AppConfig {
    /**
     * Maximum character length of input text articles for analysis.
     */
    const val MAX_ARTICLE_LENGTH = 16000

    /**
     * Maximum sequence length (in tokens) for CPU inference mode.
     */
    const val CPU_MAX_NUM_TOKENS = 8192

    /**
     * Maximum sequence length (in tokens) for GPU inference mode.
     */
    const val GPU_MAX_NUM_TOKENS = 16384

    /**
     * Maximum sequence length (in tokens) for emergency fallback CPU mode.
     */
    const val FALLBACK_CPU_MAX_NUM_TOKENS = 8192

    /**
     * Total generated tokens limit before resetting the inference session.
     */
    const val TOKEN_BUDGET_LIMIT = 7000
}
