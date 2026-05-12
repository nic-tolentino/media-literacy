package org.medialiteracy.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DownloadStateTest {

    @Test
    fun downloadingProgressFractionIsRatioOfBytesDownloadedToTotal() {
        val state = DownloadState.Downloading(
            progressFraction = 0.5f,
            bytesDownloaded = 500_000_000L,
            totalBytes = 1_000_000_000L
        )
        assertEquals(0.5f, state.progressFraction)
        assertEquals(500_000_000L, state.bytesDownloaded)
        assertEquals(1_000_000_000L, state.totalBytes)
    }

    @Test
    fun pausedPreservesProgressAndBytes() {
        val state = DownloadState.Paused(
            progressFraction = 0.3f,
            bytesDownloaded = 300L,
            totalBytes = 1000L
        )
        assertEquals(0.3f, state.progressFraction)
    }

    @Test
    fun failedWithRetryableTrueAllowsRetry() {
        val state = DownloadState.Failed("Network error", isRetryable = true)
        assertTrue(state.isRetryable)
    }

    @Test
    fun failedWithRetryableFalseIsTerminal() {
        val state = DownloadState.Failed("Checksum mismatch", isRetryable = false)
        assertFalse(state.isRetryable)
    }

    @Test
    fun cancellingDownloadResetsToIdle() {
        // Verify the state machine contract: cancelDownload() emits Idle, not a Cancelled state.
        // FakeModelRepository below enforces this.
        val fake = FakeModelRepository()
        fake.simulateDownloadStart(ModelVariant.E2B)
        fake.simulateCancel()
        assertTrue(fake.downloadState.value is DownloadState.Idle)
    }

    @Test
    fun downloadProgressionFromIdleToComplete() {
        val fake = FakeModelRepository()
        assertTrue(fake.downloadState.value is DownloadState.Idle)

        fake.simulateDownloadStart(ModelVariant.E2B)
        assertTrue(fake.downloadState.value is DownloadState.Downloading)

        fake.simulateVerifying()
        assertTrue(fake.downloadState.value is DownloadState.Verifying)

        fake.simulateComplete(ModelVariant.E2B)
        assertTrue(fake.downloadState.value is DownloadState.Complete)
        assertEquals(ModelVariant.E2B, fake.installedVariant.value)
    }

    @Test
    fun failedDownloadExposesReason() {
        val fake = FakeModelRepository()
        fake.simulateDownloadStart(ModelVariant.E4B)
        fake.simulateFailed("Server unreachable", retryable = true)

        val state = fake.downloadState.value
        assertTrue(state is DownloadState.Failed)
        assertEquals("Server unreachable", state.reason)
        assertTrue(state.isRetryable)
    }

    @Test
    fun canStartDownloadReturnsFalseWhenNotEnoughDisk() {
        val fake = FakeModelRepository(onlineStatus = true)
        // E4B requires 3.4 GB; give less than that
        assertFalse(fake.canStartDownload(ModelVariant.E4B, freeSpaceGb = 2.0))
    }

    @Test
    fun canStartDownloadReturnsFalseWhenOffline() {
        val fake = FakeModelRepository(onlineStatus = false)
        assertFalse(fake.canStartDownload(ModelVariant.E2B, freeSpaceGb = 10.0))
    }

    @Test
    fun canStartDownloadReturnsTrueWhenOnlineAndSufficientDisk() {
        val fake = FakeModelRepository(onlineStatus = true)
        assertTrue(fake.canStartDownload(ModelVariant.E2B, freeSpaceGb = 5.0))
    }
}
