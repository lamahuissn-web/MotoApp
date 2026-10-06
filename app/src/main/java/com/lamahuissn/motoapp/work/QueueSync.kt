package com.lamahuissn.motoapp.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * The queue's own appointment with the system (spec 010, phase B).
 *
 * Registered the moment a write lands, with one condition: there must be a network. Android then runs it
 * when that becomes true — app open or not, screen on or not, phone in a pocket. Nothing here keeps a
 * service alive or wakes the phone on a timer: the work either exists and waits for a network, or it does
 * not exist.
 */
object QueueSync {

    private const val WORK = "motoapp-queue-sync"

    fun schedule(context: Context) {
        val request = OneTimeWorkRequestBuilder<QueueSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            // A first retry after thirty seconds, doubling: a phone that steps out of a dead zone once
            // should not wait minutes for its second chance.
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        // KEEP, not REPLACE: the job already waiting for a network is about to do exactly this work, and
        // a second copy of it would drain the same queue twice.
        WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.KEEP, request)
    }
}
