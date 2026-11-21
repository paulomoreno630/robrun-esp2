package com.robrun.esp

import kotlin.math.pow

data class RollResult(
    val color: String,
    val number: Int?,
    val ts: Long = System.currentTimeMillis()
)

data class ColorProb(
    val name: String,
    val hex: Int,          // ARGB
    val prob: Float,
    val rawCount: Int
)

data class NumberStats(
    val total: Int,
    val odds: Int,
    val evens: Int,
    val zeros: Int,
    val hot: List<Int>,
    val cold: List<Int>
)

data class Streak(val color: String, val count: Int)

class ProbabilityEngine {

    companion object {
        val COLOR_MAP: Map<String, Int> = mapOf(
            "Vermelho" to 0xFFFF4757.toInt(),
            "Preto"    to 0xFF58A6FF.toInt(),
            "Verde"    to 0xFF3FB950.toInt(),
            "Branco"   to 0xFFC9D1D9.toInt(),
            "Roxo"     to 0xFFBC8CFF.toInt(),
            "Laranja"  to 0xFFF0883E.toInt(),
        )

        // Keywords that map to canonical color names — extend as needed for Rob Run
        val COLOR_KEYWORDS: Map<String, String> = mapOf(
            "vermelho" to "Vermelho", "red" to "Vermelho", "verm" to "Vermelho",
            "preto"    to "Preto",    "black" to "Preto",   "pto" to "Preto",
            "verde"    to "Verde",    "green" to "Verde",    "vd" to "Verde",
            "branco"   to "Branco",   "white" to "Branco",
            "roxo"     to "Roxo",     "purple" to "Roxo",   "lilas" to "Roxo",
            "laranja"  to "Laranja",  "orange" to "Laranja",
        )
    }

    val history = mutableListOf<RollResult>()
    var windowSize: Int = 30
    var recencyWeight: Boolean = true
    var dueBias: Boolean = true
    var enabledColors: Set<String> = setOf("Vermelho", "Preto", "Verde")

    fun addResult(result: RollResult) {
        history.add(result)
        if (history.size > 500) history.removeAt(0) // cap memory
    }

    fun undoLast(): RollResult? =
        if (history.isNotEmpty()) history.removeAt(history.lastIndex) else null

    private fun getWindow(): List<RollResult> =
        history.takeLast(windowSize)

    fun colorProbabilities(): List<ColorProb> {
        val window = getWindow()
        val colors = enabledColors.toList()
        if (window.isEmpty()) {
            return colors.map { ColorProb(it, COLOR_MAP[it] ?: 0xFF888888.toInt(), 1f / colors.size, 0) }
        }

        val weighted = colors.associateWith { 0.0 }.toMutableMap()

        if (recencyWeight) {
            window.forEachIndexed { i, entry ->
                if (entry.color in weighted) {
                    val w = 1.08.pow(i.toDouble()) // exponential, oldest=lowest
                    weighted[entry.color] = weighted[entry.color]!! + w
                }
            }
        } else {
            window.forEach { entry ->
                if (entry.color in weighted) weighted[entry.color] = weighted[entry.color]!! + 1.0
            }
        }

        if (dueBias) {
            colors.forEach { color ->
                val lastIdx = window.reversed().indexOfFirst { it.color == color }
                when {
                    lastIdx == -1 -> weighted[color] = weighted[color]!! + window.size * 0.4
                    lastIdx > 5   -> weighted[color] = weighted[color]!! + lastIdx * 0.08
                }
            }
        }

        val total = weighted.values.sum().coerceAtLeast(0.0001)
        val rawCounts = colors.associateWith { c -> window.count { it.color == c } }

        return colors.map { c ->
            ColorProb(c, COLOR_MAP[c] ?: 0xFF888888.toInt(), (weighted[c]!! / total).toFloat(), rawCounts[c] ?: 0)
        }.sortedByDescending { it.prob }
    }

    fun numberStats(): NumberStats? {
        val nums = history.mapNotNull { it.number }
        if (nums.isEmpty()) return null

        val total = nums.size
        val freq  = mutableMapOf<Int, Int>()
        nums.forEach { freq[it] = (freq[it] ?: 0) + 1 }

        val lastSeen = mutableMapOf<Int, Int>()
        nums.reversed().forEachIndexed { i, n -> if (!lastSeen.containsKey(n)) lastSeen[n] = i }

        val hot  = freq.entries.sortedByDescending { it.value }.take(5).map { it.key }
        val cold = lastSeen.entries.sortedByDescending { it.value }.take(5).map { it.key }

        return NumberStats(
            total = total,
            odds  = nums.count { it > 0 && it % 2 != 0 },
            evens = nums.count { it > 0 && it % 2 == 0 },
            zeros = nums.count { it == 0 },
            hot   = hot,
            cold  = cold
        )
    }

    fun currentStreak(): Streak? {
        if (history.isEmpty()) return null
        val last = history.last().color
        var count = 0
        for (i in history.indices.reversed()) {
            if (history[i].color == last) count++ else break
        }
        return Streak(last, count)
    }

    fun topPrediction(): ColorProb? = colorProbabilities().firstOrNull()

    /** Parse OCR text to find a color and optional number. Returns null if no match. */
    fun parseOcrText(text: String): RollResult? {
        val lower = text.lowercase().replace(Regex("[^a-záéíóúâêîôûãõç0-9\\s]"), " ")
        
        val color = COLOR_KEYWORDS.entries.firstOrNull { (kw, _) ->
            Regex("\\b${Regex.escape(kw)}\\b").containsMatchIn(lower)
        }?.value ?: return null

        val numberMatch = Regex("\\b([0-9]{1,2})\\b").findAll(lower)
            .map { it.value.toInt() }
            .firstOrNull { it in 0..36 }

        return RollResult(color, numberMatch)
    }
}
