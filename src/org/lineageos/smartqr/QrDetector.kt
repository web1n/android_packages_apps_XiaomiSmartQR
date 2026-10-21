/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.smartqr

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.lineageos.smartqr.aon.AonServiceHelper
import org.lineageos.smartqr.camera.MiCamClient
import org.lineageos.smartqr.camera.QrCodeDecoder

internal class QrDetector(
    private val context: Context,
    private val scope: CoroutineScope,
    private val canDetect: () -> Boolean,
    private val onResult: (String?) -> Unit,
) {
    companion object {
        private const val TAG = "QrDetector"
        private const val DEBUG = true
        private const val AON_TIMEOUT_MS = 3900L
        private const val DECODE_TIMEOUT_MS = 2000L
        private const val RETRY_DELAY_MS = 400L
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

    private suspend fun detect(): String? {
        return try {
            if (!release() || !detectAon() || !canDetect()) return null
            withTimeout(DECODE_TIMEOUT_MS) { awaitQrCode() }
        } catch (_: TimeoutCancellationException) {
            Log.w(TAG, "QR decoding timed out")
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "QR detection failed", e)
            null
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

    private suspend fun awaitQrCode(): String {
        val frames = Channel<MiCamClient.Frame>(Channel.CONFLATED)
        val camera = MiCamClient(
            context,
            onFrame = { frames.trySend(it) },
            onError = { frames.close(it) },
        )

        return try {
            camera.open()
            withContext(Dispatchers.Default) {
                val decoder = QrCodeDecoder()
                var text: String? = null
                while (text == null) {
                    val frame = frames.receive()
                    text = decoder.decode(frame.data, frame.width, frame.height)
                    if (text == null) delay(RETRY_DELAY_MS)
                }
                text
            }
        } finally {
            frames.cancel()
            camera.close()
        }
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
