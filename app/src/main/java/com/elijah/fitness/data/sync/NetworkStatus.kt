package com.elijah.fitness.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Whether the phone has a network that reaches the internet right now.
 * The repository checks it before an upload, so an offline save doesn't
 * make a request that is bound to fail.
 */
class NetworkStatus(context: Context) {

    private val connectivity = context.applicationContext
        .getSystemService(ConnectivityManager::class.java)

    fun isOnline(): Boolean {
        val manager = connectivity ?: return true
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
