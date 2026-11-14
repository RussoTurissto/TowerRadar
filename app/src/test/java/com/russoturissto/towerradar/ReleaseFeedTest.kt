package com.russoturissto.towerradar

import org.junit.Assert.*
import org.junit.Test

class ReleaseFeedTest {
    @Test fun versionComparisonIsNumeric() {
        assertTrue(ReleaseFeed.isNewer("0.24.0", "0.23.9"))
        assertFalse(ReleaseFeed.isNewer("0.23.0", "0.24.0"))
        assertFalse(ReleaseFeed.isNewer("0.24.0", "0.24.0"))
        assertFalse(ReleaseFeed.isNewer("not-a-version", "0.23.0"))
    }
    @Test fun prereleaseIsNotMistakenForStable() {
        val json = """[{
          "tag_name":"v0.24.0","name":"Test channel","draft":false,"prerelease":true,
          "assets":[{"name":"TowerRadar-v0.24.0.apk","size":53000000,
           "browser_download_url":"https://github.com/RussoTurissto/TowerRadar/releases/download/v0.24.0/TowerRadar-v0.24.0.apk",
           "digest":"sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}]}]"""
        assertNull(ReleaseFeed.parse(json, false))
        assertEquals("0.24.0", ReleaseFeed.parse(json, true)?.version)
    }
    @Test fun untrustedFileIsNotAccepted() {
        val json = """[{"tag_name":"v0.24.0","prerelease":true,
          "assets":[{"name":"malware.apk","size":50000000,
           "browser_download_url":"https://elsewhere.test/app.apk"}]}]"""
        assertNull(ReleaseFeed.parse(json, true))
    }
}
