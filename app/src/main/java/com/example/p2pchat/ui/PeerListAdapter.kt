package com.example.p2pchat.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.p2pchat.R

class PeerListAdapter(
    private val data: List<String>,
    private val onClickItem: (String) -> Unit
) : RecyclerView.Adapter<PeerListAdapter.PeerViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PeerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return PeerViewHolder(view)
    }

    override fun onBindViewHolder(holder: PeerViewHolder, position: Int) {
        val peerName = data[position]
        holder.bind(peerName, onClickItem)
    }

    override fun getItemCount() = data.size

    class PeerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val text1: TextView = itemView.findViewById(android.R.id.text1)
        fun bind(name: String, onClick: (String) -> Unit) {
            text1.text = name
            itemView.setOnClickListener { onClick(name) }
        }
    }
}