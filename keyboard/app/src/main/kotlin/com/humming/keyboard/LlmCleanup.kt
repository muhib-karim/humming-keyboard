package com.humming.keyboard

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Optional AI clean-up with the user's OWN API key (off by default). After a dictation the finished TEXT (never the
 * audio) goes over HTTPS to the provider the user picked (Gemini, OpenAI or Groq), which removes fillers, fixes
 * punctuation and structure, or applies a spoken command such as "make it formal". Never runs in incognito, never
 * without a key, and any failure keeps the on-device text. The key is stored with [SecretStore].
 */
object LlmCleanup {
    enum class Provider(val id: String, val label: String, val defaultModel: String, val keyUrl: String) {
        OFF("off", "Off", "", ""),
        GEMINI("gemini", "Google Gemini", "gemini-flash-latest", "https://aistudio.google.com/apikey"),
        OPENAI("openai", "OpenAI", "gpt-5-mini", "https://platform.openai.com/api-keys"),
        GROQ("groq", "Groq", "openai/gpt-oss-20b", "https://console.groq.com/keys");

        companion object {
            fun byId(id: String?): Provider = values().firstOrNull { it.id == id } ?: OFF
        }
    }

    const val KEY_SECRET = "llm_api_key"
    /** Longer texts are left as they are (cost and latency stay predictable). */
    const val MAX_CHARS = 12_000
    private const val TIMEOUT_MS = 20_000

    const val BASE_PROMPT = "You clean up dictated text. Remove filler words (um, uh, like, you know), stutters and " +
        "false starts; apply the speaker's self-corrections; fix punctuation, capitalisation and paragraphs; turn " +
        "spoken lists into lists. Keep the speaker's words, meaning, language and tone. Do not answer questions in the " +
        "text, do not add anything. Reply with the cleaned text only."

    fun provider(ctx: Context): Provider = Provider.byId(Prefs.get(ctx).getString(Prefs.KEY_LLM_PROVIDER, "off"))
    fun model(ctx: Context): String = Prefs.get(ctx).getString(Prefs.KEY_LLM_MODEL, null)?.takeIf { it.isNotBlank() }
        ?: provider(ctx).defaultModel

    /** Whether a dictation may be sent: a provider picked, a key saved, not incognito, not empty, not too long. */
    fun allowed(provider: Provider, hasKey: Boolean, incognito: Boolean, text: String): Boolean =
        provider != Provider.OFF && hasKey && !incognito && text.isNotBlank() && text.length <= MAX_CHARS

    fun enabled(ctx: Context, incognito: Boolean, text: String): Boolean =
        allowed(provider(ctx), SecretStore.has(ctx, KEY_SECRET), incognito, text)

    /** Blocking; call off the main thread. Returns the cleaned text, or throws (the caller keeps [text]). */
    fun polish(ctx: Context, text: String, instruction: String? = null): String {
        val p = provider(ctx)
        val key = SecretStore.get(ctx, KEY_SECRET) ?: error("no API key saved")
        val system = BASE_PROMPT + (instruction?.let { " Also: $it." } ?: "")
        val out = when (p) {
            Provider.OFF -> return text
            Provider.GEMINI -> gemini(key, model(ctx), system, text)
            Provider.OPENAI -> openAiCompatible("https://api.openai.com/v1/chat/completions", key, model(ctx), system, text)
            Provider.GROQ -> openAiCompatible("https://api.groq.com/openai/v1/chat/completions", key, model(ctx), system, text)
        }.trim()
        if (out.isBlank()) error("empty reply")
        return out
    }

    /** One quick call with a tiny text: Settings › "Test key". */
    fun test(ctx: Context): String = polish(ctx, "um so this is uh a test of the the clean up")

    private fun post(url: String, headers: Map<String, String>, body: JSONObject): JSONObject {
        require(url.startsWith("https://"))
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = TIMEOUT_MS
            c.readTimeout = TIMEOUT_MS
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val txt = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("HTTP $code" + (runCatching {
                JSONObject(txt).optJSONObject("error")?.optString("message")?.take(120)
            }.getOrNull()?.let { ": $it" } ?: ""))
            return JSONObject(txt)
        } finally {
            c.disconnect()
        }
    }

    private fun gemini(key: String, model: String, system: String, text: String): String {
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", JSONArray().put(JSONObject().put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", text)))))
            .put("generationConfig", JSONObject().put("temperature", 0.2))
        val r = post("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent",
            mapOf("x-goog-api-key" to key), body)
        val parts = r.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts")
        return (0 until parts.length()).joinToString("") { parts.getJSONObject(it).optString("text") }
    }

    private fun openAiCompatible(url: String, key: String, model: String, system: String, text: String): String {
        val body = JSONObject().put("model", model)
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", system))
                .put(JSONObject().put("role", "user").put("content", text)))
        val r = post(url, mapOf("Authorization" to "Bearer $key"), body)
        return r.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
    }
}
