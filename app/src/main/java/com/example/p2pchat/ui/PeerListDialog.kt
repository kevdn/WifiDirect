package com.example.p2pchat.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.p2pchat.R

class PeerListDialog(
    private val peers: List<String>,
    private val onPeerSelected: (String) -> Unit
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_peer_list, null)
        val recyclerPeers = view.findViewById<RecyclerView>(R.id.recyclerPeers)

        recyclerPeers.layoutManager = LinearLayoutManager(requireContext())
        val adapter = PeerListAdapter(peers) { selectedPeer ->
            onPeerSelected(selectedPeer)
            dismiss()
        }
        recyclerPeers.adapter = adapter

        return AlertDialog.Builder(requireContext())
            .setView(view)
            .create()
    }
}