/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.smartqr

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.lineageos.smartqr.aon.AonServiceHelper

internal class QrDetector(
    private val scope: CoroutineScope,
    private val canDetect: () -> Boolean,
    private val onResult: (Boolean) -> Unit,
) {
    companion object {
        private const val TAG = "QrDetector"
        private const val DEBUG = true
        private const val AON_TIMEOUT_MS = 3900L
    }

    private var job: Job? = null
    private var aon: AonServiceHelper? = null

    fun start() {
        if (job?.isCompleted == false) return

        job = scope.launch {
            onResult(detect())
        }
    }

    fun cancel() {
        if (job?.isCompleted == false) {
            job?.cancel()
        } else if (aon != null) {
            job = scope.launch { release() }
        }
    }

    private suspend fun detect(): Boolean {
        return try {
            if (!release()) return false
            detectAon() && canDetect()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AON detection failed", e)
            false
        }
    }

    private suspend fun detectAon(): Boolean {
        val detected = try {
            withTimeoutOrNull(AON_TIMEOUT_MS) { awaitAonResult() } == true
        } finally {
            release()
        }

        if (DEBUG) Log.d(TAG, "AON detection result=$detected")
        return detected && aon == null
    }

    private suspend fun awaitAonResult(): Boolean {
        val result = CompletableDeferred<Boolean>()
        val helper = AonServiceHelper(
            onResult = { result.complete(it) },
            onDied = {
                Log.e(TAG, "AON service died")
                result.complete(false)
            },
        )
        aon = helper
        withContext(Dispatchers.IO) { helper.register() }
        return result.await()
    }

    private suspend fun release(): Boolean {
        val helper = aon ?: return true
        return withContext(NonCancellable + Dispatchers.IO) {
            try {
                val released = helper.release()
                if (released) aon = null
                released
            } catch (e: Exception) {
                Log.e(TAG, "Cannot release AON request", e)
                false
            }
        }
    }
}
