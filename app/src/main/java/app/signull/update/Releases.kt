package app.signull.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Semantic version, ignoring pre-release and build suffixes. */
data class Version(val major: Int, val minor: Int, val patch: Int) : Comparable<Version> {
    override fun compareTo(other: Version): Int =
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        fun parse(text: String?): Version? {
            val core = text?.trim()?.removePrefix("v")?.removePrefix("V")?.substringBefore('-')?.substringBefore('+')
            if (core.isNullOrEmpty()) return null
            val parts = core.split('.')
            if (parts.size > 3) return null
            val numbers = parts.map { it.toIntOrNull() ?: return null }
            return Version(numbers.getOrElse(0) { 0 }, numbers.getOrElse(1) { 0 }, numbers.getOrElse(2) { 0 })
        }
    }
}

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("published_at") val publishedAt: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
data class GitHubAsset(
    val name: String,
    val size: Long,
    @SerialName("browser_download_url") val downloadUrl: String,
)

/** A published release with an installable APK. */
data class AppRelease(
    val version: Version,
    val tag: String,
    val title: String,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String,
    val apkName: String,
    val apkSize: Long,
    val publishedAt: String?,
)

object ReleaseParser {
    private val json = Json { ignoreUnknownKeys = true }

    /** Returns null for drafts, pre-releases, unparseable tags, or releases without an APK. */
    fun parse(body: String): AppRelease? {
        val release = runCatching { json.decodeFromString<GitHubRelease>(body) }.getOrNull() ?: return null
        if (release.draft || release.prerelease) return null
        val version = Version.parse(release.tagName) ?: return null
        val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) } ?: return null
        return AppRelease(
            version = version,
            tag = release.tagName,
            title = release.name?.takeIf { it.isNotBlank() } ?: "SigNull? $version",
            notes = release.body.orEmpty().trim(),
            pageUrl = release.htmlUrl,
            apkUrl = apk.downloadUrl,
            apkName = apk.name,
            apkSize = apk.size,
            publishedAt = release.publishedAt,
        )
    }
}
