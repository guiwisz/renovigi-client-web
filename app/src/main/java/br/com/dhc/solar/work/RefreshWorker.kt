package br.com.dhc.solar.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import br.com.dhc.solar.container
import br.com.dhc.solar.data.auth.NotLoggedInException
import br.com.dhc.solar.data.remote.ShineMonitorAuthException
import java.util.concurrent.TimeUnit

/**
 * Atualiza o snapshot (o repositório redesenha os widgets). 15 min é o mínimo do
 * WorkManager; a nuvem só recebe dados novos do inversor a cada ~5 min.
 */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        applicationContext.container.repository.refresh()
        Result.success()
    } catch (e: NotLoggedInException) {
        Result.success()
    } catch (e: ShineMonitorAuthException) {
        Result.failure()
    } catch (e: Exception) {
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    }

    companion object {
        private const val NAME = "solar-refresh"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
