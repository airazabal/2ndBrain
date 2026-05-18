package com.alex.a2ndbrain.core.reflection

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class TtsManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _activeUtteranceId = MutableStateFlow<String?>(null)
    val activeUtteranceId: StateFlow<String?> = _activeUtteranceId.asStateFlow()

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            Log.e("TtsManager", "Failed to construct TextToSpeech", e)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TtsManager", "Default language not supported, falling back to US English")
                tts?.setLanguage(Locale.US)
            }
            
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                    _activeUtteranceId.value = utteranceId
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                    if (_activeUtteranceId.value == utteranceId) {
                        _activeUtteranceId.value = null
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                    if (_activeUtteranceId.value == utteranceId) {
                        _activeUtteranceId.value = null
                    }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    _isSpeaking.value = false
                    if (_activeUtteranceId.value == utteranceId) {
                        _activeUtteranceId.value = null
                    }
                    Log.e("TtsManager", "TTS Playback Error: $errorCode for $utteranceId")
                }

                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    _isSpeaking.value = false
                    if (_activeUtteranceId.value == utteranceId) {
                        _activeUtteranceId.value = null
                    }
                }
            })
            isInitialized = true
        } else {
            Log.e("TtsManager", "Initialization of TextToSpeech failed")
        }
    }

    fun speak(text: String, utteranceId: String) {
        if (!isInitialized || tts == null) {
            Log.w("TtsManager", "TextToSpeech is not initialized yet")
            return
        }
        
        try {
            // Clean markdown notation, links, or emojis from speech text for a highly professional flow
            val cleanText = text
                .replace(Regex("\\[.*?\\]\\(file:///.*?\\)"), "") // remove absolute file markdown links
                .replace(Regex("\\*\\*|\\*|_|#"), "") // remove bold/italic/headings
                .trim()

            val params = android.os.Bundle()
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        } catch (e: Exception) {
            Log.e("TtsManager", "Failed to trigger speak", e)
        }
    }

    fun stop() {
        try {
            tts?.stop()
            _isSpeaking.value = false
            _activeUtteranceId.value = null
        } catch (e: Exception) {
            Log.e("TtsManager", "Failed to stop speak", e)
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
            _isSpeaking.value = false
            _activeUtteranceId.value = null
        } catch (e: Exception) {
            Log.e("TtsManager", "Failed to shutdown speak", e)
        }
    }
}
