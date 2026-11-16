package com.erhu.erhu.feature.library.remote

import android.accounts.Account
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.erhu.erhu.core.model.RemoteAccessException
import com.erhu.erhu.core.model.RemoteAccessException.Reason
import com.erhu.erhu.core.model.RemoteProvider
import com.erhu.erhu.core.model.RemoteSourceLookup
import com.erhu.erhu.core.network.remote.drive.DriveTokenProvider
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import javax.inject.Singleton

/** The outcome of asking Google for Drive access. */
sealed interface DriveAuthorization {
    class Granted(
        val token: String,
        val grantedScopes: List<String> = emptyList(),
    ) : DriveAuthorization

    /** The user has to choose an account or agree first: launch [consent] and pass the result to [GoogleDriveAuth.fromConsent]. */
    class NeedsConsent(
        val consent: PendingIntent,
    ) : DriveAuthorization
}

/** The outcome of processing the activity result from the account picker or consent screen. */
sealed interface DriveConsentOutcome {
    class Granted(
        val token: String,
        val grantedScopes: List<String> = emptyList(),
    ) : DriveConsentOutcome

    class NeedsResolution(
        val consent: PendingIntent,
    ) : DriveConsentOutcome

    object Canceled : DriveConsentOutcome

    class Failed(
        val message: String,
        val cause: Throwable? = null,
    ) : DriveConsentOutcome
}

/**
 * Google account authorization through Google Play services' AuthorizationClient, the supported
 * Android flow. The only scope ever requested is read-only Drive access.
 *
 * erhu stores no Google credential at all: Play services keeps the grant and renews access tokens,
 * and erhu holds a token in memory for a few minutes at most. A stored source only records the
 * account's e-mail address so the right account is asked next time.
 *
 * This works only when a Google Cloud OAuth client exists for this app's package name and signing
 * certificate; see docs/remote-libraries.md. Without it, Google refuses and [authorize] throws.
 */
@Singleton
class GoogleDriveAuth
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val sources: RemoteSourceLookup,
    ) : DriveTokenProvider {
        private class Cached(
            val token: String,
            val atMs: Long,
        )

        private val cache = ConcurrentHashMap<Long, Cached>()

        /** Blocking. [account] is the e-mail of an already connected account, or null to let the user choose. */
        @Throws(IOException::class)
        fun authorize(account: String?): DriveAuthorization {
            Log.d(TAG, "authorize: account=$account, requestedScope=$DRIVE_READONLY")
            val request =
                AuthorizationRequest
                    .builder()
                    .setRequestedScopes(listOf(Scope(DRIVE_READONLY)))
                    .apply { if (!account.isNullOrBlank()) setAccount(Account(account, GOOGLE_ACCOUNT_TYPE)) }
                    .build()
            val result =
                try {
                    Tasks.await(Identity.getAuthorizationClient(context).authorize(request), TIMEOUT_SECONDS, TimeUnit.SECONDS)
                } catch (e: ExecutionException) {
                    val cause = e.cause
                    if (cause is ApiException) {
                        val statusName = CommonStatusCodes.getStatusCodeString(cause.statusCode)
                        Log.e(TAG, "authorize() failed with ApiException: statusCode=${cause.statusCode} ($statusName), message=${cause.message}", cause)
                    } else {
                        Log.e(TAG, "authorize() failed with ExecutionException cause: ${cause?.javaClass?.simpleName}: ${cause?.message}", cause)
                    }
                    throw IOException(describe(cause), cause)
                } catch (e: TimeoutException) {
                    Log.e(TAG, "authorize() timed out after ${TIMEOUT_SECONDS}s", e)
                    throw IOException("Google did not answer in time", e)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    Log.e(TAG, "authorize() interrupted", e)
                    throw IOException("Sign-in was interrupted", e)
                }
            val consent = result.pendingIntent
            val token = result.accessToken
            val scopes = result.grantedScopes ?: emptyList()
            Log.d(
                TAG,
                "authorize() result: hasResolution=${result.hasResolution()}, " +
                    "hasPendingIntent=${consent != null}, " +
                    "hasAccessToken=${!token.isNullOrBlank()}, " +
                    "grantedScopes=$scopes",
            )
            return when {
                result.hasResolution() && consent != null -> DriveAuthorization.NeedsConsent(consent)
                !token.isNullOrBlank() -> DriveAuthorization.Granted(token, scopes)
                else -> throw IOException("Google did not grant access to Drive")
            }
        }

        /**
         * The result from the account selection or consent screen's Activity Result.
         * Distinguishes user cancellation from denied permissions, configuration errors,
         * or secondary resolution requirements (e.g. consent prompt following account picker).
         */
        fun fromConsent(resultCode: Int, data: Intent?): DriveConsentOutcome {
            Log.d(TAG, "fromConsent: resultCode=$resultCode, hasData=${data != null}")
            if (resultCode == Activity.RESULT_CANCELED && (data == null || data.extras?.isEmpty != false)) {
                Log.i(TAG, "User canceled authorization flow (RESULT_CANCELED with empty data)")
                return DriveConsentOutcome.Canceled
            }

            try {
                val result =
                    Identity
                        .getAuthorizationClient(context)
                        .getAuthorizationResultFromIntent(data)

                val scopes = result.grantedScopes ?: emptyList()
                val hasResolution = result.hasResolution()
                val pendingIntent = result.pendingIntent
                val token = result.accessToken

                Log.d(
                    TAG,
                    "getAuthorizationResultFromIntent success: hasResolution=$hasResolution, " +
                        "hasPendingIntent=${pendingIntent != null}, " +
                        "hasAccessToken=${!token.isNullOrBlank()}, " +
                        "grantedScopes=$scopes",
                )

                return when {
                    hasResolution && pendingIntent != null -> {
                        Log.i(TAG, "Authorization requires further resolution/consent. Forwarding PendingIntent.")
                        DriveConsentOutcome.NeedsResolution(pendingIntent)
                    }
                    !token.isNullOrBlank() -> {
                        if (scopes.isNotEmpty() && !scopes.contains(DRIVE_READONLY)) {
                            Log.w(TAG, "Token received but requested scope $DRIVE_READONLY was not granted: $scopes")
                            DriveConsentOutcome.Failed(
                                "Google Drive permission was not granted. erhu requires read-only access to browse and play your music.",
                            )
                        } else {
                            Log.i(TAG, "Token received with granted scopes: $scopes")
                            DriveConsentOutcome.Granted(token, scopes)
                        }
                    }
                    else -> {
                        Log.e(TAG, "Authorization returned neither access token nor resolution")
                        DriveConsentOutcome.Failed("Google did not grant access to Google Drive.")
                    }
                }
            } catch (e: ApiException) {
                val statusCode = e.statusCode
                val statusName = CommonStatusCodes.getStatusCodeString(statusCode)
                val statusMsg = e.status.statusMessage ?: e.message ?: ""
                Log.e(TAG, "getAuthorizationResultFromIntent failed: statusCode=$statusCode ($statusName), message=$statusMsg", e)

                if (statusMsg.contains("UNREGISTERED_ON_API_CONSOLE", ignoreCase = true)) {
                    return DriveConsentOutcome.Failed(
                        "Google sign-in is not registered in Google Cloud Console for this app's package and signing certificate.",
                        e,
                    )
                }

                return when (statusCode) {
                    CommonStatusCodes.CANCELED, STATUS_SIGN_IN_CANCELLED -> {
                        Log.i(TAG, "User canceled authorization (statusCode=$statusCode)")
                        DriveConsentOutcome.Canceled
                    }
                    CommonStatusCodes.DEVELOPER_ERROR -> {
                        DriveConsentOutcome.Failed(
                            "Google sign-in is not set up for this build of erhu (no OAuth client for its package and signing certificate).",
                            e,
                        )
                    }
                    STATUS_SIGN_IN_FAILED -> {
                        DriveConsentOutcome.Failed(
                            "Google sign-in failed (code $statusCode). Ensure the Google Drive API is enabled, OAuth consent screen is configured for 'drive.readonly', and your account is added as a test user in Google Cloud Console.",
                            e,
                        )
                    }
                    CommonStatusCodes.NETWORK_ERROR -> {
                        DriveConsentOutcome.Failed("Google could not be reached. Check your internet connection.", e)
                    }
                    else -> {
                        val detail = if (statusMsg.isNotBlank()) ": $statusMsg" else ""
                        DriveConsentOutcome.Failed("Google sign-in failed: $statusName ($statusCode)$detail", e)
                    }
                }
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Log.e(TAG, "Unexpected exception parsing consent result: ${e.javaClass.simpleName}: ${e.message}", e)
                return DriveConsentOutcome.Failed(e.message ?: "Google sign-in failed", e)
            }
        }

        /** Backward-compatible overload returning access token or null. */
        fun fromConsent(data: Intent?): String? =
            when (val outcome = fromConsent(Activity.RESULT_OK, data)) {
                is DriveConsentOutcome.Granted -> outcome.token
                else -> null
            }

        fun remember(
            sourceId: Long,
            token: String,
        ) {
            cache[sourceId] = Cached(token, System.currentTimeMillis())
        }

        override fun token(sourceId: Long): String {
            cache[sourceId]?.takeIf { System.currentTimeMillis() - it.atMs < TOKEN_REUSE_MS }?.let { return it.token }
            val source =
                sources.source(sourceId)?.takeIf { it.provider == RemoteProvider.GOOGLE_DRIVE }
                    ?: throw RemoteAccessException(Reason.SOURCE_MISSING, "This Google account is no longer connected")
            return when (val outcome = authorize(source.account)) {
                is DriveAuthorization.Granted -> outcome.token.also { remember(sourceId, it) }
                // A background caller (the player) cannot show Google's screen: the browser offers "Sign in again".
                is DriveAuthorization.NeedsConsent -> throw RemoteAccessException(Reason.AUTH_REQUIRED, "Sign in to Google Drive again")
            }
        }

        override fun invalidate(sourceId: Long) {
            cache.remove(sourceId)
        }

        private fun describe(cause: Throwable?): String {
            val statusMsg = cause?.message.orEmpty()
            if (statusMsg.contains("UNREGISTERED_ON_API_CONSOLE", ignoreCase = true)) {
                return "Google sign-in is not registered in Google Cloud Console for this app's package and signing certificate."
            }
            return when (val code = (cause as? ApiException)?.statusCode) {
                CommonStatusCodes.DEVELOPER_ERROR ->
                    "Google sign-in is not set up for this build of erhu (no OAuth client for its package and signing certificate)."
                CommonStatusCodes.NETWORK_ERROR -> "Google could not be reached. Check your connection."
                STATUS_SIGN_IN_FAILED ->
                    "Google sign-in failed (code 12500). Verify OAuth consent screen, enabled Drive API, and test users in Google Cloud Console."
                CommonStatusCodes.CANCELED, STATUS_SIGN_IN_CANCELLED -> "Google sign-in was cancelled."
                null -> cause?.message?.takeIf { it.isNotBlank() } ?: "Google sign-in failed"
                else -> "Google sign-in failed: ${CommonStatusCodes.getStatusCodeString(code)} ($code)"
            }
        }

        private fun getCertificateSha1(context: Context): String =
            try {
                val signatures =
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        context.packageManager.getPackageInfo(
                            context.packageName,
                            android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES,
                        ).signingInfo?.apkContentsSigners
                    } else {
                        @Suppress("DEPRECATION")
                        context.packageManager.getPackageInfo(
                            context.packageName,
                            android.content.pm.PackageManager.GET_SIGNATURES,
                        ).signatures
                    }
                val cert = signatures?.firstOrNull()?.toByteArray() ?: return "unknown"
                val md = java.security.MessageDigest.getInstance("SHA-1")
                val digest = md.digest(cert)
                digest.joinToString(":") { "%02X".format(it) }
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                "unknown"
            }

        private companion object {
            const val TAG = "GoogleDriveAuth"
            const val DRIVE_READONLY = "https://www.googleapis.com/auth/drive.readonly"
            const val GOOGLE_ACCOUNT_TYPE = "com.google"
            const val TIMEOUT_SECONDS = 30L

            /** Play services renews tokens itself; this only avoids a cross-process call for every byte-range request. */
            const val TOKEN_REUSE_MS = 5 * 60 * 1000L
            const val STATUS_SIGN_IN_FAILED = 12500
            const val STATUS_SIGN_IN_CANCELLED = 12501
        }
    }

@Module
@InstallIn(SingletonComponent::class)
interface GoogleDriveAuthModule {
    @Binds
    fun bindDriveTokens(impl: GoogleDriveAuth): DriveTokenProvider
}
