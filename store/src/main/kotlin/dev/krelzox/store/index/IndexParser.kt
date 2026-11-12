package dev.krelzox.store.index

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import dev.krelzox.store.model.ApkVersion
import dev.krelzox.store.model.CatalogApp
import dev.krelzox.store.model.RepoIndex
import dev.krelzox.store.model.RepoInfo
import java.io.Reader

/**
 * Streaming reader for F-Droid "index-v1" JSON. The full index is ~75 MB, so it is read
 * token by token and only the fields the store shows are kept, for the [locales] the
 * user prefers (the others are skipped without being materialized).
 *
 * The index must already be signature-verified ([IndexVerifier]) before parsing.
 */
class IndexParser(
    private val repoId: String,
    /**
     * Configured repository address. Icons, screenshots and APKs are always resolved
     * against it, never against the address the index itself declares.
     */
    private val address: String,
    /** Preferred locales, best first, e.g. ["es-ES", "es", "en-US", "en"]. */
    private val locales: List<String>,
) {

    fun parse(reader: Reader): RepoIndex {
        var repo: RepoInfo? = null
        val apps = ArrayList<AppDraft>()
        var packages: Map<String, List<ApkVersion>> = emptyMap()

        JsonReader(reader).use { json ->
            json.beginObject()
            while (json.hasNext()) {
                when (json.nextName()) {
                    "repo" -> repo = readRepo(json)
                    "apps" -> {
                        json.beginArray()
                        while (json.hasNext()) readApp(json)?.let(apps::add)
                        json.endArray()
                    }
                    "packages" -> packages = readPackages(json)
                    else -> json.skipValue()
                }
            }
            json.endObject()
        }

        val info = repo ?: throw IndexFormatException("Index has no repo section")
        val base = address.trimEnd('/')
        val catalog = apps.mapNotNull { draft ->
            val versions = packages[draft.packageName].orEmpty().sortedByDescending { it.versionCode }
            if (versions.isEmpty()) null else draft.toCatalogApp(repoId, base, versions)
        }
        return RepoIndex(info, catalog)
    }

    private fun readRepo(json: JsonReader): RepoInfo {
        var name = ""
        var address = ""
        var timestamp = 0L
        var description = ""
        json.beginObject()
        while (json.hasNext()) {
            when (json.nextName()) {
                "name" -> name = json.nextStringOrNull().orEmpty()
                "address" -> address = json.nextStringOrNull().orEmpty()
                "timestamp" -> timestamp = json.nextLong()
                "description" -> description = json.nextStringOrNull().orEmpty()
                else -> json.skipValue()
            }
        }
        json.endObject()
        return RepoInfo(name, address, timestamp, description)
    }

    private fun readApp(json: JsonReader): AppDraft? {
        val draft = AppDraft()
        json.beginObject()
        while (json.hasNext()) {
            when (json.nextName()) {
                "packageName" -> draft.packageName = json.nextStringOrNull().orEmpty()
                "name" -> draft.name = json.nextStringOrNull()
                "summary" -> draft.summary = json.nextStringOrNull()
                "description" -> draft.description = json.nextStringOrNull()
                "categories" -> draft.categories = json.readStringList()
                "license" -> draft.license = json.nextStringOrNull().orEmpty()
                "authorName" -> draft.author = json.nextStringOrNull()
                "webSite" -> draft.webSite = json.nextStringOrNull()
                "sourceCode" -> draft.sourceCode = json.nextStringOrNull()
                "issueTracker" -> draft.issueTracker = json.nextStringOrNull()
                "changelog" -> draft.changelog = json.nextStringOrNull()
                "added" -> draft.added = json.nextLong()
                "lastUpdated" -> draft.lastUpdated = json.nextLong()
                "antiFeatures" -> draft.antiFeatures = json.readStringList()
                "icon" -> draft.icon = json.nextStringOrNull()
                "localized" -> draft.localized = readLocalized(json)
                else -> json.skipValue()
            }
        }
        json.endObject()
        return draft.takeIf { it.packageName.isNotBlank() }
    }

    /** Keeps only the preferred locales; every other locale is skipped unread. */
    private fun readLocalized(json: JsonReader): Map<String, Localized> {
        val result = HashMap<String, Localized>()
        json.beginObject()
        while (json.hasNext()) {
            val locale = json.nextName()
            if (locale !in locales) {
                json.skipValue()
                continue
            }
            val entry = Localized()
            json.beginObject()
            while (json.hasNext()) {
                when (json.nextName()) {
                    "name" -> entry.name = json.nextStringOrNull()
                    "summary" -> entry.summary = json.nextStringOrNull()
                    "description" -> entry.description = json.nextStringOrNull()
                    "whatsNew" -> entry.whatsNew = json.nextStringOrNull()
                    "icon" -> entry.icon = json.nextStringOrNull()
                    "tvBanner" -> entry.tvBanner = json.nextStringOrNull()
                    "featureGraphic" -> entry.featureGraphic = json.nextStringOrNull()
                    "tvScreenshots" -> entry.screenshots[ShotKind.TV] = json.readStringList()
                    "tenInchScreenshots" -> entry.screenshots[ShotKind.TEN_INCH] = json.readStringList()
                    "sevenInchScreenshots" -> entry.screenshots[ShotKind.SEVEN_INCH] = json.readStringList()
                    "phoneScreenshots" -> entry.screenshots[ShotKind.PHONE] = json.readStringList()
                    else -> json.skipValue()
                }
            }
            json.endObject()
            result[locale] = entry
        }
        json.endObject()
        return result
    }

    private fun readPackages(json: JsonReader): Map<String, List<ApkVersion>> {
        val result = HashMap<String, List<ApkVersion>>()
        json.beginObject()
        while (json.hasNext()) {
            val packageName = json.nextName()
            val versions = ArrayList<ApkVersion>()
            json.beginArray()
            while (json.hasNext()) readPackage(json)?.let(versions::add)
            json.endArray()
            result[packageName] = versions
        }
        json.endObject()
        return result
    }

    private fun readPackage(json: JsonReader): ApkVersion? {
        var versionCode = -1L
        var versionName = ""
        var apkName = ""
        var hash = ""
        var hashType = ""
        var size = 0L
        var minSdk = 1
        var targetSdk = 0
        var maxSdk: Int? = null
        var nativeCode = emptyList<String>()
        var features = emptyList<String>()
        val permissions = ArrayList<String>()
        var signer = ""
        var added = 0L
        var antiFeatures = emptyList<String>()

        json.beginObject()
        while (json.hasNext()) {
            when (json.nextName()) {
                "versionCode" -> versionCode = json.nextLong()
                "versionName" -> versionName = json.nextStringOrNull().orEmpty()
                "apkName" -> apkName = json.nextStringOrNull().orEmpty()
                "hash" -> hash = json.nextStringOrNull().orEmpty().lowercase()
                "hashType" -> hashType = json.nextStringOrNull().orEmpty().lowercase()
                "size" -> size = json.nextLong()
                "minSdkVersion" -> minSdk = json.nextInt()
                "targetSdkVersion" -> targetSdk = json.nextInt()
                "maxSdkVersion" -> maxSdk = json.nextInt()
                "nativecode" -> nativeCode = json.readStringList()
                "features" -> features = json.readStringList()
                "uses-permission", "uses-permission-sdk-23" -> permissions += json.readPermissionNames()
                "signer" -> signer = json.nextStringOrNull().orEmpty().lowercase()
                "added" -> added = json.nextLong()
                "antiFeatures" -> antiFeatures = json.readStringList()
                else -> json.skipValue()
            }
        }
        json.endObject()

        // Without a SHA-256 hash and a signer the APK cannot be verified: never offer it.
        if (versionCode < 0 || apkName.isBlank() || hashType != "sha256" || !SHA256.matches(hash) ||
            !SHA256.matches(signer) || apkName.contains('/')
        ) {
            return null
        }
        return ApkVersion(
            versionCode = versionCode,
            versionName = versionName,
            apkName = apkName,
            sha256 = hash,
            size = size,
            minSdk = minSdk,
            targetSdk = targetSdk,
            maxSdk = maxSdk,
            nativeCode = nativeCode,
            features = features,
            permissions = permissions.distinct(),
            signer = signer,
            added = added,
            antiFeatures = antiFeatures,
        )
    }

    private class Localized {
        var name: String? = null
        var summary: String? = null
        var description: String? = null
        var whatsNew: String? = null
        var icon: String? = null
        var tvBanner: String? = null
        var featureGraphic: String? = null
        val screenshots = HashMap<ShotKind, List<String>>()
    }

    private enum class ShotKind(val folder: String) {
        TV("tvScreenshots"), TEN_INCH("tenInchScreenshots"), SEVEN_INCH("sevenInchScreenshots"), PHONE("phoneScreenshots"),
    }

    private inner class AppDraft {
        var packageName = ""
        var name: String? = null
        var summary: String? = null
        var description: String? = null
        var categories = emptyList<String>()
        var license = ""
        var author: String? = null
        var webSite: String? = null
        var sourceCode: String? = null
        var issueTracker: String? = null
        var changelog: String? = null
        var added = 0L
        var lastUpdated = 0L
        var antiFeatures = emptyList<String>()
        var icon: String? = null
        var localized: Map<String, Localized> = emptyMap()

        /** First preferred locale that has a value for [field]. */
        private fun <T : Any> pick(field: (Localized) -> T?): Pair<String, T>? {
            for (locale in locales) {
                val value = localized[locale]?.let(field) ?: continue
                return locale to value
            }
            return null
        }

        fun toCatalogApp(repoId: String, address: String, versions: List<ApkVersion>): CatalogApp {
            val iconUrl = pick { it.icon }?.let { (locale, file) -> "$address/$packageName/$locale/$file" }
                ?: icon?.let { "$address/icons-640/$it" }
            val shots = ShotKind.entries.firstNotNullOfOrNull { kind ->
                pick { it.screenshots[kind]?.takeIf(List<String>::isNotEmpty) }
                    ?.let { (locale, files) -> files.map { "$address/$packageName/$locale/${kind.folder}/$it" } }
            }.orEmpty()
            val hasTv = localized.values.any { it.tvBanner != null || !it.screenshots[ShotKind.TV].isNullOrEmpty() } ||
                versions.any { "android.software.leanback" in it.features }

            return CatalogApp(
                repoId = repoId,
                packageName = packageName,
                name = pick { it.name?.takeIf(String::isNotBlank) }?.second ?: name ?: packageName,
                summary = (pick { it.summary?.takeIf(String::isNotBlank) }?.second ?: summary).orEmpty().trim(),
                description = cleanText(pick { it.description?.takeIf(String::isNotBlank) }?.second ?: description),
                whatsNew = cleanText(pick { it.whatsNew?.takeIf(String::isNotBlank) }?.second),
                categories = categories,
                license = license,
                author = author,
                webSite = webSite?.takeIf { it.startsWith("https://") || it.startsWith("http://") },
                sourceCode = sourceCode,
                issueTracker = issueTracker,
                changelog = changelog,
                added = added,
                lastUpdated = lastUpdated,
                antiFeatures = (antiFeatures + versions.first().antiFeatures).distinct(),
                iconUrl = iconUrl,
                tvBannerUrl = pick { it.tvBanner }?.let { (locale, file) -> "$address/$packageName/$locale/$file" },
                featureGraphicUrl = pick { it.featureGraphic }?.let { (locale, file) -> "$address/$packageName/$locale/$file" },
                screenshotUrls = shots,
                hasTvAssets = hasTv,
                versions = versions,
            )
        }
    }

    companion object {
        private val SHA256 = Regex("^[0-9a-f]{64}$")
        private val TAG = Regex("<[^>]+>")
        private val BREAK = Regex("(?i)<br\\s*/?>|</p>|</li>|</h[1-6]>")
        private val ITEM = Regex("(?i)<li[^>]*>")
        private val BLANK_LINES = Regex("\n{3,}")

        /** Old index entries use simple HTML; render it as plain text. */
        fun cleanText(raw: String?): String {
            if (raw.isNullOrBlank()) return ""
            return raw
                .replace("\r\n", "\n")
                .replace(BREAK, "\n")
                .replace(ITEM, "• ")
                .replace(TAG, "")
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&amp;", "&")
                .replace(BLANK_LINES, "\n\n")
                .trim()
        }

        /** Preferred locales for the index, best first, ending with English fallbacks. */
        fun preferredLocales(deviceLocales: List<java.util.Locale>): List<String> {
            val result = LinkedHashSet<String>()
            for (locale in deviceLocales) {
                if (locale.country.isNotEmpty()) result += "${locale.language}-${locale.country}"
                result += locale.language
            }
            result += "en-US"
            result += "en"
            return result.toList()
        }
    }
}

class IndexFormatException(message: String) : Exception(message)

private fun JsonReader.nextStringOrNull(): String? =
    if (peek() == JsonToken.NULL) {
        nextNull()
        null
    } else {
        nextString()
    }

private fun JsonReader.readStringList(): List<String> {
    if (peek() == JsonToken.NULL) {
        nextNull()
        return emptyList()
    }
    val list = ArrayList<String>()
    beginArray()
    while (hasNext()) nextStringOrNull()?.let(list::add)
    endArray()
    return list
}

/** "uses-permission" entries are [name, maxSdkVersion] pairs; keep the names. */
private fun JsonReader.readPermissionNames(): List<String> {
    if (peek() == JsonToken.NULL) {
        nextNull()
        return emptyList()
    }
    val names = ArrayList<String>()
    beginArray()
    while (hasNext()) {
        if (peek() == JsonToken.BEGIN_ARRAY) {
            beginArray()
            if (hasNext()) nextStringOrNull()?.let(names::add)
            while (hasNext()) skipValue()
            endArray()
        } else {
            skipValue()
        }
    }
    endArray()
    return names
}
