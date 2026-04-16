package com.spendwise.macrobenchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The dashboard tree has several scrollable nodes: the outer Compose view,
 * a ~36-px-wide pager-indicator strip, and the actual content ScrollView.
 * `By.scrollable(true)` picks hierarchy-first, which tends to hit one of
 * the decoration nodes — flinging those renders no frames and Macrobenchmark
 * fails with "Observed no expect/actual slices". Picking the widest one
 * skips the thin indicator and the zero-area outer shell.
 */
private const val MIN_SCROLL_WIDTH_PX = 400

/**
 * Dashboard scroll jank. Launches the app, finds the first scrollable
 * container on the dashboard, and flings it a few times. Frame timings
 * (P50/P95/P99) are the baseline against which animation work (#52–#56)
 * will be measured.
 *
 * NOTE: meaningful numbers require a device seeded with real data — the
 * dashboard shows near-empty state on a fresh install. Either run against
 * your personal device after normal SMS ingest, or wait until fixture
 * seeding lands as a follow-up.
 */
@RunWith(AndroidJUnit4::class)
class DashboardScrollBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun dashboardScroll() = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
            startActivityAndWait()
        },
    ) {
        device.wait(Until.findObject(By.scrollable(true)), 5_000)
        val scrollable = device.findObjects(By.scrollable(true))
            .filter { it.visibleBounds.width() >= MIN_SCROLL_WIDTH_PX }
            .maxByOrNull { it.visibleBounds.height() }
            ?: return@measureRepeated
        scrollable.setGestureMargin(device.displayWidth / 5)
        repeat(3) {
            scrollable.fling(Direction.DOWN)
            device.waitForIdle()
            scrollable.fling(Direction.UP)
            device.waitForIdle()
        }
    }
}
