// SPDX-License-Identifier: GPL-3.0-only
// Fork change: tests for the calculator suggestion.
package helium314.keyboard.latin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CalculatorTest {
    @Test fun basicOperations() {
        assertEquals("2", Calculator.resultFor("1+1="))
        assertEquals("6", Calculator.resultFor("8-2="))
        assertEquals("12", Calculator.resultFor("3*4="))
        assertEquals("12", Calculator.resultFor("3x4="))
        assertEquals("12", Calculator.resultFor("3×4="))
        assertEquals("2.5", Calculator.resultFor("5/2="))
        assertEquals("2.5", Calculator.resultFor("5÷2="))
    }

    @Test fun orderOfOperations() {
        assertEquals("18", Calculator.resultFor("12+3*2="))
        assertEquals("30", Calculator.resultFor("(12+3)*2="))
        assertEquals("-6", Calculator.resultFor("5-11="))
        assertEquals("-1", Calculator.resultFor("-3+2="))
    }

    @Test fun decimalsAndPercent() {
        assertEquals("0.3", Calculator.resultFor("0.1+0.2="))
        assertEquals("0.75", Calculator.resultFor(".5+.25="))
        assertEquals("0.5", Calculator.resultFor("50%="))
        assertEquals("15", Calculator.resultFor("30*50%="))
    }

    @Test fun longNumbersAreExact() {
        val a = "1".repeat(45)
        val b = "1".repeat(56)
        assertEquals("1".repeat(11) + "2".repeat(45), Calculator.resultFor("$a+$b="))
        assertEquals("1".repeat(30) + "0", Calculator.resultFor("${"1".repeat(30)}*10="))
        assertEquals("0.3333333333333333", Calculator.resultFor("1/3="))
    }

    @Test fun expressionForDisplay() {
        assertEquals("12+3*2", Calculator.calculate("so 12+3*2=")?.expression)
    }

    @Test fun tooLongIsIgnoredInsteadOfWrong() {
        // longer than what is read before the cursor, so the start could be cut off
        assertNull(Calculator.resultFor("1".repeat(150) + "+" + "1".repeat(60) + "="))
    }

    @Test fun calculationInsideSentence() {
        assertEquals("4", Calculator.resultFor("so that is 2+2="))
        assertEquals("4", Calculator.resultFor("max2+2="))
        assertEquals("4", Calculator.resultFor("2 + 2 ="))
    }

    @Test fun noCalculation() {
        assertNull(Calculator.resultFor("1+1"))      // no "="
        assertNull(Calculator.resultFor("1+1= "))    // something after "="
        assertNull(Calculator.resultFor("5="))       // plain number
        assertNull(Calculator.resultFor("-5="))
        assertNull(Calculator.resultFor("hello="))
        assertNull(Calculator.resultFor("1/0="))     // division by zero
        assertNull(Calculator.resultFor("1+="))      // incomplete
        assertNull(Calculator.resultFor("1.2.3+1=")) // invalid number
        assertNull(Calculator.resultFor("(1+2="))    // missing ")"
        assertNull(Calculator.resultFor(""))
        assertNull(Calculator.resultFor(null))
    }
}
