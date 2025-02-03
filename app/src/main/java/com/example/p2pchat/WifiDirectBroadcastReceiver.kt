package com.example.p2pchat

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import androidx.core.app.ActivityCompat

class WifiDirectBroadcastReceiver(
    private val manager: WifiP2pManager,
    private val channel: WifiP2pManager.Channel,
    private val activity: ChatActivity
) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val action: String? = intent?.action
        if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {

            // Check to see if Wi-fi is enabled and notify appropriate activity

        } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {

            // Call WifiP2pManager.requestPeers() to get a list of current peers
            if (context != null && ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.NEARBY_WIFI_DEVICES
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                manager.requestPeers(channel, activity.peerListListener)
            }


        } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {

            // Respond to new connection or disconnection
            if (manager != null) {
                val networkInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent?.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO, NetworkInfo::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent?.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO)
                }

                // Check if we are connected or not
                if (networkInfo?.isConnected == true) {
                    manager.requestConnectionInfo(channel, activity.connectionInfoListener)
                } else {
                    activity.connectionStatus.text = "Not Connected"
                }

            }
        }
    }
}