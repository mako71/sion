package com.fuyi.app.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import java.io.File
import java.io.IOException

class VoskRecognizer(private val modelPath: String) {

    private var model: Model? = null
    private var recognizer: Recognizer? = null

    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            model = Model(modelPath)
            recognizer = Recognizer(model!!, 16000.0f)
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    fun recognize(audioFlow: Flow<ByteArray>): Flow<String> = callbackFlow {
        val rec = recognizer ?: throw IllegalStateException("Vosk not initialized")

        audioFlow.collect { chunk ->
            if (rec.acceptWaveForm(chunk, chunk.size)) {
                val result = withContext(Dispatchers.IO) {
                    JSONObject(rec.result).optString("text", "")
                }
                if (result.isNotEmpty()) trySend(result)
            } else {
                val partial = withContext(Dispatchers.IO) {
                    JSONObject(rec.partialResult).optString("partial", "")
                }
                if (partial.isNotEmpty()) trySend("[partial]$partial")
            }
        }

        val final = withContext(Dispatchers.IO) {
            JSONObject(rec.finalResult).optString("text", "")
        }
        if (final.isNotEmpty()) trySend(final)
        channel.close()
    }

    fun release() {
        recognizer?.close()
        model?.close()
    }

    companion object {
        suspend fun resolveModelPath(context: Context): String? = withContext(Dispatchers.IO) {
            val externalDir = File("/sdcard/fuyi/model")
            if (externalDir.exists() && File(externalDir, "am").exists()) {
                return@withContext externalDir.absolutePath
            }

            try {
                StorageService.unpack(context, "model-ja", "model",
                    { model -> },
                    { e -> e.printStackTrace() }
                )
            } catch (e: Exception) { }

            val internalDir = File(context.filesDir, "model")
            if (internalDir.exists() && File(internalDir, "am").exists()) {
                return@withContext internalDir.absolutePath
            }
            null
        }
    }
}
