package com.erhu.erhu.feature.transfer.server

import android.content.Context
import android.view.View
import com.erhu.erhu.core.common.locale.AppLocaleManager
import com.erhu.erhu.core.common.R as CommonR

/** Localized UI strings for [TransferRegistry] terminal item messages. */
data class TransferRegistryStrings(
    val duplicateMessage: String = "Already on the phone: skipped, nothing was overwritten",
    val cancelledMessage: String = "Cancelled before it finished",
)

/** Localized error messages for storage writes inside MediaStore / legacy storage. */
data class TransferSinkStrings(
    val noWritePermission: (String) -> String = { folder -> "No permission to write to Music/$folder" },
    val createRefused: String = "The phone refused to create the file",
    val openFailed: String = "Could not open the file for writing",
    val publishFailed: String = "Could not publish the file",
    val mkdirFailed: (String) -> String = { folder -> "Could not create Music/$folder" },
    val finishFailed: String = "Could not finish the file",
)

/** Localized API responses and failure reasons produced by [TransferRouter]. */
data class TransferRouterStrings(
    val wrongHost: String = "Wrong host",
    val notFound: String = "Not found",
    val methodNotAllowed: String = "Method not allowed",
    val crossOriginRefused: String = "Cross-origin request refused",
    val useTransferPage: String = "Use the transfer page",
    val sessionEndedOrWrongLink: String = "This transfer session has ended or the link is wrong",
    val wrongCode: String = "Wrong code. Check the code shown on the phone.",
    val tooManyWrongCodes: String =
        "Too many wrong codes. Scan the QR code, or stop and start the transfer on the phone.",
    val sessionEnded: String = "This transfer session has ended",
    val missingFileSize: String = "Missing file size",
    val contentLengthRequired: String = "Content-Length required",
    val emptyFile: String = "Empty file",
    val busyRetry: String = "Busy: the phone is already receiving files, this one will be retried",
    val duplicateMessage: String = "Already on the phone: skipped, nothing was overwritten",
    val noSpaceMessage: String = "Not enough free space on the phone",
    val createFailedWithReason: (String) -> String = { reason ->
        "Could not create the file on the phone: $reason"
    },
    val createFailed: String = "Could not create the file on the phone",
    val invalidAudioFormat: (String) -> String = { ext -> "Not a valid $ext audio file" },
    val finishSaveFailedWithReason: (String) -> String = { reason ->
        "Received, but the phone could not finish saving it: $reason"
    },
    val finishSaveFailed: String = "The phone could not finish saving the file",
    val writeFailedWithReason: (String) -> String = { reason ->
        "Could not write to the phone's storage (full or unavailable): $reason"
    },
    val storeFailed: String = "The phone could not store the file",
    val unsupportedFormat: String = "Not a supported audio file (MP3, FLAC, M4A, AAC, OGG, Opus or WAV)",
    val tooLargeMb: (Long) -> String = { maxMb -> "File is larger than $maxMb MB" },
)

/** Localized strings and CLDR plural patterns rendered into the browser transfer page. */
data class TransferWebStrings(
    val langTag: String = "en",
    val isRtl: Boolean = false,
    val title: String = "erhu Wi-Fi Transfer",
    val subtitle: String =
        "Send music from this computer straight to your phone over your Wi-Fi. Nothing goes through the internet.",
    val enterCodePrompt: String = "Enter the 6-digit code shown on the phone.",
    val connectButton: String = "Connect",
    val dropHeading: String = "Drop music files or folders here",
    val supportedFormats: String = "MP3, FLAC, M4A, AAC, OGG, Opus and WAV",
    val chooseFilesButton: String = "Choose files",
    val chooseFolderButton: String = "Choose a folder",
    val sendButton: String = "Send to phone",
    val cancelButton: String = "Cancel",
    val clearListButton: String = "Clear list",
    val connectFailed: String = "Could not connect",
    val phoneUnreachableTransfer: String = "The phone is not reachable. Is Wi-Fi Transfer still on?",
    val phoneUnreachable: String = "The phone is not reachable",
    val unsupportedAudio: String = "Not a supported audio file",
    val emptyFile: String = "Empty file",
    val cancelOrRemove: String = "Cancel or remove",
    val cancelOrRemoveFileTemplate: String = "Cancel or remove {name}",
    val statusWaiting: String = "Waiting",
    val statusChecking: String = "Checking",
    val statusSending: String = "Sending",
    val statusSaving: String = "Saving on phone",
    val statusSaved: String = "Saved",
    val statusDuplicate: String = "Already on phone",
    val statusSkipped: String = "Skipped",
    val statusFailed: String = "Failed",
    val statusCancelled: String = "Cancelled",
    val summarySavedTemplate: String = "{count} saved",
    val summaryDuplicateTemplate: String = "{count} already on phone",
    val summaryNotSentTemplate: String = "{count} not sent",
    val errorCodeTemplate: String = "Error {code}",
    val connectionLost: String = "Connection lost: is the phone still on this Wi-Fi?",
    val sessionEndedFatal: String =
        "This transfer session has ended. Start Wi-Fi Transfer on the phone again.",
    val fileCountZeroPattern: String = "{n} files",
    val fileCountOnePattern: String = "{n} file",
    val fileCountTwoPattern: String = "{n} files",
    val fileCountFewPattern: String = "{n} files",
    val fileCountManyPattern: String = "{n} files",
    val fileCountOtherPattern: String = "{n} files",
    val unitB: String = "B",
    val unitKb: String = "KB",
    val unitMb: String = "MB",
    val unitGb: String = "GB",
)

/** Bundle of localized strings used by the Wi-Fi Transfer HTTP server and storage sink. */
data class TransferStrings(
    val registry: TransferRegistryStrings = TransferRegistryStrings(),
    val sink: TransferSinkStrings = TransferSinkStrings(),
    val router: TransferRouterStrings = TransferRouterStrings(),
    val web: TransferWebStrings = TransferWebStrings(),
) {
    companion object {
        private const val SAMPLE_ZERO = 0
        private const val SAMPLE_ONE = 1
        private const val SAMPLE_TWO = 2
        private const val SAMPLE_FEW = 3
        private const val SAMPLE_MANY = 11
        private const val SAMPLE_OTHER = 100

        /** Resolves all server and web UI strings using the active in-app locale. */
        fun from(context: Context): TransferStrings {
            val localized = AppLocaleManager.localizedContext(context)
            val locale = AppLocaleManager.activeLocale(localized)
            return TransferStrings(
                registry = loadRegistry(localized),
                sink = loadSink(localized),
                router = loadRouter(localized),
                web =
                    loadWeb(
                        localized,
                        locale.toLanguageTag(),
                        localized.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL,
                    ),
            )
        }

        private fun loadRegistry(c: Context) =
            TransferRegistryStrings(
                duplicateMessage = c.getString(CommonR.string.transfer_registry_duplicate),
                cancelledMessage = c.getString(CommonR.string.transfer_registry_cancelled),
            )

        private fun loadSink(c: Context) =
            TransferSinkStrings(
                noWritePermission = { folder -> c.getString(CommonR.string.transfer_sink_no_write_permission, folder) },
                createRefused = c.getString(CommonR.string.transfer_sink_create_refused),
                openFailed = c.getString(CommonR.string.transfer_sink_open_failed),
                publishFailed = c.getString(CommonR.string.transfer_sink_publish_failed),
                mkdirFailed = { folder -> c.getString(CommonR.string.transfer_sink_mkdir_failed, folder) },
                finishFailed = c.getString(CommonR.string.transfer_sink_finish_failed),
            )

        private fun loadRouter(c: Context) =
            TransferRouterStrings(
                wrongHost = c.getString(CommonR.string.transfer_router_wrong_host),
                notFound = c.getString(CommonR.string.transfer_router_not_found),
                methodNotAllowed = c.getString(CommonR.string.transfer_router_method_not_allowed),
                crossOriginRefused = c.getString(CommonR.string.transfer_router_cross_origin),
                useTransferPage = c.getString(CommonR.string.transfer_router_use_transfer_page),
                sessionEndedOrWrongLink = c.getString(CommonR.string.transfer_router_session_ended_or_wrong_link),
                wrongCode = c.getString(CommonR.string.transfer_router_wrong_code),
                tooManyWrongCodes = c.getString(CommonR.string.transfer_router_too_many_codes),
                sessionEnded = c.getString(CommonR.string.transfer_router_session_ended),
                missingFileSize = c.getString(CommonR.string.transfer_router_missing_size),
                contentLengthRequired = c.getString(CommonR.string.transfer_router_content_length_required),
                emptyFile = c.getString(CommonR.string.transfer_router_empty_file),
                busyRetry = c.getString(CommonR.string.transfer_router_busy),
                duplicateMessage = c.getString(CommonR.string.transfer_registry_duplicate),
                noSpaceMessage = c.getString(CommonR.string.transfer_router_no_space),
                createFailedWithReason = { reason -> c.getString(CommonR.string.transfer_router_create_failed_with_reason, reason) },
                createFailed = c.getString(CommonR.string.transfer_router_create_failed),
                invalidAudioFormat = { ext -> c.getString(CommonR.string.transfer_router_invalid_audio_ext, ext) },
                finishSaveFailedWithReason = { reason ->
                    c.getString(CommonR.string.transfer_router_finish_save_failed_with_reason, reason)
                },
                finishSaveFailed = c.getString(CommonR.string.transfer_router_finish_save_failed),
                writeFailedWithReason = { reason -> c.getString(CommonR.string.transfer_router_write_failed_with_reason, reason) },
                storeFailed = c.getString(CommonR.string.transfer_router_store_failed),
                unsupportedFormat = c.getString(CommonR.string.transfer_router_unsupported_format),
                tooLargeMb = { maxMb -> c.getString(CommonR.string.transfer_router_too_large, maxMb) },
            )

        private fun loadWeb(
            c: Context,
            langTag: String,
            isRtl: Boolean,
        ): TransferWebStrings {
            fun filePattern(sample: Int): String =
                c.resources
                    .getQuantityString(CommonR.plurals.transfer_web_files_count, sample, sample)
                    .replace(sample.toString(), "{n}")

            return TransferWebStrings(
                langTag = langTag,
                isRtl = isRtl,
                title = c.getString(CommonR.string.transfer_web_title),
                subtitle = c.getString(CommonR.string.transfer_web_subtitle),
                enterCodePrompt = c.getString(CommonR.string.transfer_web_enter_code),
                connectButton = c.getString(CommonR.string.transfer_web_connect),
                dropHeading = c.getString(CommonR.string.transfer_web_drop_heading),
                supportedFormats = c.getString(CommonR.string.transfer_web_supported_formats),
                chooseFilesButton = c.getString(CommonR.string.transfer_web_choose_files),
                chooseFolderButton = c.getString(CommonR.string.transfer_web_choose_folder),
                sendButton = c.getString(CommonR.string.transfer_web_send_to_phone),
                cancelButton = c.getString(CommonR.string.transfer_web_cancel),
                clearListButton = c.getString(CommonR.string.transfer_web_clear_list),
                connectFailed = c.getString(CommonR.string.transfer_web_connect_failed),
                phoneUnreachableTransfer = c.getString(CommonR.string.transfer_web_phone_unreachable_transfer),
                phoneUnreachable = c.getString(CommonR.string.transfer_web_phone_unreachable),
                unsupportedAudio = c.getString(CommonR.string.transfer_web_unsupported_audio),
                emptyFile = c.getString(CommonR.string.transfer_web_empty_file),
                cancelOrRemove = c.getString(CommonR.string.transfer_web_cancel_or_remove),
                cancelOrRemoveFileTemplate = c.getString(CommonR.string.transfer_web_cancel_or_remove_file),
                statusWaiting = c.getString(CommonR.string.transfer_web_status_waiting),
                statusChecking = c.getString(CommonR.string.transfer_web_status_checking),
                statusSending = c.getString(CommonR.string.transfer_web_status_sending),
                statusSaving = c.getString(CommonR.string.transfer_web_status_saving),
                statusSaved = c.getString(CommonR.string.transfer_web_status_saved),
                statusDuplicate = c.getString(CommonR.string.transfer_web_status_duplicate),
                statusSkipped = c.getString(CommonR.string.transfer_web_status_skipped),
                statusFailed = c.getString(CommonR.string.transfer_web_status_failed),
                statusCancelled = c.getString(CommonR.string.transfer_web_status_cancelled),
                summarySavedTemplate = c.getString(CommonR.string.transfer_web_summary_saved),
                summaryDuplicateTemplate = c.getString(CommonR.string.transfer_web_summary_duplicate),
                summaryNotSentTemplate = c.getString(CommonR.string.transfer_web_summary_not_sent),
                errorCodeTemplate = c.getString(CommonR.string.transfer_web_error_code),
                connectionLost = c.getString(CommonR.string.transfer_web_connection_lost),
                sessionEndedFatal = c.getString(CommonR.string.transfer_web_session_ended),
                fileCountZeroPattern = filePattern(SAMPLE_ZERO),
                fileCountOnePattern = filePattern(SAMPLE_ONE),
                fileCountTwoPattern = filePattern(SAMPLE_TWO),
                fileCountFewPattern = filePattern(SAMPLE_FEW),
                fileCountManyPattern = filePattern(SAMPLE_MANY),
                fileCountOtherPattern = filePattern(SAMPLE_OTHER),
                unitB = c.getString(CommonR.string.transfer_web_unit_b),
                unitKb = c.getString(CommonR.string.transfer_web_unit_kb),
                unitMb = c.getString(CommonR.string.transfer_web_unit_mb),
                unitGb = c.getString(CommonR.string.transfer_web_unit_gb),
            )
        }
    }
}
