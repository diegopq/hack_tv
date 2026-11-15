package io.github.diegopq.hacktv.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.SystemClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** How the TV is online, for the icon next to the clock. */
enum class Connection {
    ETHERNET,
    WIFI,
    /** Connected to a network that doesn't reach the internet. */
    NO_INTERNET,
    OFFLINE,
}

object NetworkState {

    /** The TV's connection, updated as it changes (cable plugged, Wi-Fi dropped...). */
    fun connection(context: Context): Flow<Connection> = callbackFlow {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        fun send(caps: NetworkCapabilities?) {
            trySend(
                when {
                    caps == null -> Connection.OFFLINE
                    !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) -> Connection.NO_INTERNET
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Connection.ETHERNET
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Connection.WIFI
                    else -> Connection.ETHERNET
                },
            )
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = send(caps)
            override fun onLost(network: Network) = send(null)
        }
        send(manager?.getNetworkCapabilities(manager.activeNetwork))
        runCatching { manager?.registerDefaultNetworkCallback(callback) }
        awaitClose { runCatching { manager?.unregisterNetworkCallback(callback) } }
    }.distinctUntilChanged()
}

/** What the TV is downloading and uploading right now, in bits per second. */
data class Throughput(val downBps: Long, val upBps: Long)

object NetworkTraffic {

    /**
     * The TV's traffic, sampled once a second from the system's byte counters (every app, not just this
     * one). It only runs while someone collects it, that is, while Home is on screen.
     */
    fun throughput(): Flow<Throughput> = flow {
        var rx = TrafficStats.getTotalRxBytes()
        var tx = TrafficStats.getTotalTxBytes()
        var at = SystemClock.elapsedRealtime()
        if (rx == TrafficStats.UNSUPPORTED.toLong() || tx == TrafficStats.UNSUPPORTED.toLong()) return@flow
        while (true) {
            delay(1_000)
            val nowRx = TrafficStats.getTotalRxBytes()
            val nowTx = TrafficStats.getTotalTxBytes()
            val now = SystemClock.elapsedRealtime()
            val seconds = ((now - at).coerceAtLeast(1)) / 1000.0
            emit(
                Throughput(
                    downBps = (((nowRx - rx).coerceAtLeast(0)) * 8 / seconds).toLong(),
                    upBps = (((nowTx - tx).coerceAtLeast(0)) * 8 / seconds).toLong(),
                ),
            )
            rx = nowRx
            tx = nowTx
            at = now
        }
    }
}
