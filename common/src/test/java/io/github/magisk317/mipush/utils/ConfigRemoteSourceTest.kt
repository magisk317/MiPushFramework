package io.github.magisk317.mipush.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ConfigRemoteSourceTest {
    @Test
    fun `rawUrl returns direct GitHub raw url when accelerator is blank`() {
        val source = ConfigRemoteSource(
            repository = "owner/repo",
            branch = "main",
        )

        assertEquals(
            "https://raw.githubusercontent.com/owner/repo/main/_meta/config-index.json",
            source.rawUrl("_meta/config-index.json"),
        )
    }

    @Test
    fun `rawUrl prefixes original url when accelerator is a base url`() {
        val source = ConfigRemoteSource(
            repository = "owner/repo",
            branch = "main",
            accelerator = "https://mirror.example.com/",
        )

        assertEquals(
            "https://mirror.example.com/https://raw.githubusercontent.com/owner/repo/main/app.json",
            source.rawUrl("app.json"),
        )
    }

    @Test
    fun `rawUrl expands accelerator templates`() {
        val source = ConfigRemoteSource(
            repository = "owner/repo",
            branch = "main",
            accelerator = "https://mirror.example.com/raw?url={url}",
        )

        assertEquals(
            "https://mirror.example.com/raw?url=https://raw.githubusercontent.com/owner/repo/main/app.json",
            source.rawUrl("app.json"),
        )
    }
}
