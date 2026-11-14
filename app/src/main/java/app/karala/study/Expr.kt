package app.karala.study

import kotlin.math.*

/**
 * Bilimsel hesap makinesi için ifade çözümleyici (özyinelemeli iniş).
 * Destek: + − × ÷ ^ % ! parantez, sin cos tan asin acos atan ln log sqrt abs, π, e, örtük çarpım (2π, 3(4+1)).
 */
class Expr(private val src: String, private val degrees: Boolean = true) {
    private var pos = 0

    class Error(msg: String) : Exception(msg)

    fun eval(): Double {
        val v = expr()
        skip()
        if (pos < src.length) throw Error("Beklenmeyen: '${src[pos]}'")
        if (v.isNaN()) throw Error("Tanımsız")
        if (v.isInfinite()) throw Error("Sonsuz")
        return v
    }

    private fun skip() { while (pos < src.length && src[pos].isWhitespace()) pos++ }
    private fun peek(): Char { skip(); return if (pos < src.length) src[pos] else '\u0000' }
    private fun eat(c: Char): Boolean { if (peek() == c) { pos++; return true }; return false }

    private fun expr(): Double {
        var v = term()
        while (true) {
            v = when {
                eat('+') -> v + term()
                eat('-') || eat('−') -> v - term()
                else -> return v
            }
        }
    }

    private fun term(): Double {
        var v = unary()
        while (true) {
            val c = peek()
            v = when {
                eat('*') || eat('×') -> v * unary()
                eat('/') || eat('÷') -> { val d = unary(); if (d == 0.0) throw Error("Sıfıra bölünmez"); v / d }
                // Örtük çarpım: 2π, 3(…), 2sin(…)
                c == '(' || c.isLetter() || c == 'π' || c == '√' -> v * unary()
                else -> return v
            }
        }
    }

    private fun unary(): Double = when {
        eat('-') || eat('−') -> -unary()
        eat('+') -> unary()
        else -> power()
    }

    private fun power(): Double {
        val base = postfix()
        return if (eat('^')) base.pow(unary()) else base // sağdan birleşmeli
    }

    private fun postfix(): Double {
        var v = primary()
        while (true) {
            v = when {
                eat('!') -> factorial(v)
                eat('%') -> v / 100.0
                else -> return v
            }
        }
    }

    private fun primary(): Double {
        val c = peek()
        if (eat('(')) { val v = expr(); if (!eat(')')) { /* eksik kapanışa izin ver */ }; return v }
        if (eat('π')) return PI
        if (eat('√')) return fn("sqrt", unary())
        if (c.isDigit() || c == '.' || c == ',') return number()
        if (c.isLetter()) {
            val start = pos
            while (pos < src.length && src[pos].isLetter()) pos++
            val name = src.substring(start, pos).lowercase()
            if (name == "pi") return PI
            if (name == "e") return E
            val arg = if (eat('(')) { val v = expr(); eat(')'); v } else unary()
            return fn(name, arg)
        }
        throw Error(if (c == '\u0000') "Eksik ifade" else "Beklenmeyen: '$c'")
    }

    private fun number(): Double {
        skip()
        val start = pos
        while (pos < src.length && (src[pos].isDigit() || src[pos] == '.' || src[pos] == ',')) pos++
        if (pos < src.length && (src[pos] == 'E') && pos + 1 < src.length && (src[pos + 1].isDigit() || src[pos + 1] == '-')) {
            pos += 2
            while (pos < src.length && src[pos].isDigit()) pos++
        }
        return src.substring(start, pos).replace(',', '.').toDoubleOrNull() ?: throw Error("Geçersiz sayı")
    }

    private fun rad(x: Double) = if (degrees) Math.toRadians(x) else x
    private fun deg(x: Double) = if (degrees) Math.toDegrees(x) else x

    private fun fn(name: String, x: Double): Double = when (name) {
        "sin" -> clean(sin(rad(x)))
        "cos" -> clean(cos(rad(x)))
        "tan" -> { if (abs(cos(rad(x))) < 1e-12) throw Error("Tanımsız"); clean(tan(rad(x))) }
        "asin" -> deg(asin(x)); "acos" -> deg(acos(x)); "atan" -> deg(atan(x))
        "ln" -> ln(x); "log" -> log10(x)
        "sqrt" -> { if (x < 0) throw Error("Negatif karekök"); sqrt(x) }
        "abs" -> abs(x)
        else -> throw Error("Bilinmeyen: $name")
    }

    private fun clean(v: Double) = if (abs(v) < 1e-12) 0.0 else v

    private fun factorial(v: Double): Double {
        if (v < 0 || v != floor(v) || v > 170) throw Error("Faktöriyel tanımsız")
        var r = 1.0; for (i in 2..v.toInt()) r *= i; return r
    }

    companion object {
        fun evaluate(s: String, degrees: Boolean = true): Double = Expr(s, degrees).eval()

        /** Sonucu okunaklı biçimlendirir: gereksiz sıfırlar yok, çok büyük/küçükte bilimsel gösterim. */
        fun format(v: Double): String {
            if (v == 0.0) return "0"
            val a = abs(v)
            if (a >= 1e12 || a < 1e-9) return "%.6E".format(java.util.Locale.US, v).replace(Regex("0+E"), "E").replace(".E", "E").replace("E+", "E")
            val r = java.math.BigDecimal(v).round(java.math.MathContext(12)).stripTrailingZeros()
            return r.toPlainString()
        }
    }
}
