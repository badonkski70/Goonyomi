package mihon.domain.extensionrepo.service

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class MangaExtensionStoreTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `parse new index json with string versionCode`() {
        val store = json.decodeFromString<NetworkMangaExtensionStore>(
            """
            {
              "name": "Keiyoushi",
              "badgeLabel": "KEI",
              "signingKey": "9add655a78e96c4ec7a53ef89dccb557cb5d767489fac5e785d671a5a75d4da2",
              "contact": {"website": "https://keiyoushi.github.io", "discord": "https://discord.gg/3FbCpdKbdY"},
              "extensionList": {
                "extensions": [{
                  "name": "HentaiNexus",
                  "packageName": "eu.kanade.tachiyomi.extension.en.hentainexus",
                  "resources": {
                    "apkUrl": "https://github.com/keiyoushi/extensions/releases/download/4217666-0/tachiyomi-en.hentainexus-v1.6.20.apk",
                    "iconUrl": "https://cdn.jsdelivr.net/gh/keiyoushi/extensions/src/en/hentainexus/icon.png",
                    "jarUrl": "https://github.com/keiyoushi/extensions/releases/download/19c8e5f-0/tachiyomi-en.hentainexus-v1.6.20.jar"
                  },
                  "extensionLib": "1.6",
                  "versionCode": "106020",
                  "versionName": "1.6.20",
                  "contentWarning": "CONTENT_WARNING_NSFW",
                  "sources": [{"id": "7719798645438596394", "name": "HentaiNexus", "language": "en", "homeUrl": "https://hentainexus.com"}]
                }]
              }
            }
            """.trimIndent(),
        )
        val ext = store.extensionList!!.extensions.single()
        ext.versionCode shouldBe 106020L
        ext.sources.single().id shouldBe 7719798645438596394L
        ext.contentWarning shouldBe NetworkMangaExtensionStore.ContentWarning.NSFW
        store.toExtensionRepo("https://raw.githubusercontent.com/keiyoushi/extensions/repo").let {
            it.name shouldBe "Keiyoushi"
            it.signingKeyFingerprint shouldBe "9add655a78e96c4ec7a53ef89dccb557cb5d767489fac5e785d671a5a75d4da2"
        }
    }

    @Test
    fun `parse legacy repo json without shortName and with index_v2`() {
        val meta = json.decodeFromString<ExtensionRepoMetaDto>(
            """
            {
              "index_v2": "https://github.com/keiyoushi/extensions/raw/repo/index.pb",
              "meta": {
                "name": "Keiyoushi",
                "website": "https://keiyoushi.github.io",
                "signingKeyFingerprint": "9add655a78e96c4ec7a53ef89dccb557cb5d767489fac5e785d671a5a75d4da2"
              }
            }
            """.trimIndent(),
        )
        meta.index_v2 shouldBe "https://github.com/keiyoushi/extensions/raw/repo/index.pb"
        meta.meta.shortName shouldBe null
    }

    @Test
    fun `normalize index urls`() {
        ExtensionRepoService.normalizeIndexUrl("https://github.com/yuzono/manga-repo/raw/repo/index.pb") shouldBe
            "https://raw.githubusercontent.com/yuzono/manga-repo/repo/index.pb"
        ExtensionRepoService.normalizeIndexUrl("https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.pb") shouldBe
            "https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.pb"
        ExtensionRepoService.normalizeIndexUrl("https://example.com/repo/index.txt") shouldBe null
        ExtensionRepoService.baseUrlOf("https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.pb") shouldBe
            "https://raw.githubusercontent.com/keiyoushi/extensions/repo"
    }
}
