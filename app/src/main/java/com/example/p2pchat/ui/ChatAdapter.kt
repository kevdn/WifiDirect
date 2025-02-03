package com.example.p2pchat.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.p2pchat.R
import java.io.File
import java.io.FileOutputStream
import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import android.widget.Button


data class Message(
    val text: String? = null,
    val audioData: ByteArray? = null,
    val audioDataTemp: String? = null,
    val isIncoming: Boolean,
    val username: String,
    val timestamp: String
)

class ChatAdapter(private val messages: List<Message>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val TYPE_IN = 1
    private val TYPE_OUT = 2
    private val TYPE_AUDIO_IN = 3
    private val TYPE_AUDIO_OUT = 4

    override fun getItemViewType(position: Int): Int {
        val msg = messages[position]
        // If there's audio data, use audio types; else use text types
        return when {
            msg.audioData != null -> if (msg.isIncoming) TYPE_AUDIO_IN else TYPE_AUDIO_OUT
            msg.isIncoming -> TYPE_IN
            else -> TYPE_OUT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_IN -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_message_in, parent, false)
                InViewHolder(view)
            }
            TYPE_OUT -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_message_out, parent, false)
                OutViewHolder(view)
            }
            TYPE_AUDIO_IN -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_audio_in, parent, false)
                AudioInViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_audio_out, parent, false)
                AudioOutViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = messages[position]
        when (holder) {
            is InViewHolder -> holder.bind(msg)
            is OutViewHolder -> holder.bind(msg)
            is AudioInViewHolder -> holder.bind(msg)
            is AudioOutViewHolder -> holder.bind(msg)
        }
    }

    override fun getItemCount(): Int {
        return messages.size
    }

    class InViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textViewUsernameIn: TextView = itemView.findViewById(R.id.textViewUsernameIn)
        private val textViewMsgIn: TextView = itemView.findViewById(R.id.textViewMessageIn)
        private val textViewTimestamp: TextView = itemView.findViewById(R.id.textViewTimestamp)
        fun bind(message: Message) {
            textViewUsernameIn.text = message.username
            textViewMsgIn.text = message.text
            textViewTimestamp.text = message.timestamp
        }
    }

    class OutViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textViewUsernameOut: TextView = itemView.findViewById(R.id.textViewUsernameOut)
        private val textViewMsgOut: TextView = itemView.findViewById(R.id.textViewMessageOut)
        private val textViewTimestamp: TextView = itemView.findViewById(R.id.textViewTimestamp)
        fun bind(message: Message) {
            textViewUsernameOut.text = message.username
            textViewMsgOut.text = message.text
            textViewTimestamp.text = message.timestamp
        }
    }

    inner class AudioInViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textViewUsernameIn: TextView = itemView.findViewById(R.id.textViewUsernameIn)
        private val buttonPlayIn: Button = itemView.findViewById(R.id.buttonPlayIn)
        private val textViewTimestamp: TextView = itemView.findViewById(R.id.textViewTimestamp)
        fun bind(message: Message) {
            textViewUsernameIn.text = message.username
            buttonPlayIn.setOnClickListener {
                message.audioData?.let { playAudio(it, itemView.context) }
            }
            textViewTimestamp.text = message.timestamp
        }
    }

    inner class AudioOutViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textViewUsernameOut: TextView = itemView.findViewById(R.id.textViewUsernameOut)
        private val buttonPlayOut: Button = itemView.findViewById(R.id.buttonPlayOut)
        private val textViewTimestamp: TextView = itemView.findViewById(R.id.textViewTimestamp)
        fun bind(message: Message) {
            textViewUsernameOut.text = message.username
            buttonPlayOut.setOnClickListener {
                message.audioData?.let { playAudio(it, itemView.context) }
            }
            textViewTimestamp.text = message.timestamp
        }
    }

    private fun playAudio(data: ByteArray, context: Context) {
        try {
            val tempFile = File(context.cacheDir, "audioMsg_${System.currentTimeMillis()}.3gp")
            tempFile.deleteOnExit()

            FileOutputStream(tempFile).use { it.write(data) }

            MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                setOnPreparedListener {
                    start()
                    Log.d("AudioPlayback", "Playing audio: ${tempFile.absolutePath}")
                }
                setOnErrorListener { mp, what, extra ->
                    Log.e("AudioPlayback", "Error playing audio: $what, $extra")
                    false
                }
                prepareAsync()  // use async to avoid blocking the UI thread
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("AudioPlayback", "Error preparing media player: ${e.message}")
        }
    }

}