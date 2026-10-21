/* SPDX-License-Identifier: Apache-2.0 */
package org.lineageos.smartqr.ui

import android.content.Context
import android.view.Gravity
import android.widget.Toast
import android.widget.ToastPresenter
import com.android.internal.R

internal class TopToast(context: Context, text: CharSequence) : Toast(context) {
    init {
        view = ToastPresenter.getTextToastView(context, text).apply {
            setBackgroundResource(R.drawable.toast_frame)
        }
        duration = Toast.LENGTH_SHORT
        setGravity(
            Gravity.TOP or Gravity.CENTER_HORIZONTAL,
            0,
            context.resources.getDimensionPixelSize(R.dimen.toast_y_offset),
        )
    }
}
