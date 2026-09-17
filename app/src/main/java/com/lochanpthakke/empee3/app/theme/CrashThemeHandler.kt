package com.lochanpthakke.empee3.app.theme

import com.lochanpthakke.empee3.core.common.logging.Logger
import com.lochanpthakke.empee3.core.datastore.repository.ThemeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@Suppress("TooGenericExceptionCaught")
class CrashThemeHandler
    @Inject
    constructor(
        private val themeRepository: ThemeRepository,
        private val logger: Logger,
    ) : Thread.UncaughtExceptionHandler {
        private var defaultHandler: Thread.UncaughtExceptionHandler? = null
        private val scope = CoroutineScope(Dispatchers.IO)

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
            scope.launch {
                try {
                    themeRepository.triggerRollback("Crash detected: ${throwable.message}")
                } catch (e: Exception) {
                    logger.e(TAG, "Failed to trigger theme rollback during crash", e)
                } finally {
                    defaultHandler?.uncaughtException(thread, throwable)
                }
            }
        }

        companion object {
            private const val TAG = "CrashThemeHandler"
        }
    }
