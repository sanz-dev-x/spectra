package com.spectra.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UpdaterTest {
    @Test
    fun parsesKmpReleaseArtifact() {
        val response =
            """
            {
              "tag_name": "v1.2.3",
              "body": null,
              "published_at": "2026-09-05T12:00:00Z",
              "assets": [{
                "name": "Spectra.apk",
                "browser_download_url": "https://example.com/Spectra.apk",
                "size": 42
              }]
            }
            """.trimIndent()
        val release = checkNotNull(Updater.parseKmpRelease(response))

        assertEquals("1.2.3", release.versionName)
        assertEquals("", release.description)
        assertEquals("https://example.com/Spectra.apk", release.assets.single().downloadUrl)
        assertNull(Updater.parseKmpRelease(response.replace("Spectra.apk", "Spectra-with-Google-Cast.apk")))
    }

    @Test
    fun readsVersionOnly() {
        assertEquals("1.0.1", Updater.extractVersion("Spectra 1.0.1"))
        assertEquals("1.0.1", Updater.extractVersion("v1.0.1"))
        assertNull(Updater.extractVersion("sx-2"))
        assertTrue(Updater.compareVersions("1.0.1", "1.0.0") > 0)
    }
}
