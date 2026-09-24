package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.authenticate
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.isAuthenticationSupported
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableMap
import mihon.core.archive.CbzCrypto
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsSecurityScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_security

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val securityPreferences = remember { Injekt.get<SecurityPreferences>() }
        val authSupported = remember { context.isAuthenticationSupported() }

        val useAuthPref = securityPreferences.useAuthenticator()
        val useAuth by useAuthPref.collectAsState()
        val isCbzPasswordSet = remember { mutableStateOf(CbzCrypto.isPasswordSet()) }

        return listOf(
            Preference.PreferenceItem.SwitchPreference(
                preference = useAuthPref,
                title = stringResource(MR.strings.lock_with_biometrics),
                enabled = authSupported,
                onValueChanged = {
                    (context as FragmentActivity).authenticate(
                        title = context.stringResource(MR.strings.lock_with_biometrics),
                    )
                },
            ),
            Preference.PreferenceItem.ListPreference(
                preference = securityPreferences.lockAppAfter(),
                entries = LockAfterValues
                    .associateWith {
                        when (it) {
                            -1 -> stringResource(MR.strings.lock_never)
                            0 -> stringResource(MR.strings.lock_always)
                            else -> pluralStringResource(
                                MR.plurals.lock_after_mins,
                                count = it,
                                it,
                            )
                        }
                    }
                    .toImmutableMap(),
                title = stringResource(MR.strings.lock_when_idle),
                enabled = authSupported && useAuth,
                onValueChanged = {
                    (context as FragmentActivity).authenticate(
                        title = context.stringResource(MR.strings.lock_when_idle),
                    )
                },
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = securityPreferences.hideNotificationContent(),
                title = stringResource(MR.strings.hide_notification_content),
            ),
            Preference.PreferenceItem.ListPreference(
                preference = securityPreferences.secureScreen(),
                entries = SecurityPreferences.SecureScreenMode.entries
                    .associateWith { stringResource(it.titleRes) }
                    .toImmutableMap(),
                title = stringResource(MR.strings.secure_screen),
            ),
            // SY -->
            Preference.PreferenceItem.SwitchPreference(
                preference = securityPreferences.passwordProtectDownloads(),
                title = stringResource(AYMR.strings.password_protect_downloads),
                subtitle = stringResource(AYMR.strings.password_protect_downloads_summary),
                enabled = isCbzPasswordSet.value,
            ),
            Preference.PreferenceItem.ListPreference(
                preference = securityPreferences.encryptionType(),
                title = stringResource(AYMR.strings.encryption_type),
                entries = SecurityPreferences.EncryptionType.entries
                    .associateWith { stringResource(it.titleRes) }
                    .toImmutableMap(),
                enabled = securityPreferences.passwordProtectDownloads().get(),
            ),
            kotlin.run {
                var dialogOpen by remember { mutableStateOf(false) }
                if (dialogOpen) {
                    PasswordDialog(
                        onDismissRequest = { dialogOpen = false },
                        onReturnPassword = { password ->
                            dialogOpen = false

                            CbzCrypto.deleteKeyCbz()
                            securityPreferences.cbzPassword().set(
                                CbzCrypto.encryptCbz(password.replace("\n", "")),
                            )
                            isCbzPasswordSet.value = true
                        },
                    )
                }
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(AYMR.strings.set_cbz_zip_password),
                    onClick = {
                        dialogOpen = true
                    },
                )
            },
            Preference.PreferenceItem.TextPreference(
                title = stringResource(AYMR.strings.delete_cbz_archive_password),
                onClick = {
                    CbzCrypto.deleteKeyCbz()
                    securityPreferences.cbzPassword().set("")
                    isCbzPasswordSet.value = false
                },
                enabled = isCbzPasswordSet.value,
            ),
            // SY <--
            Preference.PreferenceItem.InfoPreference(stringResource(MR.strings.secure_screen_summary)),
        )
    }
}

private val LockAfterValues = persistentListOf(
    0, // Always
    1,
    2,
    5,
    10,
    -1, // Never
)

@Composable
private fun PasswordDialog(
    onDismissRequest: () -> Unit,
    onReturnPassword: (String) -> Unit,
) {
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisibility by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(AYMR.strings.cbz_archive_password)) },
        text = {
            TextField(
                value = password,
                onValueChange = { password = it },
                maxLines = 1,
                placeholder = { Text(text = stringResource(MR.strings.password)) },
                label = { Text(text = stringResource(MR.strings.password)) },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            passwordVisibility = !passwordVisibility
                        },
                    ) {
                        Icon(
                            imageVector = if (passwordVisibility) {
                                Icons.Default.Visibility
                            } else {
                                Icons.Default.VisibilityOff
                            },
                            contentDescription = null,
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { onReturnPassword(password) },
                ),
                modifier = Modifier.onKeyEvent {
                    if (it.key == Key.Enter) {
                        return@onKeyEvent true
                    }
                    false
                },
                visualTransformation = if (passwordVisibility) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
            )
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = true,
        ),
        confirmButton = {
            TextButton(
                onClick = {
                    onReturnPassword(password)
                },
            ) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
    )
}
