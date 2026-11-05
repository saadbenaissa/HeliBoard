// SPDX-License-Identifier: GPL-3.0-only
// Fork change: iOS-style calculator suggestion. Typing e.g. "12+3*2=" shows "12+3*2=18" in the suggestion strip,
// tapping it inserts the result directly after the "=".
package helium314.keyboard.latin

import android.text.InputType
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.view.isGone
import helium314.keyboard.event.HapticEvent
import helium314.keyboard.keyboard.KeyboardTypeface
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.databinding.ClipboardSuggestionBinding
import helium314.keyboard.latin.utils.InputTypeUtils
import java.math.BigDecimal
import java.math.MathContext

object Calculator {
    private const val MAX_LOOKBACK = 200
    private const val ALLOWED_CHARS = "0123456789.+-*/×÷xX()% "
    private const val OPERATORS = "+-*/×÷xX%"
    private val DIVISION_PRECISION = MathContext.DECIMAL64 // 16 significant digits, only needed for divisions like 1/3

    /** A calculation found before the cursor: the expression as typed (without "=") and its result. */
    class Calculation(val expression: String, val result: String)

    /** Creates the suggestion strip view with the calculation, or returns null if there is nothing to calculate. */
    @JvmStatic
    fun getSuggestionView(latinIME: LatinIME, editorInfo: EditorInfo?, parent: ViewGroup?): View? {
        if (parent == null) return null
        val inputType = editorInfo?.inputType ?: InputType.TYPE_NULL
        if (InputTypeUtils.isAnyPasswordInputType(inputType)) return null
        val textBeforeCursor = latinIME.mInputLogic.mConnection.getTextBeforeCursor(MAX_LOOKBACK, 0)
        val calculation = calculate(textBeforeCursor) ?: return null

        // reuse the layout of the clipboard suggestion, without its close button
        val binding = ClipboardSuggestionBinding.inflate(LayoutInflater.from(latinIME), parent, false)
        binding.clipboardSuggestionClose.isGone = true
        val textView = binding.clipboardSuggestionText
        KeyboardTypeface.applyToTextView(textView)
        textView.text = "${calculation.expression}=${calculation.result}"
        textView.ellipsize = TextUtils.TruncateAt.START // if it's too long, cut off the start, not the result
        textView.setOnClickListener {
            latinIME.onTextInput(calculation.result)
            AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, it, HapticEvent.KEY_PRESS)
            binding.root.isGone = true
        }
        val colors = latinIME.mSettings.current.mColors
        textView.setTextColor(colors.get(ColorType.KEY_TEXT))
        colors.setBackground(binding.root, ColorType.CLIPBOARD_SUGGESTION_BACKGROUND)
        return binding.root
    }

    /** Returns only the result, e.g. "1+1=" gives "2", or null if there is no calculation. */
    fun resultFor(textBeforeCursor: CharSequence?): String? = calculate(textBeforeCursor)?.result

    /** Finds a calculation followed by "=" at the end of the text and calculates it, or returns null. */
    fun calculate(textBeforeCursor: CharSequence?): Calculation? {
        if (textBeforeCursor.isNullOrEmpty() || textBeforeCursor.last() != '=') return null
        // walk back from the "=" as long as the characters can be part of a calculation
        var start = textBeforeCursor.length - 1
        while (start > 0 && textBeforeCursor[start - 1] in ALLOWED_CHARS) start--
        // if the calculation reaches the start of what we read, its beginning may be cut off: better show nothing than a wrong result
        if (start == 0 && textBeforeCursor.length >= MAX_LOOKBACK) return null
        val expression = textBeforeCursor.substring(start, textBeforeCursor.length - 1)
            .dropWhile { !(it.isDigit() || it == '(' || it == '-' || it == '.') } // e.g. the "x" of "max" in "max2+2="
            .trim()
        if (expression.none { it.isDigit() }) return null
        if (expression.drop(1).none { it in OPERATORS }) return null // a plain number like "5=" or "-5=" is no calculation
        val value = try {
            Parser(expression).parse()
        } catch (e: Exception) {
            return null // invalid input, or division by zero
        }
        return Calculation(expression, format(value))
    }

    /** Drops trailing zeros, e.g. 2.50 gives 2.5 and 4.0 gives 4, and never uses scientific notation. */
    private fun format(value: BigDecimal): String {
        val stripped = value.stripTrailingZeros()
        return if (stripped.signum() == 0) "0" else stripped.toPlainString()
    }

    /**
     * Small recursive descent parser, following the usual order of operations.
     * It uses BigDecimal instead of Double, so results are exact, also for very long numbers and things like 0.1+0.2.
     * expression = term (("+" | "-") term)*
     * term       = factor (("*" | "/" | "×" | "÷" | "x") factor)*
     * factor     = ("-" | "+") factor | primary "%"*
     * primary    = number | "(" expression ")"
     */
    private class Parser(private val text: String) {
        private var pos = 0

        fun parse(): BigDecimal {
            val result = expression()
            skipSpaces()
            if (pos != text.length) throw IllegalArgumentException("unexpected '${text[pos]}'")
            return result
        }

        private fun expression(): BigDecimal {
            var result = term()
            while (true) {
                result = when (peek()) {
                    '+' -> { pos++; result + term() }
                    '-' -> { pos++; result - term() }
                    else -> return result
                }
            }
        }

        private fun term(): BigDecimal {
            var result = factor()
            while (true) {
                result = when (peek()) {
                    '*', '×', 'x', 'X' -> { pos++; result * factor() }
                    '/', '÷' -> { pos++; result.divide(factor(), DIVISION_PRECISION) } // throws for division by zero
                    else -> return result
                }
            }
        }

        private fun factor(): BigDecimal {
            when (peek()) {
                '-' -> { pos++; return -factor() }
                '+' -> { pos++; return factor() }
            }
            var result = primary()
            while (peek() == '%') {
                pos++
                result = result.movePointLeft(2) // divide by 100
            }
            return result
        }

        private fun primary(): BigDecimal {
            if (peek() == '(') {
                pos++
                val result = expression()
                if (peek() != ')') throw IllegalArgumentException("missing ')'")
                pos++
                return result
            }
            skipSpaces()
            val start = pos
            while (pos < text.length && (text[pos].isDigit() || text[pos] == '.')) pos++
            return BigDecimal(text.substring(start, pos)) // throws for "" or "1.2.3"
        }

        /** Returns the next non-space character without consuming it, or null at the end. */
        private fun peek(): Char? {
            skipSpaces()
            return text.getOrNull(pos)
        }

        private fun skipSpaces() {
            while (pos < text.length && text[pos] == ' ') pos++
        }
    }
}
