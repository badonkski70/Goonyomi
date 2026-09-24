package eu.kanade.tachiyomi.extension.manga.api

import android.content.Context
import eu.kanade.tachiyomi.extension.ExtensionUpdateNotifier
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import eu.kanade.tachiyomi.extension.manga.model.MangaLoadResult
import eu.kanade.tachiyomi.extension.manga.util.MangaExtensionLoader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import logcat.LogPriority
import mihon.domain.extensionrepo.manga.interactor.GetMangaExtensionRepo
import mihon.domain.extensionrepo.manga.interactor.UpdateMangaExtensionRepo
import mihon.domain.extensionrepo.model.ExtensionRepo
import mihon.domain.extensionrepo.service.ExtensionRepoMetaDto
import mihon.domain.extensionrepo.service.NetworkMangaExtensionStore
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.injectLazy
import java.io.ByteArrayInputStream
import java.time.Instant
import java.util.zip.GZIPInputStream
import kotlin.time.Duration.Companion.days

internal class MangaExtensionApi {

    private val networkService: NetworkHelper by injectLazy()
    private val preferenceStore: PreferenceStore by injectLazy()
    private val getExtensionRepo: GetMangaExtensionRepo by injectLazy()
    private val updateExtensionRepo: UpdateMangaExtensionRepo by injectLazy()
    private val extensionManager: MangaExtensionManager by injectLazy()
    private val json: Json by injectLazy()
    private val protoBuf: ProtoBuf by injectLazy()

    private val lastExtCheck: Preference<Long> by lazy {
        preferenceStore.getLong("last_ext_check", 0)
    }

    suspend fun findExtensions(): List<MangaExtension.Available> {
        return withIOContext {
            getExtensionRepo.getAll()
                .map { async { getExtensions(it) } }
                .awaitAll()
                .flatten()
        }
    }

    private suspend fun getExtensions(extRepo: ExtensionRepo): List<MangaExtension.Available> {
        val repoBaseUrl = extRepo.baseUrl.trimEnd('/')
        return try {
            // New index format takes precedence (legacy index.min.json is a stub on migrated repos)
            getExtensionsV2(repoBaseUrl)?.let { return it }
            getExtensionsLegacy(repoBaseUrl)
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Failed to get extensions from $repoBaseUrl" }
            emptyList()
        }
    }

    private suspend fun getExtensionsLegacy(repoBaseUrl: String): List<MangaExtension.Available> {
        return try {
            val response = networkService.client
                .newCall(GET("$repoBaseUrl/index.min.json"))
                .awaitSuccess()

            with(json) {
                response
                    .parseAs<List<ExtensionJsonObject>>()
                    .toExtensions(repoBaseUrl)
            }
        } catch (e: Throwable) {
            logcat(LogPriority.ERROR, e) { "Failed to get legacy extensions from $repoBaseUrl" }
            emptyList()
        }
    }

    private suspend fun getExtensionsV2(repoBaseUrl: String): List<MangaExtension.Available>? {
        // Resolve the v2 index url via repo.json, fall back to well-known locations
        val indexUrls = linkedSetOf<String>()
        try {
            val metaBytes = networkService.client
                .newCall(GET("$repoBaseUrl/repo.json"))
                .awaitSuccess()
                .use { it.body.bytes() }
                .ungzip()
            if (metaBytes.startsWith('{')) {
                val meta = json.decodeFromString<ExtensionRepoMetaDto>(metaBytes.toString(Charsets.UTF_8))
                meta.index_v2?.let { indexUrls.add(it) }
            }
        } catch (e: Throwable) {
            logcat(LogPriority.WARN, e) { "No repo.json at $repoBaseUrl" }
        }
        indexUrls.add("$repoBaseUrl/index.json")
        indexUrls.add("$repoBaseUrl/index.pb")

        for (indexUrl in indexUrls) {
            try {
                val store = fetchStore(indexUrl) ?: continue
                val list = store.extensionList
                    ?: store.extensionListUrl?.let { fetchExtensionList(it) }
                    ?: continue
                val mapped = list.toAvailable(repoBaseUrl)
                if (mapped.isNotEmpty()) return mapped
            } catch (e: Throwable) {
                logcat(LogPriority.WARN, e) { "Failed to get v2 extensions from $indexUrl" }
            }
        }
        return null
    }

    private suspend fun fetchStore(indexUrl: String): NetworkMangaExtensionStore? {
        val bytes = networkService.client
            .newCall(GET(indexUrl))
            .awaitSuccess()
            .use { it.body.bytes() }
            .ungzip()
        if (bytes.isEmpty()) return null
        return if (bytes.startsWith('{')) {
            val text = bytes.toString(Charsets.UTF_8)
            if (text.contains("\"meta\"")) return null
            json.decodeFromString<NetworkMangaExtensionStore>(text)
        } else {
            @OptIn(ExperimentalSerializationApi::class)
            protoBuf.decodeFromByteArray<NetworkMangaExtensionStore>(bytes)
        }
    }

    private suspend fun fetchExtensionList(url: String): NetworkMangaExtensionStore.ExtensionList? {
        val bytes = networkService.client
            .newCall(GET(url))
            .awaitSuccess()
            .use { it.body.bytes() }
            .ungzip()
        if (bytes.isEmpty()) return null
        return if (bytes.startsWith('{')) {
            json.decodeFromString<NetworkMangaExtensionStore.ExtensionList>(
                bytes.toString(Charsets.UTF_8),
            )
        } else {
            @OptIn(ExperimentalSerializationApi::class)
            protoBuf.decodeFromByteArray<NetworkMangaExtensionStore.ExtensionList>(bytes)
        }
    }

    private fun ByteArray.ungzip(): ByteArray {
        if (size > 2 && this[0] == 0x1f.toByte() && this[1] == 0x8b.toByte()) {
            return GZIPInputStream(ByteArrayInputStream(this)).readBytes()
        }
        return this
    }

    private fun ByteArray.startsWith(char: Char): Boolean {
        return isNotEmpty() && this[0] == char.code.toByte()
    }

    suspend fun checkForUpdates(
        context: Context,
        fromAvailableExtensionList: Boolean = false,
    ): List<MangaExtension.Installed>? {
        // Limit checks to once a day at most
        if (fromAvailableExtensionList &&
            Instant.now().toEpochMilli() < lastExtCheck.get() + 1.days.inWholeMilliseconds
        ) {
            return null
        }

        // Update extension repo details
        updateExtensionRepo.awaitAll()

        val extensions = if (fromAvailableExtensionList) {
            extensionManager.availableExtensionsFlow.value
        } else {
            findExtensions().also { lastExtCheck.set(Instant.now().toEpochMilli()) }
        }

        val installedExtensions = MangaExtensionLoader.loadMangaExtensions(context)
            .filterIsInstance<MangaLoadResult.Success>()
            .map { it.extension }

        val extensionsWithUpdate = mutableListOf<MangaExtension.Installed>()
        for (installedExt in installedExtensions) {
            val pkgName = installedExt.pkgName
            val availableExt = extensions.find { it.pkgName == pkgName } ?: continue
            val hasUpdatedVer = availableExt.versionCode > installedExt.versionCode
            val hasUpdatedLib = availableExt.libVersion > installedExt.libVersion
            val hasUpdate = hasUpdatedVer || hasUpdatedLib
            if (hasUpdate) {
                extensionsWithUpdate.add(installedExt)
            }
        }

        if (extensionsWithUpdate.isNotEmpty()) {
            ExtensionUpdateNotifier(context).promptUpdates(extensionsWithUpdate.map { it.name })
        }

        return extensionsWithUpdate
    }

    private fun List<ExtensionJsonObject>.toExtensions(repoUrl: String): List<MangaExtension.Available> {
        return this
            .filterNot { it.name in STUB_NAMES }
            .filter {
                val libVersion = it.extractLibVersion()
                libVersion != null &&
                    libVersion >= MangaExtensionLoader.LIB_VERSION_MIN &&
                    libVersion <= MangaExtensionLoader.LIB_VERSION_MAX
            }
            .map {
                MangaExtension.Available(
                    name = it.name.substringAfter("Tachiyomi: "),
                    pkgName = it.pkg,
                    versionName = it.version,
                    versionCode = it.code,
                    libVersion = it.extractLibVersion()!!,
                    lang = it.lang,
                    isNsfw = it.nsfw == 1,
                    sources = it.sources?.map(extensionSourceMapper).orEmpty(),
                    apkName = it.apk,
                    iconUrl = "$repoUrl/icon/${it.pkg}.png",
                    repoUrl = repoUrl,
                )
            }
    }

    private fun NetworkMangaExtensionStore.ExtensionList.toAvailable(
        repoUrl: String,
    ): List<MangaExtension.Available> {
        return extensions
            .filterNot { it.name in STUB_NAMES }
            .mapNotNull { it.toAvailable(repoUrl) }
    }

    private fun NetworkMangaExtensionStore.Extension.toAvailable(
        repoUrl: String,
    ): MangaExtension.Available? {
        val libVersion = extensionLib.toDoubleOrNull() ?: return null
        if (libVersion < MangaExtensionLoader.LIB_VERSION_MIN ||
            libVersion > MangaExtensionLoader.LIB_VERSION_MAX
        ) {
            return null
        }
        val langs = sources.map { it.language }.toSet()
        return MangaExtension.Available(
            name = name.substringAfter("Tachiyomi: "),
            pkgName = packageName,
            versionName = versionName,
            versionCode = versionCode,
            libVersion = libVersion,
            lang = when (langs.size) {
                0 -> ""
                1 -> langs.first()
                else -> "all"
            },
            isNsfw = contentWarning >= NetworkMangaExtensionStore.ContentWarning.MIXED,
            sources = sources.map {
                MangaExtension.Available.MangaSource(
                    id = it.id,
                    lang = it.language,
                    name = it.name,
                    baseUrl = it.homeUrl,
                )
            },
            apkName = resources.apkUrl.substringAfterLast('/'),
            iconUrl = resources.iconUrl,
            repoUrl = repoUrl,
            apkUrl = resources.apkUrl,
        )
    }

    fun getApkUrl(extension: MangaExtension.Available): String {
        return extension.apkUrl ?: "${extension.repoUrl}/apk/${extension.apkName}"
    }

    private fun ExtensionJsonObject.extractLibVersion(): Double? {
        return version.substringBeforeLast('.').toDoubleOrNull()
    }

    companion object {
        private val STUB_NAMES = setOf(
            "Please migrate to Keiyoushi",
            "Outdated App",
            "Update to Mihon 0.20.1+",
        )
    }
}

@Serializable
private data class ExtensionJsonObject(
    val name: String,
    val pkg: String,
    val apk: String,
    val lang: String,
    val code: Long,
    val version: String,
    val nsfw: Int,
    val sources: List<ExtensionSourceJsonObject>?,
)

@Serializable
private data class ExtensionSourceJsonObject(
    val id: Long,
    val lang: String,
    val name: String,
    val baseUrl: String,
)

private val extensionSourceMapper: (ExtensionSourceJsonObject) -> MangaExtension.Available.MangaSource = {
    MangaExtension.Available.MangaSource(
        id = it.id,
        lang = it.lang,
        name = it.name,
        baseUrl = it.baseUrl,
    )
}
