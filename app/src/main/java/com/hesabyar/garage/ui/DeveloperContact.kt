package com.hesabyar.garage.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

internal data class DeveloperProfileLink(val primaryUri: String, val fallbackUri: String = primaryUri)

internal object DeveloperContact {
    const val NAME = "پیمان ارجمند"
    const val EMAIL = "peymanarjmand308@gmail.com"
    const val BALE_ID = "@peyman_arjmand"
    const val TELEGRAM_ID = "@Peymanarjmand77"

    val bale = DeveloperProfileLink("https://ble.ir/peyman_arjmand")
    val telegram = DeveloperProfileLink(
        primaryUri = "tg://resolve?domain=Peymanarjmand77",
        fallbackUri = "https://t.me/Peymanarjmand77",
    )
}

internal fun openDeveloperProfile(context: Context, link: DeveloperProfileLink): Boolean {
    fun launch(uri: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    return try {
        launch(link.primaryUri)
        true
    } catch (_: ActivityNotFoundException) {
        try {
            launch(link.fallbackUri)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
