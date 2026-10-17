package com.humming.keyboard

/**
 * Which text fields must never keep anything from a dictation, whatever the incognito settings say: password fields
 * (text, visible, web and numeric passwords) and fields whose app asked for no personalised learning.
 * Values are android.text.InputType / EditorInfo constants, inlined so this stays a plain JVM class.
 */
object FieldPrivacy {
    private const val TYPE_MASK_CLASS = 0x0000000f
    private const val TYPE_MASK_VARIATION = 0x00000ff0
    private const val TYPE_CLASS_TEXT = 0x00000001
    private const val TYPE_CLASS_NUMBER = 0x00000002
    private const val TYPE_TEXT_VARIATION_PASSWORD = 0x00000080
    private const val TYPE_TEXT_VARIATION_VISIBLE_PASSWORD = 0x00000090
    private const val TYPE_TEXT_VARIATION_WEB_PASSWORD = 0x000000e0
    private const val TYPE_NUMBER_VARIATION_PASSWORD = 0x00000010
    private const val IME_FLAG_NO_PERSONALIZED_LEARNING = 0x01000000

    fun isPassword(inputType: Int): Boolean {
        val variation = inputType and TYPE_MASK_VARIATION
        return when (inputType and TYPE_MASK_CLASS) {
            TYPE_CLASS_TEXT -> variation == TYPE_TEXT_VARIATION_PASSWORD ||
                variation == TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == TYPE_TEXT_VARIATION_WEB_PASSWORD
            TYPE_CLASS_NUMBER -> variation == TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }

    fun forcesIncognito(inputType: Int, imeOptions: Int): Boolean =
        isPassword(inputType) || (imeOptions and IME_FLAG_NO_PERSONALIZED_LEARNING) != 0
}
