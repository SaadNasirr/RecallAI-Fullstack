package com.example.recallai.voice

import android.media.MediaRecorder
import java.io.File

class AudioRecorder {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAtMs: Long = 0L

    @Suppress("DEPRECATION")
    fun start(outputDir: File): File {
        release()

        val file = File.createTempFile("recallai_voice_", ".m4a", outputDir)

        val r = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)

            // Better quality for STT
            setAudioSamplingRate(16000)
            // Lower bitrate keeps upload/transcribe faster while preserving speech clarity.
            setAudioEncodingBitRate(32000)
            setAudioChannels(1)

            setOutputFile(file.absolutePath)
            prepare()
            start()
        }

        recorder = r
        outputFile = file
        startedAtMs = System.currentTimeMillis()
        return file
    }

    fun stop(): File? {
        return try {
            recorder?.apply {
                stop()
                reset()
                release()
            }
            recorder = null

            val elapsed = System.currentTimeMillis() - startedAtMs
            val file = outputFile
            outputFile = null

            // Guard: avoid extremely short clips
            if (elapsed < 1500L) {
                file?.delete()
                null
            } else {
                file
            }
        } catch (_: Exception) {
            recorder = null
            outputFile?.delete()
            outputFile = null
            null
        }
    }

    fun release() {
        try {
            recorder?.release()
        } catch (_: Exception) {
        } finally {
            recorder = null
        }
    }
}