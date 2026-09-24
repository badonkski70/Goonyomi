package eu.kanade.tachiyomi.core.security

import dev.icerock.moko.resources.StringResource
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import tachiyomi.i18n.MR

class SecurityPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun useAuthenticator() = preferenceStore.getBoolean("use_biometric_lock", false)

    fun lockAppAfter() = preferenceStore.getInt("lock_app_after", 0)

    fun secureScreen() = preferenceStore.getEnum("secure_screen_v2", SecureScreenMode.INCOGNITO)

    fun hideNotificationContent() = preferenceStore.getBoolean("hide_notification_content", false)

    fun passwordProtectDownloads() = preferenceStore.getBoolean(
        Preference.privateKey("password_protect_downloads"),
        false,
    )

    fun encryptionType() = preferenceStore.getEnum("encryption_type", EncryptionType.AES_256)

    fun cbzPassword() = preferenceStore.getString(Preference.appStateKey("cbz_password"), "")

    /**
     * For app lock. Will be set when there is a pending timed lock.
     * Otherwise this pref should be deleted.
     */
    fun lastAppClosed() = preferenceStore.getLong(
        Preference.appStateKey("last_app_closed"),
        0,
    )

    enum class SecureScreenMode(val titleRes: StringResource) {
        ALWAYS(MR.strings.lock_always),
        INCOGNITO(MR.strings.pref_incognito_mode),
        NEVER(MR.strings.lock_never),
    }

    enum class EncryptionType(val titleRes: StringResource) {
        AES_128(MR.strings.aes_128),
        AES_256(MR.strings.aes_256),
        ZIP_STANDARD(MR.strings.standard_zip_encryption),
    }
}
