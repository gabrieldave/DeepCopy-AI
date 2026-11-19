package com.blackeye.klipnote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun isNewer_comparesNumericParts() {
        assertTrue(UpdateChecker.isNewer("1.7.5", "1.7.4"))
        assertTrue(UpdateChecker.isNewer("2.0.0", "1.9.9"))
        assertFalse(UpdateChecker.isNewer("1.7.4", "1.7.5"))
        assertFalse(UpdateChecker.isNewer("1.7.4", "1.7.4"))
    }

    @Test
    fun parse_rejectsNonOfficialDownloadHost() {
        val json = """
            {
              "tag_name": "v9.9.9",
              "html_url": "https://evil.example/KlipNote",
              "assets": [
                {
                  "name": "KlipNote-v9.9.9.apk",
                  "browser_download_url": "https://evil.example/KlipNote-v9.9.9.apk"
                }
              ]
            }
        """.trimIndent()
        assertEquals(UpdateChecker.Result.Failed, UpdateChecker.parse(json, "1.0.0"))
    }

    @Test
    fun parse_acceptsOfficialGithubAsset() {
        val json = """
            {
              "tag_name": "v1.7.5",
              "html_url": "https://github.com/gabrieldave/KlipNote/releases/tag/v1.7.5",
              "assets": [
                {
                  "name": "KlipNote-v1.7.5.apk",
                  "browser_download_url": "https://github.com/gabrieldave/KlipNote/releases/download/v1.7.5/KlipNote-v1.7.5.apk"
                }
              ]
            }
        """.trimIndent()
        val result = UpdateChecker.parse(json, "1.7.4")
        assertTrue(result is UpdateChecker.Result.UpdateAvailable)
        val update = result as UpdateChecker.Result.UpdateAvailable
        assertEquals("1.7.5", update.versionName)
        assertTrue(update.downloadUrl.startsWith("https://github.com/gabrieldave/KlipNote/"))
    }

    @Test
    fun parse_upToDateWhenNotNewer() {
        val json = """
            {
              "tag_name": "v1.7.4",
              "html_url": "https://github.com/gabrieldave/KlipNote/releases/tag/v1.7.4",
              "assets": [
                {
                  "name": "KlipNote-v1.7.4.apk",
                  "browser_download_url": "https://github.com/gabrieldave/KlipNote/releases/download/v1.7.4/KlipNote-v1.7.4.apk"
                }
              ]
            }
        """.trimIndent()
        assertEquals(UpdateChecker.Result.UpToDate, UpdateChecker.parse(json, "1.7.4"))
    }
}
