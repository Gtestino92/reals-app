package com.reals.app.ui.root

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.reals.app.ui.matchmaking.CafecitoSupportUrl

internal fun openCafecitoSupport(context: Context): Boolean {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(CafecitoSupportUrl))
        .addCategory(Intent.CATEGORY_BROWSABLE)
        .apply {
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    return runCatching {
        context.startActivity(intent)
    }.isSuccess
}
