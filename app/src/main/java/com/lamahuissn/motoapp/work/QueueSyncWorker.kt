package com.lamahuissn.motoapp.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lamahuissn.motoapp.data.QueueRunner
import com.lamahuissn.motoapp.data.TokenVault
import com.lamahuissn.motoapp.data.WriteQueueStore

/**
 * The queue draining with nobody watching (spec 010, phase B).
 *
 * It uses the app's own runner — the same walk, the same keys, the same money rules — so a payment sent
 * from a pocket cannot be treated differently from one sent from a screen. It reports back only twice: when
 * something arrived, and when something is still waiting.
 *
 * No session on the phone means nothing to send: the rows wait for their own collector, because the office
 * attributes a receipt to the token that sent it.
 */
class QueueSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val session = TokenVault(applicationContext).load() ?: return Result.success()

        val report = QueueRunner.drain(
            store = WriteQueueStore(applicationContext),
            userId = session.user.id,
            token = session.token,
        )

        // Nothing answered at all: the network is still not there for this request. WorkManager will run
        // it again with its own backoff, and until then every row keeps its place.
        if (!report.reached) return Result.retry()

        if (report.sentAnything) QueueNotifier.delivered(applicationContext, report.applied)
        if (report.stillOwed > 0) {
            QueueNotifier.waiting(applicationContext, report.stillOwed)
        } else {
            QueueNotifier.clear(applicationContext)
        }

        return Result.success()
    }
}
