package dev.krelzox.store.model

/**
 * A software repository the store reads from. [fingerprint] is the SHA-256 of the
 * certificate that signs the repository index (64 lowercase hex characters): an index
 * signed by any other key is rejected.
 */
data class RepoConfig(
    val id: String,
    val name: String,
    val address: String,
    val fingerprint: String,
    val enabled: Boolean = true,
) {
    val indexUrl: String get() = "$address/index-v1.jar"

    companion object {
        val FDROID = RepoConfig(
            id = "fdroid",
            name = "F-Droid",
            address = "https://f-droid.org/repo",
            fingerprint = "43238d512c1e5eb2d6569f4a3afbf5523418b82e0a3ed1552770abb9a9c9ccab",
        )

        private val FINGERPRINT = Regex("^[0-9a-f]{64}$")

        /** Normalizes a fingerprint typed with spaces, colons or uppercase; null if invalid. */
        fun normalizeFingerprint(raw: String): String? =
            raw.lowercase().filter { it.isLetterOrDigit() }.takeIf { FINGERPRINT.matches(it) }

        /** Repositories must be HTTPS; trailing slashes are removed. Null if invalid. */
        fun normalizeAddress(raw: String): String? {
            val trimmed = raw.trim().trimEnd('/')
            if (!trimmed.startsWith("https://")) return null
            val host = trimmed.removePrefix("https://").substringBefore('/')
            if (host.isBlank() || host.contains(' ') || host.contains('@')) return null
            return trimmed
        }
    }
}

/** One installable build of an app, exactly as published in the repository index. */
data class ApkVersion(
    val versionCode: Long,
    val versionName: String,
    val apkName: String,
    /** Lowercase hex SHA-256 of the APK file. */
    val sha256: String,
    val size: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val maxSdk: Int?,
    /** ABIs of the native code; empty means pure Java/Kotlin (runs everywhere). */
    val nativeCode: List<String>,
    /** Hardware/software features the APK requires. */
    val features: List<String>,
    val permissions: List<String>,
    /** Lowercase hex SHA-256 of the APK signing certificate. */
    val signer: String,
    val added: Long,
    val antiFeatures: List<String>,
)

/** Store listing of an app with all its published versions (newest first). */
data class CatalogApp(
    val repoId: String,
    val packageName: String,
    val name: String,
    val summary: String,
    val description: String,
    val whatsNew: String,
    val categories: List<String>,
    val license: String,
    val author: String?,
    val webSite: String?,
    val sourceCode: String?,
    val issueTracker: String?,
    val changelog: String?,
    val added: Long,
    val lastUpdated: Long,
    val antiFeatures: List<String>,
    val iconUrl: String?,
    val tvBannerUrl: String?,
    val featureGraphicUrl: String?,
    val screenshotUrls: List<String>,
    /** True when the developer published TV screenshots or a TV banner. */
    val hasTvAssets: Boolean,
    val versions: List<ApkVersion>,
) {
    val isGame: Boolean get() = categories.any { isGameCategory(it) }

    companion object {
        fun isGameCategory(category: String): Boolean =
            category.endsWith(" Game") || category == "Games"
    }
}

/** Repository metadata from the index. */
data class RepoInfo(
    val name: String,
    val address: String,
    /** Index generation time in milliseconds; used to refuse rolled-back indexes. */
    val timestamp: Long,
    val description: String,
)

/** Everything parsed from one repository index. */
data class RepoIndex(val repo: RepoInfo, val apps: List<CatalogApp>)

/** The merged catalog of every enabled repository. */
class Catalog(val apps: List<CatalogApp>) {
    val byPackage: Map<String, CatalogApp> = apps.associateBy { it.packageName }

    /** Categories with their app counts, most populated first. */
    val categories: List<Pair<String, Int>> = apps.asSequence()
        .flatMap { it.categories.asSequence() }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .map { it.key to it.value }

    companion object {
        val EMPTY = Catalog(emptyList())

        /** Merges repositories in priority order: the first one that lists a package wins. */
        fun merge(indexes: List<RepoIndex>): Catalog {
            val seen = HashSet<String>()
            val merged = ArrayList<CatalogApp>()
            for (index in indexes) {
                for (app in index.apps) {
                    if (seen.add(app.packageName)) merged += app
                }
            }
            return Catalog(merged)
        }
    }
}
