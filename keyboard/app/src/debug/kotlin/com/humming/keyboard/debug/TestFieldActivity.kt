/*
 * Humming (added, debug builds only): a chat-style screen with a focused message field, so the keyboard can be
 * shown and captured the way people see it in a messaging app.
 */
package com.humming.keyboard.debug

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class TestFieldActivity : Activity() {
    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val bg = if (night) Color.parseColor("#0B0C0F") else Color.parseColor("#F7F8FA")
        val fg = if (night) Color.parseColor("#F2F3F7") else Color.parseColor("#14161C")
        val other = if (night) Color.parseColor("#26282E") else Color.parseColor("#E9EBF0")
        window.statusBarColor = bg
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        root.addView(TextView(this).apply {
            text = "Maya"; setTextColor(fg); textSize = 20f; setPadding(dp(20), dp(18), dp(20), dp(12))
        })
        val chat = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.BOTTOM; setPadding(dp(14), 0, dp(14), dp(8)) }
        fun bubble(t: String, mine: Boolean) = TextView(this).apply {
            text = t; textSize = 16f
            setTextColor(if (mine) Color.WHITE else fg)
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                if (mine) { orientation = GradientDrawable.Orientation.TL_BR; colors = intArrayOf(Color.parseColor("#7B61FF"), Color.parseColor("#3D8BFF")) }
                else setColor(other)
            }
            setPadding(dp(14), dp(10), dp(14), dp(10))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = if (mine) Gravity.END else Gravity.START; topMargin = dp(6)
            }
        }
        chat.addView(bubble("Are we still on for dinner tonight?", false))
        chat.addView(bubble("Yes! 7:30 at the usual place", true))
        chat.addView(bubble("Perfect. Can you book a table for four?", false))
        root.addView(chat, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val field = EditText(this).apply {
            id = android.R.id.edit
            hint = "Message"
            textSize = 17f
            setTextColor(fg); setHintTextColor(Color.parseColor("#8A8F9C"))
            background = GradientDrawable().apply { cornerRadius = dp(24).toFloat(); setColor(other) }
            setPadding(dp(18), dp(12), dp(18), dp(12))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(12), dp(4), dp(12), dp(10))
            }
        }
        root.addView(field)
        setContentView(root)
        // Edge-to-edge (Android 15+): keep the title below the status bar and the field above the keyboard.
        root.setOnApplyWindowInsetsListener { v, insets ->
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
                val ime = insets.getInsets(android.view.WindowInsets.Type.ime())
                v.setPadding(0, bars.top, 0, maxOf(bars.bottom, ime.bottom))
            } else {
                @Suppress("DEPRECATION")
                v.setPadding(0, insets.systemWindowInsetTop, 0, insets.systemWindowInsetBottom)
            }
            insets
        }
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        field.requestFocus()
        field.postDelayed({
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
        }, 300)
    }
}
