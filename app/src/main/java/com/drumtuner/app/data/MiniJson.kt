package com.drumtuner.app.data

/**
 * Tiny dependency-free JSON reader/writer (objects, arrays, strings, numbers, booleans, null).
 * Kept in pure Kotlin so the preset backup format is unit-testable on the JVM.
 *
 * Mapping: object ↔ Map<String, Any?>, array ↔ List<Any?>, number → Double.
 */
object MiniJson {

    class ParseException(message: String) : Exception(message)

    fun stringify(value: Any?, indent: Int = 2): String = StringBuilder().also { write(it, value, indent, 0) }.toString()

    private fun write(sb: StringBuilder, value: Any?, indent: Int, level: Int) {
        when (value) {
            null -> sb.append("null")
            is String -> writeString(sb, value)
            is Boolean -> sb.append(value)
            is Int, is Long -> sb.append(value.toString())
            is Number -> {
                val d = value.toDouble()
                require(!d.isNaN() && !d.isInfinite()) { "JSON cannot encode $d" }
                if (d == Math.rint(d) && kotlin.math.abs(d) < 1e15) sb.append(d.toLong()) else sb.append(d)
            }
            is Map<*, *> -> {
                if (value.isEmpty()) { sb.append("{}"); return }
                sb.append('{')
                var first = true
                for ((k, v) in value) {
                    if (!first) sb.append(',')
                    first = false
                    newline(sb, indent, level + 1)
                    writeString(sb, k.toString())
                    sb.append(if (indent > 0) ": " else ":")
                    write(sb, v, indent, level + 1)
                }
                newline(sb, indent, level)
                sb.append('}')
            }
            is List<*> -> {
                if (value.isEmpty()) { sb.append("[]"); return }
                sb.append('[')
                value.forEachIndexed { i, v ->
                    if (i > 0) sb.append(',')
                    newline(sb, indent, level + 1)
                    write(sb, v, indent, level + 1)
                }
                newline(sb, indent, level)
                sb.append(']')
            }
            else -> writeString(sb, value.toString())
        }
    }

    private fun newline(sb: StringBuilder, indent: Int, level: Int) {
        if (indent <= 0) return
        sb.append('\n')
        repeat(indent * level) { sb.append(' ') }
    }

    private fun writeString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (c < ' ') sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
            }
        }
        sb.append('"')
    }

    fun parse(text: String): Any? {
        val p = Parser(text)
        p.skipWs()
        val v = p.value()
        p.skipWs()
        if (!p.atEnd()) throw ParseException("Trailing characters at ${p.pos}")
        return v
    }

    private class Parser(val s: String) {
        var pos = 0

        fun atEnd() = pos >= s.length

        fun skipWs() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }

        fun value(): Any? {
            skipWs()
            if (atEnd()) throw ParseException("Unexpected end of input")
            return when (val c = s[pos]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c.isDigit()) num() else throw ParseException("Unexpected '$c' at $pos")
            }
        }

        private fun literal(word: String, v: Any?): Any? {
            if (!s.startsWith(word, pos)) throw ParseException("Expected $word at $pos")
            pos += word.length
            return v
        }

        private fun obj(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>()
            pos++ // {
            skipWs()
            if (peek() == '}') { pos++; return m }
            while (true) {
                skipWs()
                if (peek() != '"') throw ParseException("Expected key at $pos")
                val k = str()
                skipWs()
                expect(':')
                m[k] = value()
                skipWs()
                when (peek()) {
                    ',' -> pos++
                    '}' -> { pos++; return m }
                    else -> throw ParseException("Expected , or } at $pos")
                }
            }
        }

        private fun arr(): List<Any?> {
            val l = ArrayList<Any?>()
            pos++ // [
            skipWs()
            if (peek() == ']') { pos++; return l }
            while (true) {
                l += value()
                skipWs()
                when (peek()) {
                    ',' -> pos++
                    ']' -> { pos++; return l }
                    else -> throw ParseException("Expected , or ] at $pos")
                }
            }
        }

        private fun str(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                if (atEnd()) throw ParseException("Unterminated string")
                val c = s[pos++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (atEnd()) throw ParseException("Bad escape")
                        when (val e = s[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > s.length) throw ParseException("Bad unicode escape")
                                sb.append(s.substring(pos, pos + 4).toInt(16).toChar())
                                pos += 4
                            }
                            else -> throw ParseException("Bad escape \\$e")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun num(): Double {
            val start = pos
            if (peek() == '-') pos++
            while (pos < s.length && (s[pos].isDigit() || s[pos] in ".eE+-")) pos++
            return s.substring(start, pos).toDoubleOrNull() ?: throw ParseException("Bad number at $start")
        }

        private fun peek(): Char = if (atEnd()) '\u0000' else s[pos]

        private fun expect(c: Char) {
            if (peek() != c) throw ParseException("Expected '$c' at $pos")
            pos++
        }
    }
}
