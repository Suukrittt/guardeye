package com.guardeye.app

/** Rule-based scam-wording check. NOT an AI model and NOT fact-checking. Same rules as the web app. */
data class Result(val category: String, val risk: String, val score: Int, val reasons: List<String>)

object Analyzer {
    private val rules = listOf(
        Triple(Regex("guarantee[d]?|risk[- ]free|assured returns?|\\b\\d+\\s?x returns?|double your money", RegexOption.IGNORE_CASE), 30, "Promises guaranteed or unusually high returns"),
        Triple(Regex("act now|limited (slots|time|seats)|hurry|last chance|expires (today|soon)|urgent", RegexOption.IGNORE_CASE), 15, "Uses urgency to pressure you"),
        Triple(Regex("\\botp\\b|password|\\bpin\\b|cvv|card number|aadhaar|bank details", RegexOption.IGNORE_CASE), 35, "Asks for private codes or financial details"),
        Triple(Regex("https?://\\S+|\\b(bit\\.ly|tinyurl|wa\\.me)\\S*", RegexOption.IGNORE_CASE), 10, "Contains a link - check where it really goes"),
        Triple(Regex("\\.(xyz|top|click|tk)\\b", RegexOption.IGNORE_CASE), 15, "Link uses a domain often seen in scams"),
        Triple(Regex("invest|crypto|bitcoin|trading|forex|lottery|prize|you (have )?won", RegexOption.IGNORE_CASE), 15, "Mentions investment, crypto or prizes"),
        Triple(Regex("(prime minister|minister|celebrity|ceo).{0,40}(endors|recommend|launch|scheme)|government[- ]backed", RegexOption.IGNORE_CASE), 20, "Claims a public figure or government endorses it"),
        Triple(Regex("forward (this )?to|share (this )?with (all|everyone)", RegexOption.IGNORE_CASE), 15, "Pushes you to forward it widely")
    )
    fun analyze(text: String): Result {
        val t = text.take(5000)
        val hit = rules.filter { it.first.containsMatchIn(t) }
        val s = hit.sumOf { it.second }.coerceAtMost(100)
        val cat = when { t.isBlank() -> "Unable to Determine"; s >= 50 -> "Possible Scam"; s >= 25 -> "Suspicious Content"; else -> "No Strong Warning Signals Detected" }
        val risk = when (cat) { "Possible Scam" -> "high"; "Suspicious Content" -> "medium"; else -> "low" }
        return Result(cat, risk, s, hit.map { it.third })
    }
}
