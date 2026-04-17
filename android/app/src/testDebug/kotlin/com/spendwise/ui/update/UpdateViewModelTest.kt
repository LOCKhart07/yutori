package com.spendwise.ui.update

import androidx.test.core.app.ApplicationProvider
import com.spendwise.ui.TestApp
import com.spendwise.update.UpdateInstallEvents
import com.spendwise.update.UpdatePrefs
import com.spendwise.update.model.DownloadState
import com.spendwise.update.model.LatestRelease
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApp::class)
class UpdateViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun release(
        tag: String = "v0.3.0",
        name: String = "Yutori 0.3.0",
        body: String = "",
        asset: LatestRelease.Asset? = LatestRelease.Asset(
            url = "https://api.github.test/assets/1",
            sizeBytes = 1024L,
            name = "yutori-0.3.0.apk",
        ),
    ) = LatestRelease(tagName = tag, name = name, body = body, asset = asset)

    private fun prefs(): UpdatePrefs = UpdatePrefs(ApplicationProvider.getApplicationContext())

    private fun buildVm(
        fetchLatest: suspend () -> Result<LatestRelease?> = { Result.success(null) },
        downloadAsset: (LatestRelease.Asset) -> Flow<DownloadState> = { flowOf() },
        installApk: (File) -> Unit = {},
        currentVersion: String = "0.2.0",
        prefs: UpdatePrefs = prefs(),
        installOutcomes: MutableSharedFlow<UpdateInstallEvents.Outcome> = MutableSharedFlow(extraBufferCapacity = 4),
    ): UpdateViewModel = UpdateViewModel(
        fetchLatest = fetchLatest,
        downloadAsset = downloadAsset,
        installApk = installApk,
        prefs = prefs,
        currentVersion = currentVersion,
        ioDispatcher = dispatcher,
        clock = { 1_700_000_000_000L },
        installOutcomes = installOutcomes,
    )

    @Test
    fun `onCheckNow with newer remote transitions to Available`() = runTest(dispatcher) {
        val vm = buildVm(fetchLatest = { Result.success(release("v0.3.0")) })
        vm.onCheckNow()
        advanceUntilIdle()

        val phase = vm.state.value.phase
        assertTrue(phase is UpdateScreenState.Phase.Available)
        assertEquals("v0.3.0", (phase as UpdateScreenState.Phase.Available).release.tagName)
    }

    @Test
    fun `onCheckNow with equal remote transitions to UpToDate`() = runTest(dispatcher) {
        val vm = buildVm(fetchLatest = { Result.success(release("v0.2.0")) })
        vm.onCheckNow()
        advanceUntilIdle()
        assertTrue(vm.state.value.phase is UpdateScreenState.Phase.UpToDate)
    }

    @Test
    fun `onCheckNow with no releases transitions to UpToDate`() = runTest(dispatcher) {
        val vm = buildVm(fetchLatest = { Result.success(null) })
        vm.onCheckNow()
        advanceUntilIdle()
        assertTrue(vm.state.value.phase is UpdateScreenState.Phase.UpToDate)
    }

    @Test
    fun `failed fetch transitions to ErrorChecking`() = runTest(dispatcher) {
        val vm = buildVm(fetchLatest = { Result.failure(RuntimeException("boom")) })
        vm.onCheckNow()
        advanceUntilIdle()
        assertTrue(vm.state.value.phase is UpdateScreenState.Phase.ErrorChecking)
    }

    @Test
    fun `successful check stamps lastCheckAt on prefs`() = runTest(dispatcher) {
        val p = prefs()
        val vm = buildVm(
            fetchLatest = { Result.success(null) },
            prefs = p,
        )
        vm.onCheckNow()
        advanceUntilIdle()
        assertEquals(1_700_000_000_000L, p.lastCheckAt)
    }

    @Test
    fun `failed check does not stamp lastCheckAt`() = runTest(dispatcher) {
        val p = prefs()
        val vm = buildVm(
            fetchLatest = { Result.failure(RuntimeException()) },
            prefs = p,
        )
        vm.onCheckNow()
        advanceUntilIdle()
        assertEquals(0L, p.lastCheckAt)
    }

    @Test
    fun `onOpenDialog only opens when in Available phase`() = runTest(dispatcher) {
        val vm = buildVm(fetchLatest = { Result.success(release("v0.3.0")) })
        vm.onOpenDialog()
        // No effect yet — still in NotCheckedYet.
        assertEquals(false, vm.state.value.dialogVisible)

        vm.onCheckNow()
        advanceUntilIdle()
        vm.onOpenDialog()
        assertEquals(true, vm.state.value.dialogVisible)
    }

    @Test
    fun `onDismissDialog stamps dismissedTag when on Available`() = runTest(dispatcher) {
        val p = prefs()
        val vm = buildVm(
            fetchLatest = { Result.success(release("v0.3.0")) },
            prefs = p,
        )
        vm.onCheckNow()
        advanceUntilIdle()
        vm.onOpenDialog()
        vm.onDismissDialog()

        assertEquals(false, vm.state.value.dialogVisible)
        assertEquals("v0.3.0", p.dismissedTag)
    }

    @Test
    fun `onDismissDialog does not stamp tag during Downloading`() = runTest(dispatcher) {
        val p = prefs()
        val vm = buildVm(
            fetchLatest = { Result.success(release("v0.3.0")) },
            downloadAsset = { flow { /* never emits, never completes */ } },
            prefs = p,
        )
        vm.onCheckNow()
        advanceUntilIdle()
        vm.onOpenDialog()
        vm.onStartDownload()
        advanceUntilIdle()
        vm.onDismissDialog()

        assertNull(p.dismissedTag)
    }

    @Test
    fun `onStartDownload progresses via Downloading states and invokes install on Done`() =
        runTest(dispatcher) {
            val installedApks = mutableListOf<File>()
            val apk = File.createTempFile("update", ".apk").also { it.deleteOnExit() }
            val vm = buildVm(
                fetchLatest = { Result.success(release("v0.3.0")) },
                downloadAsset = {
                    flowOf(
                        DownloadState.Progress(bytes = 100L, total = 1024L),
                        DownloadState.Progress(bytes = 1024L, total = 1024L),
                        DownloadState.Done(apk = apk),
                    )
                },
                installApk = { installedApks += it },
            )
            vm.onCheckNow()
            advanceUntilIdle()
            vm.onStartDownload()
            advanceUntilIdle()

            assertEquals(listOf(apk), installedApks)
        }

    @Test
    fun `failed download transitions to DownloadFailed`() = runTest(dispatcher) {
        val vm = buildVm(
            fetchLatest = { Result.success(release("v0.3.0")) },
            downloadAsset = {
                flowOf(DownloadState.Failed(DownloadState.Reason.Network))
            },
        )
        vm.onCheckNow()
        advanceUntilIdle()
        vm.onStartDownload()
        advanceUntilIdle()

        assertTrue(vm.state.value.phase is UpdateScreenState.Phase.DownloadFailed)
    }

    @Test
    fun `retry from DownloadFailed runs the download again`() = runTest(dispatcher) {
        var calls = 0
        val apk = File.createTempFile("update", ".apk").also { it.deleteOnExit() }
        val vm = buildVm(
            fetchLatest = { Result.success(release("v0.3.0")) },
            downloadAsset = {
                calls += 1
                if (calls == 1) {
                    flowOf(DownloadState.Failed(DownloadState.Reason.Network))
                } else {
                    flowOf(DownloadState.Done(apk))
                }
            },
        )
        vm.onCheckNow()
        advanceUntilIdle()
        vm.onStartDownload()
        advanceUntilIdle()
        assertTrue(vm.state.value.phase is UpdateScreenState.Phase.DownloadFailed)

        vm.onStartDownload()
        advanceUntilIdle()
        assertEquals(2, calls)
    }

    @Test
    fun `onCancelDownload returns to Available`() = runTest(dispatcher) {
        val vm = buildVm(
            fetchLatest = { Result.success(release("v0.3.0")) },
            downloadAsset = { flow { /* never completes */ } },
        )
        vm.onCheckNow()
        advanceUntilIdle()
        vm.onStartDownload()
        advanceUntilIdle()
        assertTrue(vm.state.value.phase is UpdateScreenState.Phase.Downloading)

        vm.onCancelDownload()
        advanceUntilIdle()
        assertTrue(vm.state.value.phase is UpdateScreenState.Phase.Available)
    }

    @Test
    fun `onToggleCheckOnOpen persists to prefs and updates state`() = runTest(dispatcher) {
        val p = prefs()
        val vm = buildVm(prefs = p)
        vm.onToggleCheckOnOpen(false)
        assertEquals(false, p.checkOnOpenEnabled)
        assertEquals(false, vm.state.value.checkOnOpenEnabled)

        vm.onToggleCheckOnOpen(true)
        assertEquals(true, p.checkOnOpenEnabled)
    }

    @Test
    fun `initial phase is NotCheckedYet when lastCheckAt is zero`() = runTest(dispatcher) {
        val p = prefs().also { it.lastCheckAt = 0L }
        val vm = buildVm(prefs = p)
        assertTrue(vm.state.value.phase is UpdateScreenState.Phase.NotCheckedYet)
    }

    @Test
    fun `initial phase is UpToDate when lastCheckAt is non-zero`() = runTest(dispatcher) {
        val p = prefs().also { it.lastCheckAt = 999L }
        val vm = buildVm(prefs = p)
        assertTrue(vm.state.value.phase is UpdateScreenState.Phase.UpToDate)
    }

    @Test
    fun `install Failure outcome flips Downloading to InstallFailed with status and message`() =
        runTest(dispatcher) {
            val outcomes = MutableSharedFlow<UpdateInstallEvents.Outcome>(extraBufferCapacity = 4)
            val vm = buildVm(
                fetchLatest = { Result.success(release("v0.3.0")) },
                downloadAsset = { flow { /* stays Downloading */ } },
                installOutcomes = outcomes,
            )
            vm.onCheckNow()
            advanceUntilIdle()
            vm.onStartDownload()
            advanceUntilIdle()
            assertTrue(vm.state.value.phase is UpdateScreenState.Phase.Downloading)

            outcomes.tryEmit(
                UpdateInstallEvents.Outcome.Failure(status = 4, message = "INSTALL_FAILED_VERSION_DOWNGRADE"),
            )
            advanceUntilIdle()

            val phase = vm.state.value.phase
            assertTrue(phase is UpdateScreenState.Phase.InstallFailed)
            val failed = phase as UpdateScreenState.Phase.InstallFailed
            assertEquals(4, failed.status)
            assertEquals("INSTALL_FAILED_VERSION_DOWNGRADE", failed.message)
        }

    @Test
    fun `install Success outcome does not change Downloading phase`() = runTest(dispatcher) {
        val outcomes = MutableSharedFlow<UpdateInstallEvents.Outcome>(extraBufferCapacity = 4)
        val vm = buildVm(
            fetchLatest = { Result.success(release("v0.3.0")) },
            downloadAsset = { flow { /* stays Downloading */ } },
            installOutcomes = outcomes,
        )
        vm.onCheckNow()
        advanceUntilIdle()
        vm.onStartDownload()
        advanceUntilIdle()
        assertTrue(vm.state.value.phase is UpdateScreenState.Phase.Downloading)

        outcomes.tryEmit(UpdateInstallEvents.Outcome.Success)
        advanceUntilIdle()

        // Success is observational only — the process typically dies
        // with the replacement install before this event is observed.
        assertTrue(vm.state.value.phase is UpdateScreenState.Phase.Downloading)
    }
}
