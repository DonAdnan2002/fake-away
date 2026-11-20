package com.fakeaway.app.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Abstracts network availability checks so hybrid engines can be tested offline/online.
 */
interface ConnectivityChecker {
    fun isOnline(): Boolean
}

class AndroidConnectivityChecker(private val context: Context) : ConnectivityChecker {
    override fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
