package com.example.recallai.voice



import android.content.Context

import android.media.AudioAttributes

import android.media.MediaPlayer

import android.os.Looper

import android.speech.tts.TextToSpeech

import com.example.recallai.data.remote.ApiClient

import com.example.recallai.data.remote.TtsRequest

import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.flow.MutableStateFlow

import kotlinx.coroutines.flow.StateFlow

import kotlinx.coroutines.flow.asStateFlow

import kotlinx.coroutines.withContext

import java.io.File

import java.io.FileOutputStream

import java.util.Locale



object RemoteSpeechPlayer {

    private const val USE_LOCAL_TTS_FALLBACK = true

    private val audioLock = Any()

    private var mediaPlayer: MediaPlayer? = null

    private var localTts: TextToSpeech? = null

    private var localTtsReady = false

    private val _isReadingAloud = MutableStateFlow(false)

    val isReadingAloud: StateFlow<Boolean> = _isReadingAloud.asStateFlow()



    /**

     * Stops remote [MediaPlayer] and local [TextToSpeech]. Safe from any thread.

     */

    fun stop() {

        val onMain = Looper.myLooper() == Looper.getMainLooper()

        fun runStop() {

            synchronized(audioLock) {

                _isReadingAloud.value = false

                try {

                    mediaPlayer?.apply {

                        try {

                            stop()

                        } catch (_: Exception) {

                        }

                        release()

                    }

                } finally {

                    mediaPlayer = null

                }

                try {

                    localTts?.stop()

                } catch (_: Exception) {

                }

            }

        }

        if (onMain) {

            runStop()

        } else {

            android.os.Handler(Looper.getMainLooper()).post { runStop() }

        }

    }



    private suspend fun stopBeforeNewPlayback() {

        withContext(Dispatchers.Main) {

            synchronized(audioLock) {

                _isReadingAloud.value = false

                try {

                    mediaPlayer?.apply {

                        try {

                            stop()

                        } catch (_: Exception) {

                        }

                        release()

                    }

                } finally {

                    mediaPlayer = null

                }

                try {

                    localTts?.stop()

                } catch (_: Exception) {

                }

            }

        }

    }



    suspend fun speak(context: Context, text: String) {

        stopBeforeNewPlayback()

        if (text.isBlank()) return



        withContext(Dispatchers.IO) {

            val responseBody = runCatching { ApiClient.api.textToSpeech(TtsRequest(text)) }.getOrNull()

            val contentType = responseBody?.contentType()?.toString().orEmpty()

            val bytes = runCatching { responseBody?.bytes() ?: ByteArray(0) }.getOrDefault(ByteArray(0))

            val canPlayRemote = contentType.contains("audio", ignoreCase = true) && bytes.size > 1024



            if (canPlayRemote) {

                val file = File(context.cacheDir, "tts_${System.currentTimeMillis()}.wav")

                FileOutputStream(file).use { out -> out.write(bytes) }



                withContext(Dispatchers.Main) {

                    synchronized(audioLock) {

                        mediaPlayer?.release()

                        mediaPlayer = null

                        _isReadingAloud.value = false

                        mediaPlayer = MediaPlayer().apply {

                            setAudioAttributes(

                                AudioAttributes.Builder()

                                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)

                                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)

                                    .build()

                            )

                            setDataSource(file.absolutePath)

                            setOnCompletionListener {

                                it.release()

                                synchronized(audioLock) {

                                    if (mediaPlayer === it) {

                                        mediaPlayer = null

                                    }

                                }

                                _isReadingAloud.value = false

                                file.delete()

                            }

                            setOnErrorListener { mp, _, _ ->

                                mp.release()

                                synchronized(audioLock) {

                                    if (mediaPlayer === mp) {

                                        mediaPlayer = null

                                    }

                                }

                                _isReadingAloud.value = false

                                file.delete()

                                if (USE_LOCAL_TTS_FALLBACK) {

                                    speakLocalFallback(context, text)

                                }

                                true

                            }

                            prepare()

                            start()

                            _isReadingAloud.value = true

                        }

                    }

                }

            } else {

                withContext(Dispatchers.Main) {

                    if (USE_LOCAL_TTS_FALLBACK) {

                        speakLocalFallback(context, text)

                    } else {

                        _isReadingAloud.value = false

                    }

                }

            }

        }

    }



    private fun speakLocalFallback(context: Context, text: String) {

        if (text.isBlank()) return

        _isReadingAloud.value = true

        val appContext = context.applicationContext



        if (localTts == null) {

            localTts = TextToSpeech(appContext) { status ->

                localTtsReady = status == TextToSpeech.SUCCESS

                if (localTtsReady) {

                    localTts?.language = Locale.US

                    localTts?.setSpeechRate(0.92f)

                    localTts?.setPitch(1.0f)

                    localTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "recall_local_tts")

                } else {

                    _isReadingAloud.value = false

                }

            }.apply {

                setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {

                    override fun onStart(utteranceId: String?) {

                        _isReadingAloud.value = true

                    }



                    override fun onDone(utteranceId: String?) {

                        _isReadingAloud.value = false

                    }



                    @Deprecated("Deprecated in Java")

                    override fun onError(utteranceId: String?) {

                        _isReadingAloud.value = false

                    }

                })

            }

            return

        }



        if (localTtsReady) {

            localTts?.language = Locale.US

            localTts?.setSpeechRate(0.92f)

            localTts?.setPitch(1.0f)

            localTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "recall_local_tts")

        } else {

            _isReadingAloud.value = false

        }

    }

}

