package com.erhu.erhu

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.erhu.erhu.core.network.repository.UpdateCheckerRepository
import com.erhu.erhu.core.network.update.AutomaticUpdateStatus
import com.erhu.erhu.core.network.update.InstalledAppVersion
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class MonthlyUpdateWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted parameters: WorkerParameters,
        private val repository: UpdateCheckerRepository,
        private val status: AutomaticUpdateStatus,
    ) : CoroutineWorker(context, parameters) {
        override suspend fun doWork(): Result {
            val result = repository.checkForUpdates(InstalledAppVersion.read(applicationContext))
            status.record(result)
            // A periodic check runs again next month; manual Retry is always independent.
            return Result.success()
        }
    }
