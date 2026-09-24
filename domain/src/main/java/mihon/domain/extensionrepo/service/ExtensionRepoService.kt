package mihon.domain.extensionrepo.service

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import mihon.domain.extensionrepo.model.ExtensionRepo
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

class ExtensionRepoService(
    networkHelper: NetworkHelper,
    private val json: Json,
    private val protoBuf: ProtoBuf,
) {
    val client = networkHelper.client

    suspend fun fetchRepoDetails(
        repo: String,
    ): ExtensionRepo? {
        return withIOContext {
            try {
                val trimmed = normalizeInput(repo) ?: return@withIOContext null
                val suffix = KNOWN_SUFFIXES.firstOrNull { trimmed.endsWith(it) }
                val indexUrl = suffix?.let { trimmed } ?: "$trimmed/repo.json"
                val baseUrl = suffix?.let { baseUrlOf(trimmed) } ?: trimmed.trimEnd('/')
                resolveRepo(indexUrl, baseUrl, 0)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to fetch repo details" }
                null
            }
        }
    }

    private suspend fun resolveRepo(indexUrl: String, baseUrl: String, depth: Int): ExtensionRepo? {
        if (depth > 2) return null
        // ponytail: stale index_v2 pointers exist (komizoku repo.json -> dead yuzono index.pb),
        // fall back to sibling well-known locations before giving up
        val candidates = linkedSetOf(
            indexUrl,
            "$baseUrl/repo.json",
            "$baseUrl/index.json",
            "$baseUrl/index.pb",
            "$baseUrl/index.min.json",
        )
        var lastError: Throwable? = null
        for (candidate in candidates) {
            try {
                resolveRepoCandidate(candidate, baseUrl, depth)?.let { return it }
            } catch (e: Exception) {
                lastError = e
            }
        }
        lastError?.let { logcat(LogPriority.WARN, it) { "Failed to fetch repo details from $baseUrl" } }
        return null
    }

    private suspend fun resolveRepoCandidate(indexUrl: String, baseUrl: String, depth: Int): ExtensionRepo? {
        val bytes = client.newCall(GET(indexUrl)).awaitSuccess().use { it.body.bytes() }.ungzip()
        return when {
            bytes.startsWith('[') -> {
                // Legacy extension list; meta lives in sibling repo.json
                val metaBytes = client.newCall(GET("$baseUrl/repo.json")).awaitSuccess()
                    .use { it.body.bytes() }.ungzip()
                if (metaBytes.startsWith('{')) {
                    val meta = json.decodeFromString<ExtensionRepoMetaDto>(metaBytes.toString(Charsets.UTF_8))
                    meta.index_v2?.let { return resolveRepo(it, baseUrlOf(it) ?: baseUrl, depth + 1) }
                    return meta.toExtensionRepo(baseUrl = baseUrl)
                }
                null
            }
            bytes.startsWith('{') -> {
                val text = bytes.toString(Charsets.UTF_8)
                if (text.contains("\"meta\"")) {
                    val meta = json.decodeFromString<ExtensionRepoMetaDto>(text)
                    meta.index_v2?.let { return resolveRepo(it, baseUrlOf(it) ?: baseUrl, depth + 1) }
                    meta.toExtensionRepo(baseUrl = baseUrl)
                } else {
                    json.decodeFromString<NetworkMangaExtensionStore>(text)
                        .toExtensionRepo(baseUrl = baseUrl)
                }
            }
            else -> {
                @OptIn(ExperimentalSerializationApi::class)
                protoBuf.decodeFromByteArray<NetworkMangaExtensionStore>(bytes)
                    .toExtensionRepo(baseUrl = baseUrl)
            }
        }
    }

    companion object {
        private val KNOWN_SUFFIXES = listOf("/index.min.json", "/index.json", "/index.pb", "/repo.json")

        fun normalizeInput(input: String): String? {
            val url = input.trim()
                .replace("http://", "https://")
                // github.com/A/B/raw/branch/... -> raw.githubusercontent.com/A/B/branch/...
                .replace(
                    Regex("^https://github\\.com/([^/]+)/([^/]+)/raw/(.+)$"),
                    "https://raw.githubusercontent.com/$1/$2/$3",
                )
            if (!url.startsWith("https://")) return null
            return url.trimEnd('/')
        }

        fun normalizeIndexUrl(input: String): String? {
            val url = normalizeInput(input) ?: return null
            return when {
                KNOWN_SUFFIXES.any { url.endsWith(it) } -> url
                else -> null
            }
        }

        fun baseUrlOf(indexUrl: String): String? {
            return when {
                indexUrl.endsWith("/index.min.json") -> indexUrl.removeSuffix("/index.min.json")
                indexUrl.endsWith("/index.json") -> indexUrl.removeSuffix("/index.json")
                indexUrl.endsWith("/index.pb") -> indexUrl.removeSuffix("/index.pb")
                indexUrl.endsWith("/repo.json") -> indexUrl.removeSuffix("/repo.json")
                else -> null
            }?.trimEnd('/')
        }

        fun ByteArray.ungzip(): ByteArray {
            if (size > 2 && this[0] == 0x1f.toByte() && this[1] == 0x8b.toByte()) {
                return GZIPInputStream(ByteArrayInputStream(this)).readBytes()
            }
            return this
        }

        private fun ByteArray.startsWith(char: Char): Boolean {
            return isNotEmpty() && this[0] == char.code.toByte()
        }
    }
}
