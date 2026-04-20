package com.yutori.ai

import android.content.Context
import java.io.File

/**
 * Canonical on-disk location for the AI model weights.
 *
 * Shared between `LlmEngineHolder` (reader) and the model-download
 * worker (writer) so both agree on the path.
 *
 * Lives under `filesDir` — app-private internal storage, cleared on
 * app uninstall. Not backed up by Android Auto Backup; we don't want
 * a 2.58 GB blob leaving the device for any reason (`plans/ai-rules-
 * spec.md` §5.2).
 */
object ModelFiles {

    private const val MODEL_DIR = "models"
    private const val MODEL_FILENAME = "gemma-4-E2B-it.litertlm"

    /** Final path the engine loads from, after a verified download. */
    fun modelFile(context: Context): File {
        val dir = File(context.applicationContext.filesDir, MODEL_DIR)
        if (!dir.exists()) dir.mkdirs()
        return File(dir, MODEL_FILENAME)
    }

    /** In-progress download target; atomically renamed on SHA-256 match. */
    fun tmpFile(context: Context): File =
        File(modelFile(context).parentFile, "$MODEL_FILENAME.tmp")
}
