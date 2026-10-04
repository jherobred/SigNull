package app.signull.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseTest {

    @Test
    fun parsesTagsLeniently() {
        assertEquals(Version(1, 2, 3), Version.parse("v1.2.3"))
        assertEquals(Version(0, 2, 0), Version.parse("0.2"))
        assertEquals(Version(1, 2, 3), Version.parse(" V1.2.3-beta.1+build7 "))
        assertNull(Version.parse("latest"))
        assertNull(Version.parse("1.2.3.4"))
        assertNull(Version.parse(null))
    }

    @Test
    fun comparesNumerically() {
        assertTrue(Version(0, 10, 0) > Version(0, 9, 9))
        assertTrue(Version(1, 0, 0) > Version(0, 99, 99))
        assertEquals(0, Version(0, 2, 0).compareTo(Version.parse("v0.2")!!))
    }

    private fun json(
        tag: String = "v1.1.0",
        name: String = "SigNull? 1.1.0",
        draft: Boolean = false,
        prerelease: Boolean = false,
        asset: String = "SigNull-v1.1.0.apk",
    ) = """
        {
          "tag_name": "$tag",
          "name": "$name",
          "body": "  - Smoother guide\n",
          "html_url": "https://github.com/jherobred/SigNull/releases/tag/$tag",
          "published_at": "2026-10-01T10:00:00Z",
          "draft": $draft,
          "prerelease": $prerelease,
          "author": { "login": "jherobred" },
          "assets": [
            { "name": "checksums.txt", "size": 120, "browser_download_url": "https://example.invalid/checksums.txt" },
            { "name": "$asset", "size": 9000000, "browser_download_url": "https://example.invalid/$asset" }
          ]
        }
    """.trimIndent()

    @Test
    fun readsAPublishedRelease() {
        val release = ReleaseParser.parse(json())!!
        assertEquals(Version(1, 1, 0), release.version)
        assertEquals("SigNull-v1.1.0.apk", release.apkName)
        assertEquals("https://example.invalid/SigNull-v1.1.0.apk", release.apkUrl)
        assertEquals(9_000_000L, release.apkSize)
        assertEquals("- Smoother guide", release.notes)
    }

    @Test
    fun blankTitleFallsBackToTheVersion() {
        assertEquals("SigNull? 1.1.0", ReleaseParser.parse(json(name = " "))!!.title)
    }

    @Test
    fun skipsReleasesThatCannotBeInstalled() {
        assertNull("draft", ReleaseParser.parse(json(draft = true)))
        assertNull("pre-release", ReleaseParser.parse(json(prerelease = true)))
        assertNull("no APK", ReleaseParser.parse(json(asset = "SigNull.aab")))
        assertNull("odd tag", ReleaseParser.parse(json(tag = "nightly")))
        assertNull("not JSON", ReleaseParser.parse("<html>rate limited</html>"))
    }
}
