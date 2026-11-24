package com.tiffles.blockgains

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.Properties
import org.junit.Test

/**
 * Privacy guard (coding-standards → Privacy): the merged manifest, including every
 * library's contribution, must never request network or storage access.
 */
class NoInternetPermissionTest {

    @Test
    fun mergedManifest_hasNoNetworkPermissions() {
        val manifest = mergedManifest().readText()

        assertThat(manifest).doesNotContain("android.permission.INTERNET")
        assertThat(manifest).doesNotContain("android.permission.ACCESS_NETWORK_STATE")
    }

    @Test
    fun mergedManifest_hasNoStoragePermissions() {
        val manifest = mergedManifest().readText()

        assertThat(manifest).doesNotContain("READ_EXTERNAL_STORAGE")
        assertThat(manifest).doesNotContain("WRITE_EXTERNAL_STORAGE")
        assertThat(manifest).doesNotContain("READ_MEDIA_")
    }

    @Test
    fun mergedManifest_disablesAutoBackup() {
        assertThat(mergedManifest().readText()).contains("android:allowBackup=\"false\"")
    }

    /** AGP writes the merged manifest path here when `isIncludeAndroidResources` is on. */
    private fun mergedManifest(): File {
        val stream = javaClass.classLoader?.getResourceAsStream("com/android/tools/test_config.properties")
        checkNotNull(stream) { "test_config.properties missing: enable unitTests.isIncludeAndroidResources" }
        val path = stream.use { Properties().apply { load(it) } }.getProperty("android_merged_manifest")
        return File(checkNotNull(path) { "android_merged_manifest not set" }).also {
            check(it.exists()) { "Merged manifest not found at $path" }
        }
    }
}
