package com.voicereminder.vip.core.tts

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.voicereminder.vip.core.model.VoiceLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

data class TtsStatus(
    val ready: Boolean = false,
    val engineLabel: String = "",
    val languages: List<VoiceLanguage> = emptyList(),
    val error: String? = null,
    val speaking: Boolean = false
)

class TtsManager(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val _status = MutableStateFlow(TtsStatus())
    val status: StateFlow<TtsStatus> = _status.asStateFlow()

    private var tts: TextToSpeech? = null
    private var activeUtterance: String? = null
    private var onDone: (() -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    private val lock = Any()

    init {
        initialize()
    }

    private fun initialize() {
        tts = TextToSpeech(context.applicationContext) { result ->
            mainHandler.post {
                if (result != TextToSpeech.SUCCESS) {
                    _status.value = TtsStatus(
                        ready = false,
                        error = "تعذر تشغيل محرك النطق. تأكد من تثبيت محرك Text-to-Speech وبيانات اللغة."
                    )
                    return@post
                }
                val engine = tts
                val label = runCatching { engine?.defaultEngine }.getOrNull() ?: "محرك النظام"
                val languages = collectLanguages(engine)
                runCatching { engine?.language = Locale("ar") }
                engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) {
                        mainHandler.post { finishUtterance(utteranceId, null) }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        mainHandler.post { finishUtterance(utteranceId, "تعذر نطق النص.") }
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        mainHandler.post {
                            finishUtterance(utteranceId, "خطأ النطق ($errorCode).")
                        }
                    }
                })
                _status.value = TtsStatus(
                    ready = true,
                    engineLabel = label,
                    languages = languages,
                    error = null
                )
            }
        }
    }

    private fun collectLanguages(engine: TextToSpeech?): List<VoiceLanguage> {
        if (engine == null) return emptyList()
        val voices: Set<Voice>? = runCatching { engine.voices }.getOrNull()
        val result = linkedMapOf<String, VoiceLanguage>()

        val fallback = listOf(
            "ar" to "العربية",
            "ar-SA" to "العربية (السعودية)",
            "en-US" to "الإنجليزية (الولايات المتحدة)",
            "en-GB" to "الإنجليزية (المملكة المتحدة)",
            "id-ID" to "الإندونيسية"
        )
        fallback.forEach { (tag, label) ->
            val available = runCatching {
                engine.isLanguageAvailable(Locale.forLanguageTag(tag))
            }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED) >= TextToSpeech.LANG_AVAILABLE
            val offline = voices
                ?.filter { it.locale.toLanguageTag().substringBefore('-') == tag.substringBefore('-') }
                ?.any { !it.isNetworkConnectionRequired }
                ?: false
            result[tag] = VoiceLanguage(tag, label, offline, available)
        }

        voices?.forEach { voice ->
            val tag = voice.locale.toLanguageTag()
            if (tag.isBlank() || tag == "und") return@forEach
            val label = voice.locale.getDisplayName(Locale("ar")).ifBlank { tag }
            val previous = result[tag]
            val offline = (previous?.offline ?: false) || !voice.isNetworkConnectionRequired
            result[tag] = VoiceLanguage(tag, label, offline, true)
        }

        return result.values.sortedWith(
            compareByDescending<VoiceLanguage> { it.tag.startsWith("ar") }
                .thenByDescending { it.available }
                .thenBy { it.tag }
        )
    }

    fun languageLabel(tag: String): String {
        _status.value.languages.firstOrNull { it.tag == tag }?.let { return it.label }
        return runCatching {
            Locale.forLanguageTag(tag).getDisplayName(Locale("ar")).ifBlank { tag }
        }.getOrDefault(tag)
    }

    fun isLanguageAvailable(tag: String): Boolean {
        val engine = tts ?: return false
        return runCatching {
            engine.isLanguageAvailable(Locale.forLanguageTag(tag)) >= TextToSpeech.LANG_AVAILABLE
        }.getOrDefault(false)
    }

    fun isOfflineLanguage(tag: String): Boolean {
        val languages = _status.value.languages
        return languages.firstOrNull { it.tag == tag }?.offline
            ?: languages.firstOrNull {
                it.tag.substringBefore('-') == tag.substringBefore('-')
            }?.offline
            ?: false
    }

    fun speak(
        text: String,
        languageTag: String = "ar",
        rate: Float = 1f,
        pitch: Float = 1f,
        volume: Float = 0.9f,
        utterancePrefix: String = "vr",
        onDone: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ): Boolean {
        val engine = tts
        if (engine == null || !_status.value.ready) {
            onError?.invoke("محرك النطق غير جاهز.")
            return false
        }
        if (text.isBlank()) {
            onError?.invoke("النص فارغ.")
            return false
        }

        var locale = Locale.forLanguageTag(languageTag)
        var result = runCatching { engine.setLanguage(locale) }
            .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        if (result < TextToSpeech.LANG_AVAILABLE) {
            val fallback = Locale("ar")
            result = runCatching { engine.setLanguage(fallback) }
                .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
            if (result >= TextToSpeech.LANG_AVAILABLE) {
                locale = fallback
            } else {
                onError?.invoke("اللغة المطلوبة غير متاحة في محرك النطق.")
                return false
            }
        }

        synchronized(lock) {
            this.onDone = onDone
            this.onError = onError
        }
        engine.setSpeechRate(rate.coerceIn(0.5f, 2f))
        engine.setPitch(pitch.coerceIn(0.5f, 2f))
        val utteranceId = "$utterancePrefix-${UUID.randomUUID()}"
        activeUtterance = utteranceId
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume.coerceIn(0.05f, 1f))
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, android.media.AudioManager.STREAM_ALARM)
        }
        val started = engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        if (started != TextToSpeech.SUCCESS) {
            finishUtterance(utteranceId, "تعذر بدء النطق.")
            return false
        }
        _status.value = _status.value.copy(speaking = true)
        return true
    }

    fun maxVolume() = 1f

    fun stop() {
        runCatching { tts?.stop() }
        activeUtterance = null
        synchronized(lock) {
            onDone = null
            onError = null
        }
        _status.value = _status.value.copy(speaking = false)
    }

    fun shutdown() {
        stop()
        runCatching { tts?.shutdown() }
        tts = null
        _status.value = TtsStatus(ready = false)
    }

    private fun finishUtterance(utteranceId: String?, error: String?) {
        if (utteranceId != null && activeUtterance != null && utteranceId != activeUtterance) return
        activeUtterance = null
        _status.value = _status.value.copy(speaking = false)
        val done: (() -> Unit)?
        val failure: ((String) -> Unit)?
        synchronized(lock) {
            done = onDone
            failure = onError
            onDone = null
            onError = null
        }
        if (error == null) done?.invoke() else failure?.invoke(error)
    }
}
