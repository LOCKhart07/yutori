package com.yutori.aispike

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration

private const val TAG = "YutoriSpike"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SpikeScreen() }

        // Headless mode — launched via `adb shell am start -n
        // com.yutori.aispike/.MainActivity -e autoRun true`. Runs the
        // full fixture benchmark and logs every result under TAG so the
        // host can read it with `adb logcat -s YutoriSpike:*`. The UI
        // is still set above so the app is obviously running, but no
        // taps are required.
        if (intent?.getBooleanExtra("autoRun", false) == true) {
            val modeStr = intent?.getStringExtra("mode")?.lowercase() ?: "tool"
            val mode = when (modeStr) {
                "parser" -> RunMode.PARSER
                "parser-fresh" -> RunMode.PARSER_FRESH_ENGINE
                else -> RunMode.TOOL_CALL
            }
            Log.i(TAG, "HEADLESS_START mode=$mode")
            val runner = SpikeRunner(this)
            lifecycleScope.launch {
                runCatching {
                    val init = runner.initEngine(mode)
                    Log.i(
                        TAG,
                        "INIT_OK mode=$mode cold_ms=${init.coldInit.inWholeMilliseconds} " +
                            "model=${init.modelFile.path} " +
                            "size_bytes=${init.modelFile.sizeBytes}",
                    )
                    FIXTURE_PROMPTS.forEachIndexed { i, p ->
                        runCatching {
                            val r = runner.runOne(p, mode)
                            Log.i(
                                TAG,
                                "RESULT mode=$mode idx=${i + 1} first_ms=${r.firstTokenLatency.inWholeMilliseconds} " +
                                    "total_ms=${r.totalLatency.inWholeMilliseconds} " +
                                    "extracted=${r.extractedRule != null} " +
                                    "rule=${r.extractedRule} " +
                                    "prompt=\"$p\" " +
                                    "raw_response=\"${r.response.replace("\n", " | ").take(300)}\"",
                            )
                        }.onFailure { Log.w(TAG, "RESULT_FAIL idx=${i + 1} msg=${it.message}", it) }
                    }
                    runner.close()
                    Log.i(TAG, "HEADLESS_DONE")
                }.onFailure { Log.e(TAG, "HEADLESS_FAIL msg=${it.message}", it) }
            }
        }
    }
}

@Suppress("LongMethod")
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SpikeScreen() {
    val context = LocalContext.current
    val runner = remember { SpikeRunner(context) }
    val scope = rememberCoroutineScope()
    val log = remember { mutableStateOf("Model expected at:\n${context.getExternalFilesDir(null)}/${SpikeRunner.MODEL_FILENAME}\n") }
    var prompt by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    fun append(line: String) {
        log.value = log.value + line + "\n"
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Yutori AI spike") }) },
    ) { padding ->
        Surface(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = !busy,
                        onClick = {
                            busy = true
                            scope.launch {
                                runCatching {
                                    append("→ init engine…")
                                    val res = withContext(Dispatchers.Default) { runner.initEngine(RunMode.TOOL_CALL) }
                                    append(
                                        "  model: ${res.modelFile.path} (${"%.1f".format(res.modelFile.sizeBytes / 1_048_576.0)} MiB)",
                                    )
                                    append("  cold init: ${res.coldInit.format()}")
                                }.onFailure { append("✗ init failed: ${it.message}") }
                                busy = false
                            }
                        },
                    ) { Text("Load model") }

                    Button(
                        enabled = !busy,
                        onClick = {
                            busy = true
                            scope.launch {
                                runBench(runner, ::append)
                                busy = false
                            }
                        },
                    ) { Text("Run fixtures") }

                    Button(
                        enabled = !busy,
                        onClick = {
                            runner.close()
                            append("engine closed")
                        },
                    ) { Text("Close") }
                }

                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Custom prompt") },
                    modifier = Modifier.fillMaxWidth(),
                )

                Button(
                    enabled = !busy && prompt.isNotBlank(),
                    onClick = {
                        busy = true
                        val p = prompt
                        scope.launch {
                            runOnce(runner, p, ::append)
                            busy = false
                        }
                    },
                ) { Text("Run custom") }

                Spacer(Modifier.height(8.dp))

                Text(
                    log.value,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                )
            }
        }
    }
}

private suspend fun runBench(runner: SpikeRunner, append: (String) -> Unit) {
    append("→ running ${FIXTURE_PROMPTS.size} fixtures…")
    val firsts = mutableListOf<Duration>()
    val totals = mutableListOf<Duration>()
    var hit = 0
    FIXTURE_PROMPTS.forEachIndexed { i, p ->
        runCatching {
            val r = withContext(Dispatchers.Default) { runner.runOne(p, RunMode.TOOL_CALL) }
            firsts += r.firstTokenLatency
            totals += r.totalLatency
            if (r.extractedRule != null) hit++
            append("[${i + 1}/${FIXTURE_PROMPTS.size}] ${r.firstTokenLatency.format()} | ${r.totalLatency.format()} → ${r.extractedRule ?: "(no tool call)"}")
        }.onFailure { append("[${i + 1}] ✗ ${it.message}") }
    }
    if (totals.isNotEmpty()) {
        append("— summary —")
        append("  tool-call rate: $hit/${FIXTURE_PROMPTS.size}")
        append("  first-token median: ${median(firsts).format()}")
        append("  total median:       ${median(totals).format()}")
        append("  total max:          ${totals.max().format()}")
    }
}

private suspend fun runOnce(runner: SpikeRunner, prompt: String, append: (String) -> Unit) {
    runCatching {
        val r = withContext(Dispatchers.Default) { runner.runOne(prompt, RunMode.TOOL_CALL) }
        append("→ ${prompt}")
        append("  first-token: ${r.firstTokenLatency.format()}  total: ${r.totalLatency.format()}")
        append("  extracted:   ${r.extractedRule ?: "(no tool call — raw response: ${r.response.take(200)})"}")
    }.onFailure { append("✗ ${it.message}") }
}

private fun median(xs: List<Duration>): Duration =
    xs.sorted().let { if (it.isEmpty()) Duration.ZERO else it[it.size / 2] }

private fun Duration.format(): String = "${inWholeMilliseconds} ms"
