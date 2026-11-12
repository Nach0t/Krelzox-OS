package dev.krelzox.store.domain

import dev.krelzox.store.model.CatalogApp
import java.text.Normalizer

/**
 * Local catalog search. Every word of the query must appear in the app's name, summary,
 * package name or categories; matches in the name rank highest. Accents and case are
 * ignored ("musica" finds "Música").
 */
object Search {

    private val MARKS = Regex("\\p{Mn}+")

    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(MARKS, "").lowercase().trim()

    fun search(apps: Collection<CatalogApp>, query: String, limit: Int = 200): List<CatalogApp> {
        val words = normalize(query).split(' ', '\t').filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        return apps.asSequence()
            .mapNotNull { app -> score(app, words)?.let { app to it } }
            .sortedWith(compareByDescending<Pair<CatalogApp, Int>> { it.second }.thenBy { it.first.name.lowercase() })
            .take(limit)
            .map { it.first }
            .toList()
    }

    private fun score(app: CatalogApp, words: List<String>): Int? {
        val name = normalize(app.name)
        val summary = normalize(app.summary)
        val packageName = app.packageName.lowercase()
        val categories = normalize(app.categories.joinToString(" "))
        var total = 0
        for (word in words) {
            total += when {
                name == word -> 100
                name.startsWith(word) -> 60
                name.split(' ').any { it.startsWith(word) } -> 45
                word in name -> 30
                word in categories -> 15
                word in summary -> 10
                word in packageName -> 5
                else -> return null
            }
        }
        if (app.hasTvAssets) total += 3
        return total
    }
}
