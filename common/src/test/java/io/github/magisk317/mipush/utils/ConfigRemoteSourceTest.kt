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
    fun `rawUrl returns direct GitLab raw url for prefixed repository`() {
        val source = ConfigRemoteSource(
            repository = "gitlab:magisk3171/MiPushConfigurations",
            branch = "dev",
        )

        assertEquals(
            "https://gitlab.com/magisk3171/MiPushConfigurations/-/raw/dev/_meta/config-index.json",
            source.rawUrl("_meta/config-index.json"),
        )
        assertEquals("gitlab:magisk3171/MiPushConfigurations@dev", source.displayName)
        assertEquals("gitlab:magisk3171/MiPushConfigurations@dev", source.cacheKey)
    }

    @Test
    fun `rawUrl normalizes full project urls`() {
        val gitlabSource = ConfigRemoteSource(
            repository = "https://gitlab.com/magisk3171/MiPushConfigurations.git",
            branch = "dev",
        )
        val githubSource = ConfigRemoteSource(
            repository = "https://github.com/owner/repo.git",
            branch = "main",
        )

        assertEquals(
            "https://gitlab.com/magisk3171/MiPushConfigurations/-/raw/dev/app.json",
            gitlabSource.rawUrl("/app.json"),
        )
        assertEquals(
            "https://raw.githubusercontent.com/owner/repo/main/app.json",
            githubSource.rawUrl("app.json"),
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
            repository = "gitlab:magisk3171/MiPushConfigurations",
            branch = "dev",
            accelerator = "https://mirror.example.com/raw?url={url}",
        )

        assertEquals(
            "https://mirror.example.com/raw?url=https://gitlab.com/magisk3171/MiPushConfigurations/-/raw/dev/app.json",
            source.rawUrl("app.json"),
        )
    }
}
