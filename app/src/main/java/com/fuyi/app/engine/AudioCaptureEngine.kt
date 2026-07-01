package com.fuyi.app.engine

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection   
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

object AudioCaptureEngine {

    private const val SAMPLE_RATE = 16000
    private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    private const val BUFFER_SIZE_FRAMES = 4096

    fun capture(projection: MediaProjection): Flow<ByteArray> = callbackFlow {
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            .coerceAtLeast(BUFFER_SIZE_FRAMES * 2)

        val config = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()

        val recorder = AudioRecord.Builder()
            .setAudioPlaybackCaptureConfig(config)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AUDIO_FORMAT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_CONFIG)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .build()

        recorder.startRecording()

        val buffer = ByteArray(BUFFER_SIZE_FRAMES * 2)

        try {
            while (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val bytesRead = withContext(Dispatchers.IO) {
                    recorder.read(buffer, 0, buffer.size)
                }
                if (bytesRead > 0) {
                    val chunk = buffer.copyOf(bytesRead)
                    trySend(chunk)
                } else if (bytesRead == AudioRecord.ERROR_INVALID_OPERATION ||
                    bytesRead == AudioRecord.ERROR_DEAD_OBJECT) {
                    break
                }
            }
        } finally {
            recorder.stop()
            recorder.release()
            channel.close()
        }
    }
}
