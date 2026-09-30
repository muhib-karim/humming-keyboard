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
import dev.patrickgold.florisboard.appContext
import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.editor.EditorContent
import dev.patrickgold.florisboard.ime.nlp.SpellingProvider
import dev.patrickgold.florisboard.ime.nlp.SpellingResult
import dev.patrickgold.florisboard.ime.nlp.SuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.SuggestionProvider
import dev.patrickgold.florisboard.ime.nlp.WordSuggestionCandidate
import com.humming.keyboard.NextWords
import com.humming.keyboard.TypingAssist
import java.io.File
import dev.patrickgold.florisboard.lib.devtools.flogDebug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.florisboard.lib.android.readText
import org.florisboard.lib.kotlin.guardedByLock

class LatinLanguageProvider(context: Context) : SpellingProvider, SuggestionProvider {
    companion object {
        // Default user ID used for all subtypes, unless otherwise specified.
        // See `ime/core/Subtype.kt` Line 210 and 211 for the default usage
        const val ProviderId = "org.florisboard.nlp.providers.latin"
    }

    private val appContext by context.appContext()

    private val wordData = guardedByLock { mutableMapOf<String, Int>() }
    private val wordDataSerializer = MapSerializer(String.serializer(), Int.serializer())

    override val providerId = ProviderId

    override suspend fun create() {
        // Here we initialize our provider, set up all things which are not language dependent.
    }

    override suspend fun preload(subtype: Subtype) = withContext(Dispatchers.IO) {
        // Here we have the chance to preload dictionaries and prepare a neural network for a specific language.
        // Is kept in sync with the active keyboard subtype of the user, however a new preload does not necessary mean
        // the previous language is not needed anymore (e.g. if the user constantly switches between two subtypes)

        // To read a file from the APK assets the following methods can be used:
        // appContext.assets.open()
        // appContext.assets.reader()
        // appContext.assets.bufferedReader()
        // appContext.assets.readText()
        // To copy an APK file/dir to the file system cache (appContext.cacheDir), the following methods are available:
        // appContext.assets.copy()
        // appContext.assets.copyRecursively()

        // The subtype we get here contains a lot of data, however we are only interested in subtype.primaryLocale and
        // subtype.secondaryLocales.

        wordData.withLock { wordData ->
            if (wordData.isEmpty()) {
                // Here we use readText() because the test dictionary is a json dictionary
                val rawData = appContext.assets.readText("ime/dict/data.json")
                val jsonData = Json.decodeFromString(wordDataSerializer, rawData)
                wordData.putAll(jsonData)
            }
        }
        // Humming: real word list (AOSP LatinIME en_US, 57k words) for completions, corrections and autocorrect,
        // and on-device learned next words.
        TypingAssist.load(appContext)
        nextWords()
        Unit
    }

    @Volatile private var lastPrev: String? = null

    // Humming (added): on-device next-word model, learned from accepted words.
    @Volatile private var nextWordsInstance: NextWords? = null
    private fun nextWords(): NextWords = nextWordsInstance ?: synchronized(this) {
        nextWordsInstance ?: NextWords(File(appContext.filesDir, "next_words.tsv"), NextWords.loadSeed(appContext))
            .also { nextWordsInstance = it }
    }

    override suspend fun spell(
        subtype: Subtype,
        word: String,
        precedingWords: List<String>,
        followingWords: List<String>,
        maxSuggestionCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean,
    ): SpellingResult {
        return when (word.lowercase()) {
            // Use typo for typing errors
            "typo" -> SpellingResult.typo(arrayOf("typo1", "typo2", "typo3"))
            // Use grammar error if the algorithm can detect this. On Android 11 and lower grammar errors are visually
            // marked as typos due to a lack of support
            "gerror" -> SpellingResult.grammarError(arrayOf("grammar1", "grammar2", "grammar3"))
            // Use valid word for valid input
            else -> SpellingResult.validWord()
        }
    }

    override suspend fun suggest(
        subtype: Subtype,
        content: EditorContent,
        maxCandidateCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean,
    ): List<SuggestionCandidate> {
        // Humming: the upstream provider returned no suggestions. This fills the suggestion strip:
        // - while typing a word: the typed word when it is a real word, keyboard-aware corrections and completions;
        //   a close correction for a non-word is marked for autocommit (autocorrect on space);
        // - after a space: next-word predictions (learned on the device first, then a small seed list).
        val ta = TypingAssist.loaded() ?: withContext(Dispatchers.IO) { TypingAssist.load(appContext) }
        val n = maxCandidateCount.coerceIn(1, 3)
        val word = content.composingText.ifBlank { content.currentWordText }
        if (word.isBlank()) {
            if (isPrivateSession) return emptyList()
            val prev = NextWords.previousWord(content.textBeforeSelection)
            if (prev.isEmpty()) return emptyList()
            val caps = content.textBeforeSelection.trimEnd().let { it.isEmpty() || it.last() in ".!?" }
            return nextWords().predict(prev, n).map {
                WordSuggestionCandidate(
                    text = if (caps) it.replaceFirstChar { c -> c.titlecase() } else it,
                    confidence = 0.5,
                    isEligibleForAutoCommit = false,
                    sourceProvider = this,
                )
            }
        }
        if (!word.all { it.isLetter() || it == '\'' }) return emptyList()
        lastPrev = if (isPrivateSession) null else NextWords.previousWord(content.textBeforeSelection.dropLast(word.length)).ifEmpty { null }
        val out = LinkedHashMap<String, WordSuggestionCandidate>()
        val auto = ta.autocorrect(word)
        if (auto != null) {
            out[auto.lowercase()] = WordSuggestionCandidate(auto, confidence = 0.9, isEligibleForAutoCommit = true, sourceProvider = this)
        }
        val isWord = ta.isWord(word)
        val completions = ta.completions(word, n)
        val corrections = if (isWord) emptyList() else ta.corrections(word, n)
        val ranked = (corrections + completions).sortedBy { it.score }
        for (c in ranked) {
            val w = TypingAssist.matchCase(c.word, word)
            out.putIfAbsent(w.lowercase(), WordSuggestionCandidate(w, confidence = 0.6, sourceProvider = this))
            if (out.size >= n) break
        }
        // Middle slot convention (like Gboard): the literal typed word stays reachable when it is not the top pick.
        val list = out.values.take(n).toMutableList()
        if (list.none { it.text.toString().equals(word, ignoreCase = true) }) {
            val typed = WordSuggestionCandidate(word, secondaryText = null, confidence = 0.4, sourceProvider = this)
            if (list.size >= n) list.removeAt(list.lastIndex)
            list.add(0, typed)
        }
        if (dev.patrickgold.florisboard.BuildConfig.DEBUG) {
            android.util.Log.i("HUMMING", "HUMMING_SUGGEST word=$word -> ${list.joinToString("|") { it.text }}")
        }
        return list
    }

    override suspend fun notifySuggestionAccepted(subtype: Subtype, candidate: SuggestionCandidate) {
        // We can use flogDebug, flogInfo, flogWarning and flogError for debug logging, which is a wrapper for Logcat
        flogDebug { candidate.toString() }
        // Humming: learn "previous word -> accepted word" on the device.
        lastPrev?.let { prev -> runCatching { nextWords().learn(prev, candidate.text.toString()); nextWords().save() } }
    }

    override suspend fun notifySuggestionReverted(subtype: Subtype, candidate: SuggestionCandidate) {
        flogDebug { candidate.toString() }
    }

    override suspend fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate): Boolean {
        flogDebug { candidate.toString() }
        return false
    }

    override suspend fun getListOfWords(subtype: Subtype): List<String> {
        return wordData.withLock { it.keys.toList() }
    }

    override suspend fun getFrequencyForWord(subtype: Subtype, word: String): Double {
        return wordData.withLock { it.getOrDefault(word, 0) / 255.0 }
    }

    override suspend fun destroy() {
        // Here we have the chance to de-allocate memory and finish our work. However this might never be called if
        // the app process is killed (which will most likely always be the case).
    }
}
