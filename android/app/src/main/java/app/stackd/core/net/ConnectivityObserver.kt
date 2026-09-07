package app.stackd.core.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.callbackFlow

/**
 * The device's online/offline state as a cold [Flow], mirroring the web's
 * `useOnlineStatus` hook. Collectors get the current value on start, then an
 * update whenever validated internet comes or goes.
 *
 * A NetworkCallback is push-based; `callbackFlow` bridges it to a Flow that
 * registers on collect and tears the callback down on cancel, so nothing leaks
 * when the observing composable leaves the tree. We gate on
 * NET_CAPABILITY_VALIDATED, not merely "a network exists", so captive portals
 * and dead Wi-Fi read as offline the way the user experiences them.
 */
fun Context.onlineStatus(): Flow<Boolean> = callbackFlow {
    val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun validated(network: Network?): Boolean {
        val caps = network?.let { cm.getNetworkCapabilities(it) } ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    // Seed with the current active network so the banner state is correct
    // before any callback fires.
    trySend(validated(cm.activeNetwork))

    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { trySend(true) }
        override fun onLost(network: Network) { trySend(validated(cm.activeNetwork)) }
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            trySend(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
        }
    }

    val request = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .build()
    cm.registerNetworkCallback(request, callback)

    awaitClose { cm.unregisterNetworkCallback(callback) }
}.distinctUntilChanged()
