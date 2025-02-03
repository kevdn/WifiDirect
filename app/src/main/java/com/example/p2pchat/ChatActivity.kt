package com.example.p2pchat

import android.Manifest
import android.annotation.SuppressLint
import android.util.Log
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.p2pchat.ui.ChatAdapter
import com.example.p2pchat.ui.Message
import com.example.p2pchat.ui.PeerListDialog
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import android.media.MediaRecorder
import android.os.Build
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale

class ChatActivity : AppCompatActivity() {

    lateinit var username: String

    lateinit var connectionStatus: TextView
    lateinit var typeMsg: EditText
    lateinit var sendButton: ImageButton
    lateinit var aSwitch: Button
    lateinit var discoverButton: Button

    lateinit var manager: WifiP2pManager
    lateinit var channel: WifiP2pManager.Channel
    lateinit var receiver: BroadcastReceiver
    lateinit var intentFilter: IntentFilter

    var peers: MutableList<WifiP2pDevice> = mutableListOf()
    var deviceNameArray: Array<String> = arrayOf()
    var deviceArray: Array<WifiP2pDevice> = arrayOf()

    private lateinit var chatAdapter: ChatAdapter
    private lateinit var recyclerChatMessages: RecyclerView
    private val messages = mutableListOf<Message>()

    private var mediaRecorder: MediaRecorder? = null
    private var audioFilePath: String = ""
    private var isRecording = false

    object SocketManager {
        var socket: Socket? = null
    }

    var serverClass: ServerClass? = null
    var clientClass: ClientClass? = null
    var isHost: Boolean = false

    private val PERMISSION_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        username = intent.getStringExtra("username") ?: "Unknown"

        initalWork()
        exqListener()
        setupWifiDirect()
        checkAudioPermissions()

        loadMessagesFromFile()
        chatAdapter.notifyDataSetChanged()
    }

    fun getCurrentTime(): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault()) // hour:minute:second
        return sdf.format(Date()) // return current time
    }

    /**
     * Initialize layout references and set up RecyclerView.
     */
    private fun initalWork() {
        connectionStatus = findViewById(R.id.connection_status)
        typeMsg = findViewById(R.id.editTextTypeMsg)
        sendButton = findViewById(R.id.sendButton)
        aSwitch = findViewById(R.id.switch1)
        discoverButton = findViewById(R.id.buttonDiscover)

        recyclerChatMessages = findViewById(R.id.recyclerChatMessages)
        recyclerChatMessages.layoutManager = LinearLayoutManager(this)
        chatAdapter = ChatAdapter(messages)
        recyclerChatMessages.adapter = chatAdapter
        var recordAudioButton = findViewById<Button>(R.id.recordAudioButton)

        recordAudioButton.setOnClickListener {
            if (!isRecording) {
                startRecording()
                recordAudioButton.text = "Stop Recording"
                isRecording = true
            } else {
                stopRecordingAndSend()
                recordAudioButton.text = "Start Recording"
                isRecording = false
            }
        }
    }

    /**
     * Set listeners for buttons and other UI elements.
     */
    private fun exqListener() {
        aSwitch.setOnClickListener {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
            startActivity(intent)
        }

        discoverButton.setOnClickListener {
            if (!checkRequiredPermissions()) {
                requestPermissions()
                return@setOnClickListener
            }
            discoverPeers()
        }

        sendButton.setOnClickListener {
            val executor = Executors.newSingleThreadExecutor()
            val msg = typeMsg.text.toString().trim()
            if (msg.isNotEmpty()) {
                messages.add(Message(msg, isIncoming = false, username = username, timestamp = getCurrentTime()))
                chatAdapter.notifyItemInserted(messages.size - 1)
                recyclerChatMessages.scrollToPosition(messages.size - 1)
                typeMsg.setText("")

                executor.execute {
                    if (isHost) {
                        serverClass?.write(msg.toByteArray(), isText = true)
                    } else {
                        clientClass?.write(msg.toByteArray(), isText = true)
                    }
                }
                saveMessagesToFile()
                Log.d("MessageSending", "Sent message: $msg")
            }
        }

    }

    /**
     * Configure Wi-Fi P2P manager and create an intent filter for broadcast events.
     */
    private fun setupWifiDirect() {
        manager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel = manager.initialize(this, mainLooper, null)
        receiver = WifiDirectBroadcastReceiver(manager, channel, this)

        intentFilter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        }
    }

    /**
     * Save messages to a JSON file.
     */
    private fun saveMessagesToFile() {
        try {
            val gson = Gson()
            val jsonString = gson.toJson(messages)

            val file = File(filesDir, "chat_history.json")
            file.writeText(jsonString)

            Log.d("Storage", "Messages saved successfully!")
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("Storage", "Error saving messages: ${e.message}")
        }
    }

    // Đọc tin nhắn từ tệp JSON
    private fun loadMessagesFromFile() {
        try {
            val file = File(filesDir, "chat_history.json")
            if (file.exists()) {
                val jsonString = file.readText()
                val gson = Gson()
                val type = object : TypeToken<List<Message>>() {}.type
                val savedMessages: List<Message> = gson.fromJson(jsonString, type)

                messages.clear()
                messages.addAll(savedMessages)

                Log.d("Storage", "Messages loaded successfully!")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("Storage", "Error loading messages: ${e.message}")
        }
    }

    /**
     * Check if we have the required permissions for location and nearby Wi-Fi.
     */
    private fun checkRequiredPermissions(): Boolean {
        val locPermission =
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val wifiPermission =
            ActivityCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES)
        return (locPermission == PackageManager.PERMISSION_GRANTED && wifiPermission == PackageManager.PERMISSION_GRANTED)
    }

    /**
     * Request the missing permissions from the user.
     */
    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ),
            PERMISSION_REQUEST_CODE
        )
    }

    /**
     * Check if we have the required audio permissions.
     */
    private fun checkAudioPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        if (permissions.any {
                ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }) {
            ActivityCompat.requestPermissions(this, permissions, 2002)
        }
    }


    /**
     * Start discovering peers using the Wi-Fi P2P manager.
     */
    private fun discoverPeers() {
        // Check for ACCESS_FINE_LOCATION permission
        val fineLocationPermission = ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        // Check for NEARBY_WIFI_DEVICES permission (required for Android 13+)
        val nearbyWifiPermission =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.NEARBY_WIFI_DEVICES
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true // NEARBY_WIFI_DEVICES is not required for below Android 13
            }

        if (!fineLocationPermission || !nearbyWifiPermission) {
            Toast.makeText(this, "Required permissions are not granted.", Toast.LENGTH_SHORT).show()
            // Optionally, you can request permissions here or redirect the user to settings
            requestPermissions()
            return
        }

        try {
            manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    connectionStatus.text = "Discovery Started"
                }

                override fun onFailure(reason: Int) {
                    connectionStatus.text = "Discovery not Started"
                    Toast.makeText(
                        this@ChatActivity,
                        "Failed to start discovery.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
        } catch (e: SecurityException) {
            e.printStackTrace()
            Toast.makeText(this, "SecurityException: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Listener to handle peer list changes.
     * You can call this from your BroadcastReceiver when WIFI_P2P_PEERS_CHANGED_ACTION occurs.
     */
    @SuppressLint("MissingPermission")
    val peerListListener = WifiP2pManager.PeerListListener { wifiP2pDeviceList ->
        Log.d(
            "MainActivity",
            "PeerListListener invoked. Peers count: ${wifiP2pDeviceList.deviceList.size}"
        )

        if (wifiP2pDeviceList.deviceList.isEmpty()) {
            connectionStatus.text = "No Devices Found"
            // Optionally, show the dialog even if no peers are found
            PeerListDialog(emptyList()) { selectedPeer ->
                // Handle selection (if any)
            }.show(supportFragmentManager, "peerListDialog")
            return@PeerListListener
        }

        peers.clear()
        peers.addAll(wifiP2pDeviceList.deviceList)

        deviceNameArray = Array(peers.size) { "" }
        deviceArray = Array(peers.size) { WifiP2pDevice() }

        for (index in peers.indices) {
            deviceNameArray[index] = peers[index].deviceName
            deviceArray[index] = peers[index]
        }

        Log.d("MainActivity", "Peers found: ${deviceNameArray.joinToString(", ")}")

        // Show discovered peers in a popup instead of a ListView
        PeerListDialog(deviceNameArray.toList()) { selectedPeer ->
            val selectedIndex = deviceNameArray.indexOf(selectedPeer)
            if (selectedIndex != -1) {
                val device = deviceArray[selectedIndex]
                val config = WifiP2pConfig().apply {
                    deviceAddress = device.deviceAddress
                }
                manager.connect(channel, config, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        connectionStatus.text = "Connecting to ${device.deviceName}"
                        Log.d("MainActivity", "Connecting to ${device.deviceName}")
                    }

                    override fun onFailure(reason: Int) {
                        connectionStatus.text = "Connection Failed"
                        Toast.makeText(this@ChatActivity, "Connection Failed", Toast.LENGTH_SHORT)
                            .show()
                        Log.d("MainActivity", "Connection Failed with reason: $reason")
                    }
                })
            }
        }.show(supportFragmentManager, "peerListDialog")
    }

    /**
     * Listener to handle connection info once a connection is formed.
     */
    val connectionInfoListener = WifiP2pManager.ConnectionInfoListener { wifiP2pInfo: WifiP2pInfo ->
        val groupOwnerAddress: InetAddress? = wifiP2pInfo.groupOwnerAddress
        if (wifiP2pInfo.groupFormed && wifiP2pInfo.isGroupOwner) {
            connectionStatus.text = "Host"
            isHost = true
            serverClass = ServerClass(this)
            serverClass?.start()
        } else if (wifiP2pInfo.groupFormed) {
            connectionStatus.text = "Client"
            isHost = false
            clientClass = ClientClass(groupOwnerAddress!!, this)
            clientClass?.start()
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(receiver, intentFilter)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(receiver)
    }

    /**
     * Update UI by adding an incoming message to the RecyclerView.
     */
    fun handleIncomingMessage(receivedMessage: Message) {
        runOnUiThread {
            val incomingMessage = Message(
                text = receivedMessage.text, // message text
                audioData = null, // not audio data
                audioDataTemp = null,
                isIncoming = true, // incoming message
                username = receivedMessage.username, // save sender's name
                timestamp = getCurrentTime() // save current time
            )
            messages.add(incomingMessage)
            chatAdapter.notifyItemInserted(messages.size - 1)
            recyclerChatMessages.scrollToPosition(messages.size - 1)
            saveMessagesToFile()
        }
        Log.d("IncomingMessage", "Received message: ${receivedMessage.text}")
    }


    /**
     * Start recording audio.
     */
    private fun startRecording() {
        try {
            val outputFile = File.createTempFile("temp_audio", ".3gp", cacheDir)
            audioFilePath = outputFile.absolutePath
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)  // use microphone
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4) // use MPEG-4 format
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)  // use AAC encoding for better quality
                setAudioEncodingBitRate(4000)  // Increase bit rate for better quality
                setAudioSamplingRate(22050)  // Increase sampling rate for better quality
                setOutputFile(audioFilePath)
                prepare()
                start()
            }
            Log.d("AudioRecording", "Recording started")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopRecordingAndSend() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            Log.d("AudioRecording", "Recording stopped")

            val audioFile = File(audioFilePath)
            if (audioFile.exists()) {
                val data = audioFile.readBytes()
                Log.d("AudioRecording", "Audio file size: ${data.size} bytes")

                messages.add(Message(audioData = data, isIncoming = false, username = username, timestamp = getCurrentTime()))
                runOnUiThread {
                    chatAdapter.notifyItemInserted(messages.size - 1)
                    recyclerChatMessages.scrollToPosition(messages.size - 1)
                }

                Executors.newSingleThreadExecutor().execute {
                    if (isHost) {
                        serverClass?.write(data, isText = false)
                    } else {
                        clientClass?.write(data, isText = false)
                    }
                }

                saveMessagesToFile()
                Log.d("AudioRecording", "Audio sent successfully")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("AudioRecording", "Error sending audio: ${e.message}")
        }
    }



    /**
     * Handle incoming audio data and add it to the RecyclerView.
     */
    fun handleIncomingAudio(receivedMessage: Message) {
        val decodedAudioData = Base64.getDecoder().decode(receivedMessage.audioDataTemp)
        val incomingAudioMessage = Message(
            text = null, // not text message
            audioData = decodedAudioData, // audio data
            audioDataTemp = null,
            isIncoming = true, // marked as incoming message
            username = receivedMessage.username, // save sender's name
            timestamp = getCurrentTime() // save current time
        )
        messages.add(incomingAudioMessage)
        runOnUiThread {
            chatAdapter.notifyItemInserted(messages.size - 1)
            recyclerChatMessages.scrollToPosition(messages.size - 1)
        }
        saveMessagesToFile()
        Log.d("IncomingAudio", "Received audio message from ${receivedMessage.username}")
    }



    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun playReceivedAudio(filePath: String) {
        try {
            val mediaPlayer = MediaPlayer().apply {
                setDataSource(filePath)
                setAudioStreamType(android.media.AudioManager.STREAM_MUSIC)
                prepare()
                start()
                setVolume(1.0f, 1.0f)  // set volume to max
            }
            Log.d("AudioPlayback", "Playing received audio from: $filePath")
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("AudioPlayback", "Error playing audio: ${e.message}")
        }
    }

    /**
     * Server socket thread class.
     */
    class ServerClass(private val activity: ChatActivity) : Thread() {
        private var serverSocket: ServerSocket? = null
        private var inputStream: InputStream? = null
        private var outputStream: OutputStream? = null

        fun write(bytes: ByteArray, isText: Boolean) {
            try {
                val gson = Gson()
                val messageData = if (isText) {
                    Message(
                        text = String(bytes, Charsets.UTF_8),
                        isIncoming = false,
                        username = activity.username,
                        timestamp = activity.getCurrentTime()
                    )
                } else {
                    Message(
                        audioDataTemp = Base64.getEncoder().encodeToString(bytes),
                        isIncoming = false,
                        username = activity.username,
                        timestamp = activity.getCurrentTime()
                    )
                }
                val jsonMessage = gson.toJson(messageData)
                val dataWithPrefix = if (isText) {
                    "TXT:".toByteArray() + jsonMessage.toByteArray()
                } else {
                    "AUD:".toByteArray() + jsonMessage.toByteArray()
                }

                outputStream?.write(dataWithPrefix)
                outputStream?.flush()
                Log.d("Write", "Sent message: $jsonMessage")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }


        override fun run() {
            try {
                serverSocket = ServerSocket(8888)
                SocketManager.socket = serverSocket?.accept()
                inputStream = SocketManager.socket?.getInputStream()
                outputStream = SocketManager.socket?.getOutputStream()

                val buffer = ByteArray(32768)
                val handler = Handler(Looper.getMainLooper())

                while (SocketManager.socket != null && !SocketManager.socket!!.isClosed) {
                    val bytes = inputStream?.read(buffer) ?: -1
                    if (bytes > 0) {
                        val data = buffer.copyOf(bytes)
                        handler.post {
                            val prefix = data.copyOfRange(0, 4).toString(Charsets.UTF_8)
                            val jsonData = data.copyOfRange(4, data.size).toString(Charsets.UTF_8)
                            val gson = Gson()

                            if (prefix == "TXT:") {
                                val receivedMessage = gson.fromJson(jsonData, Message::class.java)
                                activity.handleIncomingMessage(receivedMessage)
                            } else if (prefix == "AUD:") {
                                val receivedMessage = gson.fromJson(jsonData, Message::class.java)
                                activity.handleIncomingAudio(receivedMessage)
                            }

                        }

                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                inputStream?.close()
                outputStream?.close()
                serverSocket?.close()
                SocketManager.socket?.close()
            }
        }
    }

    class ClientClass(private val hostAddress: InetAddress, private val activity: ChatActivity) :
        Thread() {
        private var inputStream: InputStream? = null
        private var outputStream: OutputStream? = null

        fun write(bytes: ByteArray, isText: Boolean) {
            try {
                val gson = Gson()
                val messageData = if (isText) {
                    Message(
                        text = String(bytes, Charsets.UTF_8),
                        isIncoming = false,
                        username = activity.username,
                        timestamp = activity.getCurrentTime()
                    )
                } else {
                    Message(
                        audioDataTemp = Base64.getEncoder().encodeToString(bytes),
                        isIncoming = false,
                        username = activity.username,
                        timestamp = activity.getCurrentTime()
                    )
                }
                val jsonMessage = gson.toJson(messageData)
                val dataWithPrefix = if (isText) {
                    "TXT:".toByteArray() + jsonMessage.toByteArray()
                } else {
                    "AUD:".toByteArray() + jsonMessage.toByteArray()
                }

                outputStream?.write(dataWithPrefix)
                outputStream?.flush()
                Log.d("Write", "Sent message: $jsonMessage")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }


        override fun run() {
            try {
                SocketManager.socket = Socket()
                SocketManager.socket?.connect(InetSocketAddress(hostAddress, 8888), 20000)
                inputStream = SocketManager.socket?.getInputStream()
                outputStream = SocketManager.socket?.getOutputStream()

                val buffer = ByteArray(32768)
                val handler = Handler(Looper.getMainLooper())

                while (SocketManager.socket != null && !SocketManager.socket!!.isClosed) {
                    val bytes = inputStream?.read(buffer) ?: -1
                    if (bytes > 0) {
                        val data = buffer.copyOf(bytes)
                        handler.post {
                            val prefix = data.copyOfRange(0, 4).toString(Charsets.UTF_8)
                            val jsonData = data.copyOfRange(4, data.size).toString(Charsets.UTF_8)
                            val gson = Gson()

                            if (prefix == "TXT:") {
                                val receivedMessage = gson.fromJson(jsonData, Message::class.java)
                                activity.handleIncomingMessage(receivedMessage)
                            } else if (prefix == "AUD:") {
                                val receivedMessage = gson.fromJson(jsonData, Message::class.java)
                                activity.handleIncomingAudio(receivedMessage)
                            }

                        }

                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                inputStream?.close()
                outputStream?.close()
                SocketManager.socket?.close()
            }
        }
    }
}