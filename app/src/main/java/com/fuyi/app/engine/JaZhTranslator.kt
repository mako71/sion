package com.fuyi.app.engine

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class JaZhTranslator {

    private val conditions = DownloadConditions.Builder().requireWifi().build()

    suspend fun prepareModel(): Boolean = suspendCancellableCoroutine { continuation ->
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.JAPANESE)
            .setTargetLanguage(TranslateLanguage.CHINESE)
            .build()

        val translator = Translation.getClient(options)

        translator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                translator.close()
                continuation.resume(true)
            }
            .addOnFailureListener { e ->
                translator.close()
                continuation.resumeWithException(e)
            }
    }

    suspend fun translate(text: String): String? {
        if (text.isBlank()) return text

        return suspendCancellableCoroutine { continuation ->
            val options = TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.JAPANESE)
                .setTargetLanguage(TranslateLanguage.CHINESE)
                .build()

            val translator = Translation.getClient(options)

            translator.downloadModelIfNeeded()
                .addOnSuccessListener {
                    translator.translate(text)
                        .addOnSuccessListener { result ->
                            translator.close()
                            continuation.resume(result)
                        }
                        .addOnFailureListener { e ->
                            translator.close()
                            continuation.resumeWithException(e)
                        }
                }
                .addOnFailureListener { e ->
                    translator.close()
                    continuation.resumeWithException(e)
                }
        }
    }

    fun formatTranslated(text: String): String = text
}
