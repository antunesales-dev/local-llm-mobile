package com.localllm.chat.llm.agent.tools

import com.localllm.chat.llm.agent.Tool
import com.localllm.chat.llm.agent.ToolParameter
import com.localllm.chat.llm.agent.ToolResult
import org.json.JSONObject

class CalculatorTool : Tool {
    override val name = "calculator"
    override val description = "Evaluate a mathematical expression and return the result."
    override val parameters = listOf(
        ToolParameter("expression", "string", "Mathematical expression to evaluate, e.g. '2 + 3 * 4'"),
    )

    override suspend fun execute(args: JSONObject): ToolResult {
        val expression = args.optString("expression", "")
        if (expression.isBlank()) {
            return ToolResult("Error: empty expression", isError = true)
        }

        return try {
            val sanitized = expression.replace(Regex("[^0-9+\\-*/.() ]"), "")
            val result = Parser(sanitized.replace(" ", "")).parseExpression()
            ToolResult(formatResult(result))
        } catch (e: Exception) {
            ToolResult("Error evaluating expression: ${e.message}", isError = true)
        }
    }

    private fun formatResult(value: Double): String {
        return if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            value.toString()
        }
    }

    /** Recursive descent parser respecting operator precedence: +- then * / then unary then parens/numbers. */
    private class Parser(private val input: String) {
        private var pos = 0

        fun parseExpression(): Double {
            val result = parseAddSub()
            if (pos < input.length) throw IllegalArgumentException("Unexpected character: '${input[pos]}'")
            return result
        }

        private fun parseAddSub(): Double {
            var left = parseMulDiv()
            while (pos < input.length && input[pos] in "+-") {
                val op = input[pos++]
                val right = parseMulDiv()
                left = if (op == '+') left + right else left - right
            }
            return left
        }

        private fun parseMulDiv(): Double {
            var left = parseUnary()
            while (pos < input.length && input[pos] in "*/") {
                val op = input[pos++]
                val right = parseUnary()
                if (op == '/') {
                    if (right == 0.0) throw ArithmeticException("Division by zero")
                    left /= right
                } else {
                    left *= right
                }
            }
            return left
        }

        private fun parseUnary(): Double {
            if (pos < input.length && input[pos] == '-') {
                pos++
                return -parseAtom()
            }
            return parseAtom()
        }

        private fun parseAtom(): Double {
            if (pos < input.length && input[pos] == '(') {
                pos++ // skip '('
                val result = parseAddSub()
                if (pos < input.length && input[pos] == ')') pos++ // skip ')'
                return result
            }
            return parseNumber()
        }

        private fun parseNumber(): Double {
            val start = pos
            while (pos < input.length && (input[pos].isDigit() || input[pos] == '.')) {
                pos++
            }
            if (start == pos) throw IllegalArgumentException("Expected number at position $pos")
            return input.substring(start, pos).toDouble()
        }
    }
}
