/*
 * Copyright (C) 2022-2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.nlp.latin

import android.content.Context
import com.humming.keyboard.BanglaPhonetic
import com.humming.keyboard.predict.PredictionEngine
import com.humming.keyboard.predict.Predictor
import com.humming.keyboard.predict.StripOrder
import com.humming.keyboard.predict.UserModel
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.appContext
import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.editor.EditorContent
import dev.patrickgold.florisboard.ime.nlp.LiteralWordCandidate
import dev.patrickgold.florisboard.ime.nlp.SpellingProvider
import dev.patrickgold.florisboard.ime.nlp.SpellingResult
import dev.patrickgold.florisboard.ime.nlp.SuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.SuggestionProvider
import dev.patrickgold.florisboard.ime.nlp.WordSuggestionCandidate
import dev.patrickgold.florisboard.lib.devtools.flogDebug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Humming: prediction for the Latin subtypes. The upstream class was a stub (it returned no suggestions and a test
 * dictionary for glide typing); this one is backed by [Predictor]: a 156k-word English dictionary with frequencies and
 * next-word statistics, 60k-word German, Spanish, French, Italian and Portuguese dictionaries, keyboard-aware
 * autocorrect and on-device learning per language ([UserModel], never uploaded). Other languages get no suggestions
 * rather than English ones.
 */
class LatinLanguageProvider(context: Context) : SpellingProvider, SuggestionProvider {
    companion object {
        // Default user ID used for all subtypes, unless otherwise specified.
        // See `ime/core/Subtype.kt` Line 210 and 211 for the default usage
        const val ProviderId = "org.florisboard.nlp.providers.latin"
        private const val GLIDE_WORDS = 40_000
    }

    private val appContext by context.appContext()
    private val prefs by FlorisPreferenceStore

    override val providerId = ProviderId

    private val glideWords = java.util.concurrent.ConcurrentHashMap<String, List<String>>()
    @Volatile private var lastLearnedAt: Pair<Int, String>? = null

    override suspend fun create() {
    }

    override suspend fun preload(subtype: Subtype) = withContext(Dispatchers.IO) {
        PredictionEngine.languageFor(subtype.primaryLocale.language)?.let { PredictionEngine.get(appContext, it) }
        Unit
    }

    /** The dictionary for the subtype's language (en, de, es, fr, it, pt); null for a language without one. */
    private suspend fun predictor(subtype: Subtype): Predictor? {
        val lang = PredictionEngine.languageFor(subtype.primaryLocale.language) ?: return null
        return PredictionEngine.loaded(lang) ?: withContext(Dispatchers.IO) { PredictionEngine.get(appContext, lang) }
    }

    private fun isEnglish(subtype: Subtype) = PredictionEngine.languageFor(subtype.primaryLocale.language) == "en"

    private fun settings(allowPossiblyOffensive: Boolean) = Predictor.Settings(
        autocorrectStrength = prefs.suggestion.autocorrectStrength.get().coerceIn(0, 3),
        allowOffensive = allowPossiblyOffensive,
        nextWord = prefs.suggestion.nextWordPrediction.get(),
        count = prefs.suggestion.suggestionCount.get().coerceIn(3, 5),
        shortcuts = com.humming.keyboard.predict.TextShortcuts.parse(prefs.suggestion.textShortcuts.get()),
        neuralWeight = prefs.suggestion.neuralWeight.get().coerceIn(0, 100),
        personalWeight = prefs.suggestion.personalWeight.get().coerceIn(0, 100),
    )

    override suspend fun spell(
        subtype: Subtype,
        word: String,
        precedingWords: List<String>,
        followingWords: List<String>,
        maxSuggestionCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean,
    ): SpellingResult {
        val p = predictor(subtype) ?: return SpellingResult.validWord()
        if (word.isBlank() || !word.all { it.isLetter() || it == '\'' || it == '\u2019' }) return SpellingResult.validWord()
        if (p.lexicon.isWord(word) || p.user.unigram(word) >= UserModel.NEW_WORD_MIN_USES) return SpellingResult.validWord()
        val before = precedingWords.joinToString(" ") + " "
        val r = p.suggest(before, word, Predictor.Settings(allowOffensive = allowPossiblyOffensive, count = maxSuggestionCount.coerceIn(1, 5)))
        val alternatives = r.ranked.map { it.word }.filter { !it.equals(word, ignoreCase = true) }.take(maxSuggestionCount.coerceAtLeast(1))
        return if (alternatives.isEmpty()) SpellingResult.validWord() else SpellingResult.typo(alternatives.toTypedArray())
    }

    override suspend fun suggest(
        subtype: Subtype,
        content: EditorContent,
        maxCandidateCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean,
    ): List<SuggestionCandidate> {
        // Bangla phonetic: on a Bengali subtype with a Latin layout, the Bangla spelling of the typed word is the
        // auto-commit suggestion (space or punctuation types it) and the Latin word stays one tap away.
        if (subtype.primaryLocale.language == "bn") {
            val typed = content.composingText.ifBlank { content.currentWordText }
            if (!BanglaPhonetic.isPhoneticInput(typed)) return emptyList()
            return listOf(
                WordSuggestionCandidate(BanglaPhonetic.transliterate(typed), confidence = 0.9,
                    isEligibleForAutoCommit = true, isEligibleForUserRemoval = false, sourceProvider = this),
                LiteralWordCandidate(typed, sourceProvider = this),
            )
        }
        // A language without a bundled dictionary gets no suggestions rather than English ones.
        val p = predictor(subtype) ?: return emptyList()
        val word = content.composingText.ifBlank { content.currentWordText }
        val before = content.textBeforeSelection.let { if (word.isNotEmpty() && it.endsWith(word)) it.dropLast(word.length) else it }
        if (!isPrivateSession && prefs.suggestion.learnFromTyping.get()) learnFinishedWord(p, content.textBeforeSelection, word)
        if (word.isNotEmpty() && !word.all { it.isLetter() || it == '\'' || it == '\u2019' }) return emptyList()
        val settings = settings(allowPossiblyOffensive)
        val t0 = System.nanoTime()
        val result = p.suggest(before, word, settings)
        val ms = (System.nanoTime() - t0) / 1_000_000.0
        val slots = StripOrder.arrange(result, settings.count, prefs.suggestion.bestInMiddle.get())
        if (dev.patrickgold.florisboard.BuildConfig.DEBUG) {
            android.util.Log.i("HUMMING", "HUMMING_SUGGEST word=$word -> ${slots.joinToString("|") { it.text + if (it.kind == StripOrder.Kind.AUTOCORRECT) "*" else "" }} ms=${"%.1f".format(Locale.ROOT, ms)}")
        }
        val out = slots.map { s ->
            when (s.kind) {
                StripOrder.Kind.LITERAL -> LiteralWordCandidate(s.text, sourceProvider = this)
                StripOrder.Kind.AUTOCORRECT -> WordSuggestionCandidate(s.text, confidence = 0.9, isEligibleForAutoCommit = true, sourceProvider = this)
                StripOrder.Kind.WORD -> WordSuggestionCandidate(s.text, confidence = 0.6, sourceProvider = this)
            }
        }.toMutableList()
        // Emoji prediction: a word with an obvious emoji ("pizza") offers it in the last slot.
        if (prefs.suggestion.emojiInStrip.get() && out.size >= 3 && isEnglish(subtype)) {
            val key = (if (word.isNotEmpty()) word else Regex("([\\p{L}]+)\\s*$").find(before)?.groupValues?.get(1) ?: "").lowercase(Locale.ROOT)
            val emoji = if (key.length >= 3 && (word.isEmpty() || p.lexicon.isWord(word))) PredictionEngine.emoji(appContext)[key] else null
            if (emoji != null) out[out.lastIndex] = WordSuggestionCandidate(emoji, confidence = 0.5, isEligibleForUserRemoval = false, sourceProvider = this)
        }
        return out
    }

    /**
     * Learns each word once it is finished (followed by a space or punctuation): its use count and the word before it.
     * Local only; skipped in incognito and when "Learn from my typing" is off.
     */
    private fun learnFinishedWord(p: Predictor, textBefore: String, composing: String) {
        if (composing.isNotEmpty() || textBefore.isEmpty()) return
        val last = textBefore.last()
        if (last.isLetter() || last == '\'') return
        val trimmed = textBefore.trimEnd { !it.isLetter() && it != '\'' && it != '\u2019' }
        val m = Regex("([\\p{L}'\u2019]+)$").find(trimmed) ?: return
        val w = m.value.replace('\u2019', '\'')
        val key = Pair(trimmed.length, w)
        if (key == lastLearnedAt) return
        lastLearnedAt = key
        // Words glued to @ / . : # or digits are addresses, links or tags, not vocabulary.
        val preceding = trimmed.dropLast(w.length)
        if (preceding.isNotEmpty() && preceding.last() in "@./:#_\\0123456789") return
        val ctx = p.context(preceding)
        p.user.learn(ctx.last(), w)
        PredictionEngine.saveSoon()
    }

    override suspend fun notifySuggestionAccepted(subtype: Subtype, candidate: SuggestionCandidate) {
        flogDebug { candidate.toString() }
    }

    override suspend fun notifySuggestionReverted(subtype: Subtype, candidate: SuggestionCandidate) {
        flogDebug { candidate.toString() }
    }

    override suspend fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate): Boolean {
        // Long-press a suggestion: never suggest it again (until the user types it again).
        val p = predictor(subtype) ?: return false
        p.user.block(candidate.text.toString())
        PredictionEngine.saveSoon(force = true)
        return true
    }

    override suspend fun getListOfWords(subtype: Subtype): List<String> {
        val lang = PredictionEngine.languageFor(subtype.primaryLocale.language) ?: return emptyList()
        glideWords[lang]?.let { return it }
        val p = predictor(subtype) ?: return emptyList()
        val words = ArrayList<String>(GLIDE_WORDS)
        for (i in 0 until p.lexicon.size) {
            if (words.size >= GLIDE_WORDS) break
            val d = p.lexicon.display(i)
            if (p.lexicon.isOffensive(i) || d.length < 1 || !d.all { it.isLetter() || it == '\'' }) continue
            if (d.length > 1 && d.all { it.isUpperCase() }) continue
            words.add(d)
        }
        return words.also { glideWords[lang] = it }
    }

    override suspend fun getFrequencyForWord(subtype: Subtype, word: String): Double {
        val p = predictor(subtype) ?: return 0.0
        val i = p.lexicon.indexOf(word.lowercase(Locale.ROOT))
        val uses = p.user.unigram(word)
        val base = if (i >= 0) ((p.lexicon.logP(i) + 24f) / 24f).toDouble().coerceIn(0.0, 1.0) else 0.0
        return (base + 0.02 * uses.coerceAtMost(10)).coerceAtMost(1.0)
    }

    override suspend fun destroy() {
        PredictionEngine.saveSoon(force = true)
    }
}
