package dev.krelzox.launcher.core.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class LaunchableMergerTest {

    private fun entry(pkg: String, label: String, tv: Boolean, activity: String = "$pkg.Main") =
        AppEntry(packageName = pkg, activityName = activity, label = label, isTvNative = tv)

    private val own = "dev.krelzox.launcher"
    private val es = Locale.forLanguageTag("es-CL")

    @Test
    fun `tv native activity hides launcher fallback of same package`() {
        val result = LaunchableMerger.merge(
            tvNative = listOf(entry("com.a", "A", tv = true, activity = "com.a.Tv")),
            launcher = listOf(entry("com.a", "A phone", tv = false, activity = "com.a.Phone")),
            ownPackage = own,
            locale = es,
        )
        assertEquals(listOf("com.a/com.a.Tv"), result.map { it.key })
    }

    @Test
    fun `launcher-only apps are kept as fallback`() {
        val result = LaunchableMerger.merge(
            tvNative = emptyList(),
            launcher = listOf(entry("com.b", "Browser", tv = false)),
            ownPackage = own,
            locale = es,
        )
        assertEquals(1, result.size)
        assertEquals(false, result.single().isTvNative)
    }

    @Test
    fun `own package is excluded`() {
        val result = LaunchableMerger.merge(
            tvNative = listOf(entry(own, "Krelzox", tv = true), entry("com.c", "C", tv = true)),
            launcher = listOf(entry(own, "Krelzox", tv = false)),
            ownPackage = own,
            locale = es,
        )
        assertEquals(listOf("com.c"), result.map { it.packageName })
    }

    @Test
    fun `duplicates are removed`() {
        val e = entry("com.d", "D", tv = true)
        val result = LaunchableMerger.merge(listOf(e, e), emptyList(), own, es)
        assertEquals(1, result.size)
    }

    @Test
    fun `sorted by label ignoring case and accents`() {
        val result = LaunchableMerger.merge(
            tvNative = listOf(
                entry("com.z", "zeta", tv = true),
                entry("com.a", "Ajustes", tv = true),
                entry("com.e", "Éxito", tv = true),
                entry("com.b", "beta", tv = true),
            ),
            launcher = emptyList(),
            ownPackage = own,
            locale = es,
        )
        assertEquals(listOf("Ajustes", "beta", "Éxito", "zeta"), result.map { it.label })
    }
}
