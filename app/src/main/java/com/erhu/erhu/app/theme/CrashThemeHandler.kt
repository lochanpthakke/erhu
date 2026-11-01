package com.erhu.erhu.app.theme

import android.content.Context
import com.erhu.erhu.core.common.logging.LocalCrashLogger
import com.erhu.erhu.core.common.logging.Logger
import com.erhu.erhu.core.datastore.repository.ThemeRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@Suppress("TooGenericExceptionCaught")
class CrashThemeHandler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val themeRepository: ThemeRepository,
        private val logger: Logger,
    ) : Thread.UncaughtExceptionHandler {
        private var defaultHandler: Thread.UncaughtExceptionHandler? = null

        fun install() {
            defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler(this)
            logger.i(TAG, "CrashThemeHandler installed successfully")
        }

        override fun uncaughtException(
            thread: Thread,
            throwable: Throwable,
        ) {
            logger.e(TAG, "Uncaught exception detected: ${throwable.message}", throwable)
            try {
                LocalCrashLogger(context).logCrash(throwable)
            } catch (e: Exception) {
                logger.e(TAG, "Failed to write local crash log", e)
            }

            // Block (briefly) instead of launching async: returning from here before the
            // default handler runs leaves a dead main thread and a frozen, un-killed app.
            try {
                runBlocking(Dispatchers.IO) {
                    withTimeoutOrNull(ROLLBACK_TIMEOUT_MS) {
                        themeRepository.triggerRollback("Crash detected: ${throwable.message}")
                    }
                }
            } catch (e: Exception) {
                logger.e(TAG, "Failed to trigger theme rollback during crash", e)
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }

        companion object {
            private const val TAG = "CrashThemeHandler"
            private const val ROLLBACK_TIMEOUT_MS = 1_000L
        }
    }
