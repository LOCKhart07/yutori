package com.yutori.ai

import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.LogSeverity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Process-singleton wrapper around LiteRT-LM's [Engine]. Lazy-init on
 * first request, kept warm for the app lifetime, torn down explicitly
 * when the user disables the feature or deletes the model file.
 *
 * Thread-safe: `ensureReady()` is guarded by a mutex so two concurrent
 * first callers don't both pay the cold-init cost. Stage A measured
 * ~1.3 s warm-start on the Nord 4 (page cache hot) and ~7 s cold-cold
 * (`plans/ai-rules-spec.md` §4.2).
 */
class LlmEngineHolder(
    /**
     * Producer of the `.litertlm` file on disk. Returns `null` if the
     * model hasn't been downloaded yet — in that case [ensureReady]
     * throws `IllegalStateException` and the caller surfaces
     * `ModelUnavailable` to the UI.
     */
    private val modelFileProvider: () -> File?,
) {
    private val mutex = Mutex()
    private var engine: Engine? = null

    suspend fun ensureReady(): Engine = mutex.withLock {
        engine?.let { return@withLock it }
        val file = modelFileProvider() ?: error("AI model file not installed")
        require(file.exists() && file.length() > 0) {
            "AI model file missing or empty: ${file.absolutePath}"
        }
        Engine.setNativeMinLogSeverity(LogSeverity.WARNING)
        val mark = TimeSource.Monotonic.markNow()
        val e = Engine(EngineConfig(modelPath = file.absolutePath, backend = Backend.CPU()))
        e.initialize()
        val cold: Duration = mark.elapsedNow()
        Log.i(TAG, "LLM engine initialised in ${cold.inWholeMilliseconds} ms")
        engine = e
        e
    }

    suspend fun close() = mutex.withLock {
        runCatching { engine?.close() }
        engine = null
    }

    companion object {
        private const val TAG = "YutoriAi"
    }
}
